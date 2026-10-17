package com.summy.threat.eximus;

import com.summy.threat.Config;
import com.summy.threat.SummyThreat;
import com.summy.threat.ability.AbilityEffects;
import com.summy.threat.armor.EchoArmorEffects;
import com.summy.threat.scaling.MonsterScaler;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 卓越者核心机制：卓越护盾（Eximus Shield）。
 *
 * <p>数值按配置表图片的等级曲线：{@code d = TL}、{@code f1(d)=1+0.0015d⁴}，
 * 45–50 用 {@code g} 插值、>50 用 {@code f2(d)=1+260d^0.9}；当前 TL 上限 20，
 * 以 {@code f1(20)=241} 归一化、TL20 锚点由配置决定（默认 75%）。
 *
 * <p>承伤在 {@link LivingDamageEvent}（HIGHEST）里、护甲/附魔/吸收之后扣，
 * 不可再生；破盾/死亡/重缩放时清除属性快照地板，负向 modifier 重新生效。
 */
public final class EximusShield {
	public static final String TAG_SHIELD = "summy_threat:eximus_shield";
	public static final String TAG_SHIELD_MAX = "summy_threat:eximus_shield_max";
	/** 旧版标签：只读兼容并一次性迁移到新标签。 */
	public static final String TAG_LEGACY_SHIELD = "summy_threat:overguard";
	public static final String TAG_LEGACY_SHIELD_MAX = "summy_threat:overguard_max";

	/** 玩家 → 卓越者的护盾吸收量（由 CombatMeter 一次性消费）。 */
	private static final Map<UUID, Float> ABSORBED = new HashMap<>();
	/** 破盾后的真无敌截止 gameTime：0.5 秒 = 10 tick。 */
	private static final Map<UUID, Long> GUARD_UNTIL = new HashMap<>();
	private static final int BREAK_GUARD_TICKS = 10;
	private static final UUID ID_KNOCKBACK_RESISTANCE =
			UUID.fromString("6f5c1a2e-0009-4a0b-9d1e-000000000201");
	private static final String SHIELD_MODIFIER_NAME = "summy_threat:eximus_shield";

	private EximusShield() {
	}

	public static boolean isEximus(LivingEntity entity) {
		return entity.getPersistentData().getBoolean(MonsterScaler.TAG_EXIMUS)
				&& MonsterScaler.kindOf(entity) == MonsterScaler.Kind.EXIMUS;
	}

	public static boolean isActive(LivingEntity entity) {
		return isEximus(entity) && current(entity) > 0.0D;
	}

	public static double current(LivingEntity entity) {
		CompoundTag data = entity.getPersistentData();
		double value = data.contains(TAG_SHIELD) ? data.getDouble(TAG_SHIELD) : data.getDouble(TAG_LEGACY_SHIELD);
		return Math.max(0.0D, value);
	}

	public static double max(LivingEntity entity) {
		CompoundTag data = entity.getPersistentData();
		double value = data.contains(TAG_SHIELD_MAX)
				? data.getDouble(TAG_SHIELD_MAX) : data.getDouble(TAG_LEGACY_SHIELD_MAX);
		return Math.max(0.0D, value);
	}

	/** 当前 TL 的护盾占最大生命比例。 */
	public static double ratio(int tl) {
		int d = Math.max(0, Math.min(20, tl));
		double value = f(d);
		double anchor = f(20);
		double ratio = Config.EXIMUS_SHIELD_TL20_RATIO.get() * value / anchor;
		return Math.max(0.0D, Math.min(10.0D, ratio));
	}

	/**
	 * 图中公式的完整实现：d ≤ 45 用 f1；45–50 用 g 三次平滑插值；>50 用 f2。
	 * 当前 TL 上限 20，所以实际只走 f1，后两段为未来扩展保留。
	 */
	private static double f(int d) {
		if (d <= 45) {
			return 1.0D + 0.0015D * Math.pow(d, 4.0D);
		}
		if (d <= 50) {
			double t = (d - 45.0D) / 5.0D;
			double g = 3.0D * t * t - 2.0D * t * t * t;
			double f1 = 1.0D + 0.0015D * Math.pow(45.0D, 4.0D);
			double f2 = 1.0D + 260.0D * Math.pow(d, 0.9D);
			return f1 * (1.0D - g) + f2 * g;
		}
		return 1.0D + 260.0D * Math.pow(d, 0.9D);
	}

