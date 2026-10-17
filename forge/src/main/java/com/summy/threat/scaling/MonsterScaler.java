package com.summy.threat.scaling;

import com.summy.threat.Config;
import com.summy.threat.SummyThreat;
import com.summy.threat.ability.AbilityEffects;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.UUID;

/**
 * 怪物侧数值管线：按 TL 与档位给怪物写属性（护甲 / 韧性 / 生命 / 攻击 / 移速）。
 *
 * <p>口径见交接文档 §4 / §5.3 / §5.4 / §5.6 / §7 / §8；本类只做数值，不做能力与刷新。
 * 受影响的是"敌对生物"（{@link Enemy}）与末影龙；写入用 transient modifier，便于整体回收。
 * 跨存档时这些运行期修正会丢失，所以另用**会话令牌**（{@code summy_threat:session}）区分
 * "本会话已数值化"与"跨存档残留"：残留实体重新载入时按存档 TL 补一次重缩放。
 */
public final class MonsterScaler {
	/** 已经被本模组数值化过的标记（防重复叠加） */
	public static final String TAG_SCALED = "summy_threat:scaled";
	/** 该实体的 TL（计量与显示用） */
	public static final String TAG_TL = "summy_threat:tl";
	/** 卓越者标记（本轮只由命令强制） */
	public static final String TAG_EXIMUS = "summy_threat:eximus";
	/** 区域首领标记（本轮只由命令强制） */
	public static final String TAG_LEADER = "summy_threat:leader";
	/** 区域首领的 PCF（生命公式用，默认 20） */
	public static final String TAG_PCF = "summy_threat:pcf";
	/** 会话令牌：与当前服务端会话比对，用于识别跨存档残留实体 */
	public static final String TAG_SESSION = "summy_threat:session";
	/** 能力名列表（能力系统落地后写入；Jade 提示框读取显示） */
	public static final String TAG_ABILITIES = "summy_threat:abilities";

	/** 默认 PCF：正式 PCF 系统落地前，区域首领用 20 档 */
	public static final double DEFAULT_PCF = 20.0D;

	/** 当前会话令牌（开服时换发）；0 = 尚未开服 */
	private static long sessionToken;

	private static final UUID ID_ARMOR = UUID.fromString("6f5c1a2e-0007-4a0b-9d1e-000000000001");
	private static final UUID ID_TOUGHNESS = UUID.fromString("6f5c1a2e-0007-4a0b-9d1e-000000000002");
	private static final UUID ID_HEALTH = UUID.fromString("6f5c1a2e-0007-4a0b-9d1e-000000000003");
	private static final UUID ID_ATTACK = UUID.fromString("6f5c1a2e-0007-4a0b-9d1e-000000000004");
	private static final UUID ID_SPEED = UUID.fromString("6f5c1a2e-0007-4a0b-9d1e-000000000005");

	private static final String MODIFIER_NAME = "summy_threat:scaling";

	/** 类别：普通 / 卓越者 / 区域首领 / Boss */
	public enum Kind { NORMAL, EXIMUS, LEADER, BOSS }

	private MonsterScaler() {
	}

	/** 开服换发会话令牌：此后 join 的实体与本会话绑定；令牌不同的旧实体视为跨存档残留 */
	@SubscribeEvent
	public static void onServerAboutToStart(ServerAboutToStartEvent event) {
		sessionToken = System.nanoTime();
	}

