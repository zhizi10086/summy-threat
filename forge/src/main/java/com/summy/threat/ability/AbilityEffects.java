package com.summy.threat.ability;

import com.summy.threat.Config;
import com.summy.threat.eximus.Overguard;
import com.summy.threat.scaling.MonsterScaler;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * TL 能力效果层（交接文档 §6.5）：能力以逗号分隔的 id 存在 {@code summy_threat:abilities} 上，
 * 只在实体同时带 {@code summy_threat:scaled} 且总开关开启时生效。
 *
 * <p>追加段（熔火 / 荆棘 / 尸爆）统一走 {@link #damageExtra}：结算前清目标 {@code invulnerableTime}
 * （进清前帧、保证落地），结算后不清（保留原版受击帧、防连击）；追加段带防递归标记，
 * 不再触发吸血 / 熔火 / 腐蚀 / 荆棘 / 亡灵增援的连锁。
 *
 * <p>比例型能力（吸血 / 熔火 / 荆棘 / 腐蚀）的基数取 {@link LivingHurtEvent} 的减伤前伤害：
 * 吸收盾把 {@link LivingDamageEvent} 的 amount 扣成 0 时仍然生效，也不会被 80%~90% 的减伤压没。
 */
public final class AbilityEffects {
	private static final UUID ID_SWIFT = UUID.fromString("6f5c1a2e-0009-4a0b-9d1e-000000000101");
	private static final UUID ID_STRENGTH = UUID.fromString("6f5c1a2e-0009-4a0b-9d1e-000000000102");
	private static final UUID ID_FRENZY = UUID.fromString("6f5c1a2e-0009-4a0b-9d1e-000000000103");
	private static final UUID ID_CORROSION = UUID.fromString("6f5c1a2e-0009-4a0b-9d1e-000000000104");

	private static final String MODIFIER_NAME = "summy_threat:tl_ability";

	/** 追加段递归深度：>0 时表示正在结算追加段 */
	private static int extraDepth;

	/** 腐蚀：目标 UUID → 3 层各自的到期 tick（0 = 未叠） */
	private static final Map<UUID, long[]> CORROSION = new HashMap<>();
	/** 相位冷却：实体 UUID → 可再次触发的 tick */
	private static final Map<UUID, Long> PHASE_COOLDOWN = new HashMap<>();
	/** 亡灵增援：召唤者 UUID → 召唤体 UUID */
	private static final Map<UUID, List<UUID>> SUMMONS = new HashMap<>();
	/** 本击减伤前伤害：受害者 UUID → (伤害, tick)，仅在 LivingHurt → LivingDamage 之间有效 */
	private static final Map<UUID, NominalHit> NOMINAL = new HashMap<>();
	/** 荆棘：攻击者 UUID → 下次可触发 tick（按攻击者全局 0.25 秒冷却） */
	private static final Map<UUID, Long> THORNS_COOLDOWN = new HashMap<>();

	private record NominalHit(float amount, long tick) {
	}

	private AbilityEffects() {
	}

	public static boolean enabled() {
		return Config.SPEC.isLoaded() && Config.TL_ABILITIES_ENABLED.get();
	}

	/** 该实体身上的能力（解析标签；未知 id 忽略）。 */
	public static List<AbilityId> abilities(LivingEntity entity) {
		String raw = entity.getPersistentData().getString(MonsterScaler.TAG_ABILITIES);
		if (raw.isEmpty()) {
			return List.of();
		}
		List<AbilityId> out = new ArrayList<>(3);
		for (String part : raw.split(",")) {
			AbilityId id = AbilityId.byId(part);
			if (id != null) {
				out.add(id);
			}
		}
		return out;
	}

	public static boolean has(LivingEntity entity, AbilityId id) {
		return enabled() && abilities(entity).contains(id);
	}

	/** 写入能力（逗号分隔；空串 = 无能力），并重刷静态修饰符（迅捷 / 力量 / 狂怒）。 */
	public static void setAbilities(LivingEntity entity, int tl, String csv) {
		entity.getPersistentData().putString(MonsterScaler.TAG_ABILITIES, csv);
		refreshStaticModifiers(entity, tl);
	}

	/**
	 * 按 TL 区间抽取：等概率、同一只怪不重复；extra = 卓越者的 +1。
	 * 亡灵增援只对亡灵生物进入候选池。
	 */
	public static String roll(int tl, int extra, RandomSource random, boolean undead) {
		List<AbilityId> pool = new ArrayList<>();
		for (AbilityId id : AbilityId.values()) {
			if (id.unlockTl() <= tl) {
				if (id == AbilityId.NECRO_REINFORCEMENT && !undead) {
					continue;
				}
				pool.add(id);
			}
		}
		int count = Math.min(pool.size(), AbilityId.baseCountFor(tl) + extra);
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < count; i++) {
			AbilityId picked = pool.remove(random.nextInt(pool.size()));
			if (sb.length() > 0) {
				sb.append(',');
			}
			sb.append(picked.id());
		}
		return sb.toString();
	}

	private static void refreshStaticModifiers(LivingEntity entity, int tl) {
		AttributeInstance speed = entity.getAttribute(Attributes.MOVEMENT_SPEED);
		AttributeInstance attack = entity.getAttribute(Attributes.ATTACK_DAMAGE);
		if (speed != null) {
			speed.removeModifier(ID_SWIFT);
		}
		if (attack != null) {
			attack.removeModifier(ID_STRENGTH);
			attack.removeModifier(ID_FRENZY);
		}
		if (!enabled()) {
			return;
		}
		List<AbilityId> ids = abilities(entity);
		if (speed != null && ids.contains(AbilityId.SWIFT)) {
			speed.addTransientModifier(new AttributeModifier(ID_SWIFT, MODIFIER_NAME + "/swift", 0.15D,
					AttributeModifier.Operation.MULTIPLY_TOTAL));
		}
		if (attack != null && ids.contains(AbilityId.STRENGTH)) {
			attack.addTransientModifier(new AttributeModifier(ID_STRENGTH, MODIFIER_NAME + "/strength",
					0.10D + 0.01D * tl, AttributeModifier.Operation.MULTIPLY_TOTAL));
		}
		updateFrenzy(entity);
	}

	/** 狂怒：生命 <40% 时攻击 +30%（乘算）；不满足则移除。 */
	private static void updateFrenzy(LivingEntity entity) {
		AttributeInstance attack = entity.getAttribute(Attributes.ATTACK_DAMAGE);
		if (attack == null) {
			return;
		}
		boolean active = enabled() && entity.isAlive()
				&& entity.getHealth() < entity.getMaxHealth() * 0.40F
				&& abilities(entity).contains(AbilityId.FRENZY);
		boolean present = attack.getModifier(ID_FRENZY) != null;
		if (active && !present) {
			attack.addTransientModifier(new AttributeModifier(ID_FRENZY, MODIFIER_NAME + "/frenzy", 0.30D,
					AttributeModifier.Operation.MULTIPLY_TOTAL));
		} else if (!active && present) {
			attack.removeModifier(ID_FRENZY);
		}
	}

	/**
	 * 记录本击的减伤前伤害（护甲 / 附魔 / 吸收之前）。
	 * LOWEST 优先级确保看到其它模组处理后的最终名义值。
	 */
	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void onLivingHurt(LivingHurtEvent event) {
		if (!enabled()) {
			return;
		}
		LivingEntity victim = event.getEntity();
		Entity attacker = event.getSource().getEntity();
		// 只缓存与数值化怪物相关的命中，避免无意义地记录全部环境伤害
		if (!isScaled(victim) && !(attacker instanceof LivingEntity living && isScaled(living))) {
			return;
		}
		NOMINAL.put(victim.getUUID(), new NominalHit(event.getAmount(), victim.level().getGameTime()));
	}

	private static boolean isScaled(LivingEntity entity) {
		return entity.getPersistentData().getBoolean(MonsterScaler.TAG_SCALED);
	}

	/** 取本击减伤前伤害（同 tick 内有效）；没有记录或已过期时回退到最终伤害。 */
	private static float nominalOf(LivingEntity victim, float fallback) {
		NominalHit hit = NOMINAL.remove(victim.getUUID());
		if (hit == null) {
			return fallback;
		}
		long now = victim.level().getGameTime();
		if (Math.abs(now - hit.tick()) > 1L) {
			return fallback;
		}
		return Math.max(hit.amount(), 0.0F);
	}

	/**
	 * 命中是否已连接到攻击者：近战直击，或攻击者所属的投射物（排除投掷药水）。
	 * 箭、火球、三叉戟、潜影贝子弹、羊驼唾液、凋灵头颅等都会命中此分支。
	 */
	private static boolean isAttackConnected(DamageSource source, LivingEntity attacker) {
		Entity direct = source.getDirectEntity();
		if (direct == attacker) {
			return true;
		}
		return direct instanceof Projectile projectile
				&& !(projectile instanceof ThrownPotion)
				&& projectile.getOwner() == attacker;
	}

	/** 每 200 tick 清一次无对应 LivingDamage 的残留记录（被取消的命中）。 */
	@SubscribeEvent
	public static void onServerTick(TickEvent.ServerTickEvent event) {
		if (event.phase != TickEvent.Phase.END || event.getServer().getTickCount() % 200 != 0) {
			return;
		}
		long now = event.getServer().overworld().getGameTime();
		NOMINAL.entrySet().removeIf(entry -> now - entry.getValue().tick() > 40L);
		THORNS_COOLDOWN.entrySet().removeIf(entry -> now - entry.getValue() > 200L);
	}

	@SubscribeEvent
	public static void onLivingDamage(LivingDamageEvent event) {
		if (!enabled()) {
			return;
		}
		LivingEntity victim = event.getEntity();
		Entity sourceEntity = event.getSource().getEntity();
		float amount = event.getAmount();
		// 先取出名义伤害：即使最终伤害被吸收盾扣成 0，比例型能力仍然生效
		float nominal = nominalOf(victim, amount);

		// —— 受害方减伤：抗火 → 坚韧 → 韧皮（追加段同样吃这几层）——
		if (has(victim, AbilityId.FIREPROOF) && event.getSource().is(DamageTypeTags.IS_FIRE)) {
			event.setAmount(0.0F);
			return;
		}
		if (amount > 0.0F && has(victim, AbilityId.TOUGHNESS)) {
			amount *= 0.90F;
		}
		if (amount > 0.0F && has(victim, AbilityId.THICK_HIDE)
				&& event.getSource().is(DamageTypeTags.IS_PROJECTILE)) {
			amount *= 0.70F;
		}
		if (amount != event.getAmount()) {
			event.setAmount(amount);
		}

		// —— 攻击方触发：吸血 / 熔火 / 腐蚀（追加段不连锁）——
		if (extraDepth == 0 && nominal > 0.0F && sourceEntity instanceof LivingEntity attacker
				&& attacker != victim && isAttackConnected(event.getSource(), attacker)) {
			if (has(attacker, AbilityId.LIFESTEAL)) {
				// 至少回复自身最大生命的 0.5%，避免高血量单位（尤其 Boss）几乎不可见
				attacker.heal(Math.max(nominal * 0.15F, attacker.getMaxHealth() * 0.005F));
			}
			if (has(attacker, AbilityId.EMBER)) {
				damageExtra(victim, attacker.damageSources().onFire(), nominal * 0.10F, false);
			}
			if (has(attacker, AbilityId.CORROSION) && !Overguard.isActive(victim)) {
				applyCorrosion(victim);
			}
		}

		// —— 荆棘：反弹本次承伤 10%（追加段不连锁）——
		if (extraDepth == 0 && nominal > 0.0F && sourceEntity instanceof LivingEntity attacker
				&& attacker != victim && has(victim, AbilityId.THORNS)) {
			// 按攻击者全局冷却 0.25 秒（5 tick），防止 SRA 高频攻击瞬间反死玩家
			long now = victim.level().getGameTime();
			if (now >= THORNS_COOLDOWN.getOrDefault(attacker.getUUID(), 0L)) {
				THORNS_COOLDOWN.put(attacker.getUUID(), now + 5L);
				// 荆棘：无击退（结算前后保存 / 还原目标速度）
				damageExtra(attacker, victim.damageSources().thorns(victim), nominal * 0.10F, true);
			}
		}

		// —— 亡灵增援：受击时按 TL 概率召唤普通同类（上限 2 只）——
		if (extraDepth == 0 && has(victim, AbilityId.NECRO_REINFORCEMENT)
				&& victim.getMobType() == MobType.UNDEAD
				&& victim.level() instanceof ServerLevel level) {
			int tl = victim.getPersistentData().getInt(MonsterScaler.TAG_TL);
			if (tl >= 16) {
				double chance = Math.min(0.60D, 0.05D + 0.02D * (tl - 16));
				if (level.random.nextDouble() < chance) {
					summonReinforcement(victim, level);
				}
			}
		}
	}

	@SubscribeEvent
	public static void onLivingDeath(LivingDeathEvent event) {
		LivingEntity victim = event.getEntity();
		if (!enabled()) {
			cleanup(victim);
			return;
		}
		if (has(victim, AbilityId.DEATH_BURST) && victim.level() instanceof ServerLevel level) {
			// 尸爆：自身最大生命 100% 的魔法伤害（indirect_magic 无视护甲），允许连锁
			float base = victim.getMaxHealth();
			deathBurstFx(level, victim);
			DamageSource burst = victim.damageSources().indirectMagic(victim, victim);
			AABB box = victim.getBoundingBox().inflate(3.0D);
			for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, box,
					e -> e != victim && e.isAlive())) {
				float amount = base;
				if (target instanceof Player player) {
					// 对玩家的原始伤害上限 = 玩家最大生命的 85%（附魔保护 / 抗性 / 吸收仍继续结算）
					amount = Math.min(amount, player.getMaxHealth() * 0.85F);
				}
				damageExtra(target, burst, amount, false);
			}
		}
		cleanup(victim);
	}

	/** 灵魂爆发：爆炸烟尘 + 灵魂火粒子环 + 低沉爆炸音。 */
	private static void deathBurstFx(ServerLevel level, LivingEntity victim) {
		double x = victim.getX();
		double y = victim.getY() + victim.getBbHeight() * 0.5D;
		double z = victim.getZ();
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, x, y, z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
		level.sendParticles(ParticleTypes.SOUL, x, y, z, 24, 1.6D, 0.9D, 1.6D, 0.02D);
		level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, x, y, z, 12, 1.2D, 0.6D, 1.2D, 0.01D);
		level.playSound(null, x, y, z, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.0F, 0.8F);
		level.playSound(null, x, y, z, SoundEvents.SOUL_ESCAPE, SoundSource.HOSTILE, 0.6F, 0.9F);
	}

	@SubscribeEvent
	public static void onLivingTick(LivingEvent.LivingTickEvent event) {
		LivingEntity entity = event.getEntity();
		if (entity.level().isClientSide) {
			return;
		}
		if (CORROSION.containsKey(entity.getUUID())) {
			updateCorrosion(entity);
		}
		if (!enabled() || entity.tickCount % 10 != 0) {
			return;
		}
		CompoundTag data = entity.getPersistentData();
		if (!data.getBoolean(MonsterScaler.TAG_SCALED) || data.getString(MonsterScaler.TAG_ABILITIES).isEmpty()) {
			return;
		}
		List<AbilityId> ids = abilities(entity);
		if (ids.contains(AbilityId.REGENERATION) && entity.tickCount % 20 == 0
				&& entity.getHealth() < entity.getMaxHealth()) {
			entity.heal(entity.getMaxHealth() * 0.01F);
		}
		if (ids.contains(AbilityId.FRENZY)) {
			updateFrenzy(entity);
		}
		if (ids.contains(AbilityId.PHASE)) {
			long now = entity.level().getGameTime();
			long ready = PHASE_COOLDOWN.getOrDefault(entity.getUUID(), 0L);
			if (entity.getHealth() < entity.getMaxHealth() * 0.25F && now >= ready) {
				entity.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 30, 0, false, false, true));
				PHASE_COOLDOWN.put(entity.getUUID(), now + 400L);
			}
		}
	}

	/** 追加段：进清前帧、结算后不清；期间跳过攻击方链式能力。 */
	private static void damageExtra(LivingEntity target, DamageSource source, float amount, boolean suppressKnockback) {
		if (amount <= 0.0F || !target.isAlive()) {
			return;
		}
		extraDepth++;
		try {
			Vec3 velocity = suppressKnockback ? target.getDeltaMovement() : null;
			target.invulnerableTime = 0;
			boolean landed = target.hurt(source, amount);
			if (suppressKnockback && landed) {
				target.setDeltaMovement(velocity);
				target.hurtMarked = true;
			}
		} finally {
			extraDepth--;
		}
	}

	private static void applyCorrosion(LivingEntity victim) {
		long now = victim.level().getGameTime();
		long[] stacks = CORROSION.computeIfAbsent(victim.getUUID(), id -> new long[3]);
		int slot = 0;
		for (int i = 0; i < stacks.length; i++) {
			if (stacks[i] <= now) {
				slot = i;
				break;
			}
			if (stacks[i] < stacks[slot]) {
				slot = i;
			}
		}
		stacks[slot] = now + 120L;
		refreshCorrosionModifier(victim, now);
	}

	/** 超宏防护获得时清除已有腐蚀，保证属性快照基线干净。 */
	public static void clearCorrosion(LivingEntity entity) {
		CORROSION.remove(entity.getUUID());
		AttributeInstance armor = entity.getAttribute(Attributes.ARMOR);
		if (armor != null) {
			armor.removeModifier(ID_CORROSION);
		}
	}

	private static void updateCorrosion(LivingEntity victim) {
		long[] stacks = CORROSION.get(victim.getUUID());
		if (stacks == null) {
			return;
		}
		refreshCorrosionModifier(victim, victim.level().getGameTime());
	}

	private static void refreshCorrosionModifier(LivingEntity victim, long now) {
		long[] stacks = CORROSION.get(victim.getUUID());
		if (stacks == null) {
			return;
		}
		int active = 0;
		for (long expiry : stacks) {
			if (expiry > now) {
				active++;
			}
		}
		AttributeInstance armor = victim.getAttribute(Attributes.ARMOR);
		if (armor != null) {
			armor.removeModifier(ID_CORROSION);
			if (active > 0) {
				armor.addTransientModifier(new AttributeModifier(ID_CORROSION, MODIFIER_NAME + "/corrosion",
						-0.10D * active, AttributeModifier.Operation.MULTIPLY_TOTAL));
			}
		}
		if (active == 0) {
			CORROSION.remove(victim.getUUID());
		}
	}

	private static void summonReinforcement(LivingEntity owner, ServerLevel level) {
		List<UUID> list = SUMMONS.computeIfAbsent(owner.getUUID(), id -> new ArrayList<>());
		list.removeIf(id -> !(level.getEntity(id) instanceof LivingEntity e) || !e.isAlive());
		if (list.size() >= 2) {
			return;
		}
		EntityType<?> type = owner.getType();
		Entity created = type.create(level);
		if (!(created instanceof Mob mob)) {
			return;
		}
		double dx = (level.random.nextDouble() * 2.0D - 1.0D) * 7.0D;
		double dz = (level.random.nextDouble() * 2.0D - 1.0D) * 7.0D;
		mob.moveTo(owner.getX() + dx, owner.getY(), owner.getZ() + dz, level.random.nextFloat() * 360.0F, 0.0F);
		mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()), MobSpawnType.MOB_SUMMONED,
				null, null);
		// 召唤体为普通同类：能力标签置空（join 缩放时"已有标签"→不再抽取）
		mob.getPersistentData().putString(MonsterScaler.TAG_ABILITIES, "");
		level.addFreshEntity(mob);
		list.add(mob.getUUID());
	}

	private static void cleanup(LivingEntity entity) {
		UUID id = entity.getUUID();
		CORROSION.remove(id);
		PHASE_COOLDOWN.remove(id);
		SUMMONS.remove(id);
		NOMINAL.remove(id);
		THORNS_COOLDOWN.remove(id);
	}
}
