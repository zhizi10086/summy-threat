package com.summy.threat.ability;

import com.summy.threat.Config;
import com.summy.threat.scaling.MonsterScaler;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
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
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
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

	/** 按 TL 区间抽取：等概率、同一只怪不重复；extra = 卓越者的 +1。 */
	public static String roll(int tl, int extra, RandomSource random) {
		List<AbilityId> pool = new ArrayList<>();
		for (AbilityId id : AbilityId.values()) {
			if (id.unlockTl() <= tl) {
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

	@SubscribeEvent
	public static void onLivingDamage(LivingDamageEvent event) {
		if (!enabled()) {
			return;
		}
		LivingEntity victim = event.getEntity();
		Entity sourceEntity = event.getSource().getEntity();
		float amount = event.getAmount();

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
		if (extraDepth == 0 && amount > 0.0F && sourceEntity instanceof LivingEntity attacker
				&& attacker != victim && event.getSource().getDirectEntity() == attacker) {
			if (has(attacker, AbilityId.LIFESTEAL)) {
				attacker.heal(amount * 0.15F);
			}
			if (has(attacker, AbilityId.EMBER)) {
				damageExtra(victim, attacker.damageSources().onFire(), amount * 0.10F);
			}
			if (has(attacker, AbilityId.CORROSION)) {
				applyCorrosion(victim);
			}
		}

		// —— 荆棘：反弹本次承伤 10%（追加段不连锁）——
		if (extraDepth == 0 && amount > 0.0F && sourceEntity instanceof LivingEntity attacker
				&& attacker != victim && has(victim, AbilityId.THORNS)) {
			damageExtra(attacker, victim.damageSources().thorns(victim), amount * 0.10F);
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
			float base = (float) (victim.getMaxHealth() * 0.10D);
			AABB box = victim.getBoundingBox().inflate(3.0D);
			for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, box,
					e -> e != victim && e.isAlive())) {
				float amount = base;
				if (target instanceof Player player) {
					amount = Math.min(amount, player.getMaxHealth());
				}
				damageExtra(target, victim.damageSources().mobAttack(victim), amount);
			}
		}
		cleanup(victim);
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
	private static void damageExtra(LivingEntity target, DamageSource source, float amount) {
		if (amount <= 0.0F || !target.isAlive()) {
			return;
		}
		extraDepth++;
		try {
			target.invulnerableTime = 0;
			target.hurt(source, amount);
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
	}
}