	/** 实体加入世界：自然刷怪与命令召唤都会经过这里 */
	@SubscribeEvent
	public static void onEntityJoin(EntityJoinLevelEvent event) {
		if (event.getLevel().isClientSide()) {
			return;
		}
		if (!(event.getEntity() instanceof Mob mob) || !isScalable(mob)) {
			return;
		}
		CompoundTag data = mob.getPersistentData();
		if (data.contains(TAG_SCALED)) {
			if (data.getLong(TAG_SESSION) == sessionToken) {
				return; // 本会话已数值化（重复 join 不重复处理）
			}
			// 跨存档 / 跨会话的残留实体：运行期属性修正已丢失、但标记仍在（此前会一直保持原版数值）。
			// 用存档里的 TL 补一次重缩放——不依赖当前全局 TL；档位 none（短路）时跳过。
			if (ScalingState.tier() == ScalingState.Tier.NONE) {
				return;
			}
			int storedTl = data.contains(TAG_TL) ? data.getInt(TAG_TL) : 0;
			scaleNow(mob, storedTl);
			SummyThreat.LOGGER.info("[Summy Threat] 重新数值化残留实体 {}（TL {}）",
					mob.getName().getString(), storedTl);
			return;
		}
		if (!ScalingState.enabled()) {
			return;
		}
		scaleNow(mob, ScalingState.globalTl());
	}

	/** 是否属于"敌对生物"（含史莱姆等 Enemy 接口实现）或末影龙 */
	public static boolean isScalable(LivingEntity entity) {
		return entity instanceof Enemy || entity instanceof EnderDragon;
	}

	/** 判定类别（实体类型 + 命令打的标记） */
	public static Kind kindOf(LivingEntity entity) {
		if (entity instanceof Warden || entity instanceof EnderDragon || entity instanceof WitherBoss) {
			return Kind.BOSS;
		}
		if (entity.getPersistentData().getBoolean(TAG_LEADER)) {
			return Kind.LEADER;
		}
		if (entity.getPersistentData().getBoolean(TAG_EXIMUS)) {
			return Kind.EXIMUS;
		}
		return Kind.NORMAL;
	}

	/** 立即按当前全局 TL 数值化（命令调用；自然刷怪由 onEntityJoin 调用） */
	public static boolean scaleNow(LivingEntity entity, int tl) {
		return scaleNow(entity, tl, kindOf(entity));
	}