	/**
	 * 新刷 / 重缩放时调用：新卓越者满盾；残留卓越者按剩余比例保留（不可再生）。
	 * 旧 {@code summy_threat:overguard} 标签会在这里迁移到新标签。
	 */
	public static void refresh(LivingEntity entity, int tl, boolean eximus) {
		CompoundTag data = entity.getPersistentData();
		GUARD_UNTIL.remove(entity.getUUID());
		if (!eximus) {
			clear(entity);
			return;
		}
		// 先清掉旧快照/负面效果，再读取干净的最大生命与属性作为新基线
		clearSnapshot(entity);
		clearNegativeEffects(entity);
		AbilityEffects.clearCorrosion(entity);
		double newMax = Math.max(0.0D, entity.getMaxHealth() * ratio(tl));
		double current;
		if (data.contains(TAG_SHIELD) && data.contains(TAG_SHIELD_MAX)) {
			double oldCurrent = Math.max(0.0D, data.getDouble(TAG_SHIELD));
			double oldMax = Math.max(0.0D, data.getDouble(TAG_SHIELD_MAX));
			current = oldMax > 0.0D ? Math.min(newMax, oldCurrent / oldMax * newMax) : newMax;
		} else if (data.contains(TAG_LEGACY_SHIELD) && data.contains(TAG_LEGACY_SHIELD_MAX)) {
			double oldCurrent = Math.max(0.0D, data.getDouble(TAG_LEGACY_SHIELD));
			double oldMax = Math.max(0.0D, data.getDouble(TAG_LEGACY_SHIELD_MAX));
			current = oldMax > 0.0D ? Math.min(newMax, oldCurrent / oldMax * newMax) : newMax;
		} else {
			current = newMax;
		}
		data.putDouble(TAG_SHIELD_MAX, newMax);
		data.putDouble(TAG_SHIELD, current);
		data.remove(TAG_LEGACY_SHIELD);
		data.remove(TAG_LEGACY_SHIELD_MAX);
		if (current > 0.0D) {
			applyShieldModifiers(entity);
			applySnapshot(entity);
		} else {
			removeShieldModifiers(entity);
			clearSnapshot(entity);
		}
	}

	public static void clear(LivingEntity entity) {
		CompoundTag data = entity.getPersistentData();
		data.remove(TAG_SHIELD);
		data.remove(TAG_SHIELD_MAX);
		data.remove(TAG_LEGACY_SHIELD);
		data.remove(TAG_LEGACY_SHIELD_MAX);
		removeShieldModifiers(entity);
		clearSnapshot(entity);
		ABSORBED.remove(entity.getUUID());
		GUARD_UNTIL.remove(entity.getUUID());
	}

	/** 护盾破碎后的 0.5 秒真无敌；不依赖护盾剩余值，护盾已归零仍会生效。 */
	public static boolean isGuarded(LivingEntity entity) {
		if (entity == null) {
			return false;
		}
		Long until = GUARD_UNTIL.get(entity.getUUID());
		if (until == null) {
			return false;
		}
		long now = entity.level().getGameTime();
		if (now >= until) {
			GUARD_UNTIL.remove(entity.getUUID());
			return false;
		}
		return true;
	}

	/**
	 * 破盾保护：在所有伤害进入护甲/附魔/吸收之前整击取消。
	 * 与 SR「魂心破碎 / 免死」同一写法；只取消走 LivingHurtEvent 的伤害。
	 */
	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public static void onLivingHurt(LivingHurtEvent event) {
		if (event.getEntity().level().isClientSide()) {
			return;
		}
		if (isGuarded(event.getEntity())) {
			event.setAmount(0.0F);
			event.setCanceled(true);
			// 清掉原版受击帧，保证 0.5 秒窗口结束后能立刻重新受伤，不被这次取消的命中额外延长。
			event.getEntity().invulnerableTime = 0;
		}
	}

	private static void clearNegativeEffects(LivingEntity entity) {
		for (MobEffectInstance effect : new ArrayList<>(entity.getActiveEffects())) {
			if (!effect.getEffect().isBeneficial()) {
				entity.removeEffect(effect.getEffect());
			}
		}
	}

	/** 护盾存在期间：100% 击退抗性（加法 +1.0，原版上限 1.0）。 */
	private static void applyShieldModifiers(LivingEntity entity) {
		AttributeInstance resistance = entity.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
		if (resistance == null) {
			return;
		}
		resistance.removeModifier(ID_KNOCKBACK_RESISTANCE);
		resistance.addTransientModifier(new AttributeModifier(ID_KNOCKBACK_RESISTANCE,
				SHIELD_MODIFIER_NAME + "/knockback_resistance", 1.0D,
				AttributeModifier.Operation.ADDITION));
	}

	private static void removeShieldModifiers(LivingEntity entity) {
		AttributeInstance resistance = entity.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
		if (resistance != null) {
			resistance.removeModifier(ID_KNOCKBACK_RESISTANCE);
		}
	}