	public static boolean scaleNow(LivingEntity entity, int tl, Kind kind) {
		tl = ScalingState.clampTl(tl);
		double rHp = ScalingState.rHp();
		double rAtk = ScalingState.rAtk();

		double armorTarget;
		double toughnessTarget;
		double healthMultiplier;
		double attackMultiplier;
		double speedBonus = 0.0D;

		switch (kind) {
			case LEADER -> {
				// 区域首领：护甲固定 60 / 40；生命 = 基础 × 固定倍率 × (0.3 + PCF/28)；不叠加卓越者
				armorTarget = 60.0D;
				toughnessTarget = 40.0D;
				healthMultiplier = ScalingState.scale(leaderMultiplier(entity), rHp)
						* (Config.LEADER_PCF_BASE_FACTOR.get() + pcfOf(entity) / Config.LEADER_PCF_SCALE_DIVISOR.get());
				attackMultiplier = ScalingState.scale(leaderAttack(entity), rAtk);
			}
			case BOSS -> {
				// Boss：护甲固定 100 / 50；生命与攻击走 TL 曲线（锚点 TL0 = 原版、TL20 = 配置倍率）
				armorTarget = 100.0D;
				toughnessTarget = 50.0D;
				healthMultiplier = ScalingState.scale(
						bossTlCurve(tl, bossMultiplier(entity), Config.HEALTH_MULTIPLIER_PER_TL), rHp);
				attackMultiplier = ScalingState.scale(
						bossTlCurve(tl, bossAttack(entity), Config.ATTACK_MULTIPLIER_PER_TL), rAtk);
			}
			default -> {
				boolean eximus = kind == Kind.EXIMUS;
				armorTarget = ScalingState.armorValue(tl) + (eximus ? Config.EXIMUS_ARMOR_BONUS.get() : 0.0D);
				toughnessTarget = ScalingState.toughnessValue(tl) + (eximus ? Config.EXIMUS_TOUGHNESS_BONUS.get() : 0.0D);
				healthMultiplier = ScalingState.healthMultiplier(tl)
						* (eximus ? ScalingState.eximusMultiplier(tl) : 1.0D);
				attackMultiplier = ScalingState.attackMultiplier(tl)
						* (eximus ? Config.EXIMUS_DAMAGE_MULTIPLIER.get() : 1.0D);
				speedBonus = eximus ? Config.EXIMUS_SPEED_BONUS.get() : 0.0D;
			}
		}

		// 护甲 / 韧性：以"表值 − 当前基础值"的加法把它抬到目标值
		setAddition(entity, Attributes.ARMOR, ID_ARMOR, armorTarget);
		setAddition(entity, Attributes.ARMOR_TOUGHNESS, ID_TOUGHNESS, toughnessTarget);
		// 生命：乘性；写完后补满血（新刷的怪本来就是满血）
		setMultiplier(entity, Attributes.MAX_HEALTH, ID_HEALTH, healthMultiplier);
		// 攻击：乘性
		setMultiplier(entity, Attributes.ATTACK_DAMAGE, ID_ATTACK, attackMultiplier);
		// 移速：仅卓越者 +5%
		setMultiplier(entity, Attributes.MOVEMENT_SPEED, ID_SPEED, 1.0D + speedBonus);

		entity.setHealth(entity.getMaxHealth());

		CompoundTag data = entity.getPersistentData();
		data.putBoolean(TAG_SCALED, true);
		data.putInt(TAG_TL, tl);
		data.putLong(TAG_SESSION, sessionToken);

		// TL 能力：新怪按 TL 区间抽取（0/1/2/3/4；卓越者 +1）；已有标签（残留 / 命令覆盖）则保留
		if (AbilityEffects.enabled()) {
			if (!data.contains(TAG_ABILITIES)) {
				int extra = kind == Kind.EXIMUS ? 1 : 0;
				AbilityEffects.setAbilities(entity, tl, AbilityEffects.roll(tl, extra, entity.getRandom()));
			} else {
				AbilityEffects.setAbilities(entity, tl, data.getString(TAG_ABILITIES));
			}
		}

		if (kind != Kind.NORMAL) {
			SummyThreat.LOGGER.info("[Summy Threat] 数值化 {}（{}，TL {}）：护甲 {} / 韧性 {}，生命 ×{}，攻击 ×{}，能力 {}",
					entity.getName().getString(), kind, tl, round(armorTarget), round(toughnessTarget),
					round(healthMultiplier), round(attackMultiplier),
					data.getString(TAG_ABILITIES).isEmpty() ? "无" : data.getString(TAG_ABILITIES));
		}
		return true;
	}

	/** 显示名（命令用）：生命 ×M ×E 等口径的中文摘要 */
	public static Component describe(LivingEntity entity) {
		Kind kind = kindOf(entity);
		int tl = entity.getPersistentData().getInt(TAG_TL);
		String abilities = entity.getPersistentData().getString(TAG_ABILITIES);
		return Component.literal("TL=%d/%s 最大生命=%.1f 护甲=%.1f 韧性=%.1f 攻击=%.1f 能力=%s".formatted(
				tl, kind, entity.getMaxHealth(),
				value(entity, Attributes.ARMOR), value(entity, Attributes.ARMOR_TOUGHNESS),
				value(entity, Attributes.ATTACK_DAMAGE), abilities.isEmpty() ? "无" : abilities));
	}

	/** Boss 的 TL 曲线：TL0 = 1.0、TL20 = 锚点倍率；形状与普通怪 M/A 表同形（不含档位压缩）。 */
	private static double bossTlCurve(int tl, double anchor, ForgeConfigSpec.DoubleValue[] table) {
		double v = table[ScalingState.clampTl(tl)].get();
		double v20 = table[20].get();
		if (v20 <= 1.0D) {
			return 1.0D;
		}
		return 1.0D + (anchor - 1.0D) * (v - 1.0D) / (v20 - 1.0D);
	}

	private static double value(LivingEntity entity, net.minecraft.world.entity.ai.attributes.Attribute attribute) {
		AttributeInstance instance = entity.getAttribute(attribute);
		return instance == null ? 0.0D : instance.getValue();
	}

	private static void setAddition(LivingEntity entity, net.minecraft.world.entity.ai.attributes.Attribute attribute,
			UUID id, double target) {
		AttributeInstance instance = entity.getAttribute(attribute);
		if (instance == null) {
			return;
		}
		instance.removeModifier(id);
		double delta = target - instance.getBaseValue();
		if (Math.abs(delta) > 1.0E-6D) {
			instance.addTransientModifier(new AttributeModifier(id, MODIFIER_NAME, delta,
					AttributeModifier.Operation.ADDITION));
		}
	}

	private static void setMultiplier(LivingEntity entity, net.minecraft.world.entity.ai.attributes.Attribute attribute,
			UUID id, double multiplier) {
		AttributeInstance instance = entity.getAttribute(attribute);
		if (instance == null) {
			return;
		}
		instance.removeModifier(id);
		double delta = multiplier - 1.0D;
		if (Math.abs(delta) > 1.0E-6D) {
			instance.addTransientModifier(new AttributeModifier(id, MODIFIER_NAME, delta,
					AttributeModifier.Operation.MULTIPLY_TOTAL));
		}
	}

	private static double pcfOf(LivingEntity entity) {
		CompoundTag data = entity.getPersistentData();
		return data.contains(TAG_PCF) ? data.getDouble(TAG_PCF) : DEFAULT_PCF;
	}

	private static double leaderMultiplier(LivingEntity entity) {
		if (entity.getType() == net.minecraft.world.entity.EntityType.PIGLIN_BRUTE) {
			return Config.LEADER_PIGLIN_BRUTE_MULTIPLIER.get();
		}
		if (entity.getType() == net.minecraft.world.entity.EntityType.ELDER_GUARDIAN) {
			return Config.LEADER_ELDER_GUARDIAN_MULTIPLIER.get();
		}
		return Config.LEADER_ZOMBIE_MULTIPLIER.get();
	}

	private static double leaderAttack(LivingEntity entity) {
		if (entity.getType() == net.minecraft.world.entity.EntityType.PIGLIN_BRUTE) {
			return Config.LEADER_PIGLIN_BRUTE_ATTACK.get();
		}
		if (entity.getType() == net.minecraft.world.entity.EntityType.ELDER_GUARDIAN) {
			return Config.LEADER_ELDER_GUARDIAN_ATTACK.get();
		}
		return Config.LEADER_ZOMBIE_ATTACK.get();
	}

	private static double bossMultiplier(LivingEntity entity) {
		if (entity instanceof Warden) {
			return Config.BOSS_WARDEN_MULTIPLIER.get();
		}
		if (entity instanceof EnderDragon) {
			return Config.BOSS_ENDER_DRAGON_MULTIPLIER.get();
		}
		return Config.BOSS_WITHER_MULTIPLIER.get();
	}

	private static double bossAttack(LivingEntity entity) {
		if (entity instanceof Warden) {
			return Config.BOSS_WARDEN_ATTACK.get();
		}
		if (entity instanceof EnderDragon) {
			return Config.BOSS_ENDER_DRAGON_ATTACK.get();
		}
		return Config.BOSS_WITHER_ATTACK.get();
	}

	/** 区域首领名单判定（命令用）：只有这三种原版实体可以做区域首领 */
	public static boolean isLeaderEligible(LivingEntity entity) {
		return entity.getType() == net.minecraft.world.entity.EntityType.ZOMBIE
				|| entity.getType() == net.minecraft.world.entity.EntityType.PIGLIN_BRUTE
				|| entity.getType() == net.minecraft.world.entity.EntityType.ELDER_GUARDIAN;
	}

	private static String round(double v) {
		return String.format("%.2f", v);
	}
}