	/** 记录全部可同步属性的当前值作为固定地板。 */
	private static void applySnapshot(LivingEntity entity) {
		clearSnapshot(entity);
		for (AttributeInstance instance : entity.getAttributes().getSyncableAttributes()) {
			if (instance instanceof EximusShieldFloor floor) {
				floor.summyThreat$setFloor(instance.getValue());
			}
		}
	}

	private static void clearSnapshot(LivingEntity entity) {
		for (AttributeInstance instance : entity.getAttributes().getSyncableAttributes()) {
			if (instance instanceof EximusShieldFloor floor) {
				floor.summyThreat$clearFloor();
			}
		}
	}

	// 放在 LOWEST：先让坚韧/冰盾/守护等减伤与转移结算；同优先级的 CombatMeter 随后读取吸收量。
	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void onLivingDamage(LivingDamageEvent event) {
		LivingEntity victim = event.getEntity();
		if (!isActive(victim)) {
			return;
		}
		if (event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return;
		}
		double current = current(victim);
		float amount = event.getAmount();
		if (current <= 0.0D || amount <= 0.0F) {
			return;
		}
		double shieldDamage = amount;
		if (event.getSource().getEntity() instanceof Player player) {
			// 护腿只加速护盾消耗；溢出生命的伤害仍按原始 amount 计算。
			shieldDamage *= EchoArmorEffects.shieldDamageMultiplier(player);
		}
		double absorbed = Math.min(current, shieldDamage);
		double left = current - absorbed;
		double healthDamage = Math.max(0.0D, amount - Math.min(current, amount));
		victim.getPersistentData().putDouble(TAG_SHIELD, left);
		event.setAmount((float) healthDamage);
		if (event.getSource().getEntity() instanceof Player) {
			ABSORBED.merge(victim.getUUID(), (float) absorbed, Float::sum);
		}
		if (left <= 0.0D) {
			// 破盾当击的溢出伤害清零，并开启 0.5 秒真无敌；后续命中在 onLivingHurt 整击取消。
			GUARD_UNTIL.put(victim.getUUID(), victim.level().getGameTime() + BREAK_GUARD_TICKS);
			event.setAmount(0.0F);
			victim.invulnerableTime = 0;
			removeShieldModifiers(victim);
			breakFx(victim);
			clearSnapshot(victim);
			SummyThreat.LOGGER.info("[Summy Threat] 卓越护盾破碎：{}（0.5 秒真无敌）", victim.getName().getString());
		}
	}

	@SubscribeEvent
	public static void onEffectApplicable(MobEffectEvent.Applicable event) {
		if (isActive(event.getEntity()) && !event.getEffectInstance().getEffect().isBeneficial()) {
			event.setResult(Event.Result.DENY);
		}
	}

	@SubscribeEvent
	public static void onLivingTick(LivingEvent.LivingTickEvent event) {
		LivingEntity entity = event.getEntity();
		if (entity.level().isClientSide() || !isActive(entity)) {
			return;
		}
		if (entity.tickCount % 10 == 0) {
			aura(entity);
		}
	}

	@SubscribeEvent
	public static void onLivingDeath(LivingDeathEvent event) {
		LivingEntity entity = event.getEntity();
		removeShieldModifiers(entity);
		clearSnapshot(entity);
		ABSORBED.remove(entity.getUUID());
		GUARD_UNTIL.remove(entity.getUUID());
	}

	@SubscribeEvent
	public static void onServerAboutToStart(ServerAboutToStartEvent event) {
		ABSORBED.clear();
		GUARD_UNTIL.clear();
	}

	/** CombatMeter 读取本次命中被护盾吸收的量（一次性）。 */
	public static float consumeAbsorbed(LivingEntity entity) {
		Float value = ABSORBED.remove(entity.getUUID());
		return value == null ? 0.0F : value;
	}

	private static void aura(LivingEntity entity) {
		if (!(entity.level() instanceof ServerLevel level)) {
			return;
		}
		double x = entity.getX();
		double y = entity.getY() + entity.getBbHeight() * 0.5D;
		double z = entity.getZ();
		level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, x, y, z, 2, 0.6D, 0.6D, 0.6D, 0.01D);
		level.sendParticles(ParticleTypes.END_ROD, x, y, z, 1, 0.4D, 0.5D, 0.4D, 0.01D);
	}

	private static void breakFx(LivingEntity entity) {
		if (!(entity.level() instanceof ServerLevel level)) {
			return;
		}
		double x = entity.getX();
		double y = entity.getY() + entity.getBbHeight() * 0.5D;
		double z = entity.getZ();
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, x, y, z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
		level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, x, y, z, 12, 1.0D, 0.6D, 1.0D, 0.02D);
		level.playSound(null, x, y, z, SoundEvents.SOUL_ESCAPE, SoundSource.HOSTILE, 0.8F, 0.9F);
		level.playSound(null, x, y, z, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 0.6F, 0.8F);
	}
}
