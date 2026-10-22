package com.summy.threat.devcheck;

import com.summy.threat.SummyThreat;
import com.summy.threat.combat.ArmorDamageFormula;
import com.summy.threat.combat.ArmorPenetration;
import com.summy.threat.combat.ArmorShred;
import com.summy.threat.eximus.EximusShield;
import com.summy.threat.scaling.MonsterScaler;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Husk;

import java.util.ArrayList;
import java.util.List;

/**
 * A7/B5 无世界副作用断言：真实受伤管线、边界清理与腐蚀窗口。
 *
 * <p>腐蚀过期需要跨 tick 观察，因此由 ThreatDevCheck 在 292 / 340 / 430 tick 分阶段调用。
 */
final class ArmorPenetrationDevCheck {
	private static final double EPSILON = 0.05D;
	private static final List<String> FAILURES = new ArrayList<>();
	private static Husk normalTarget;
	private static Husk bossTarget;
	private static Husk corrosionDefault;
	private static Husk corrosionCustom;
	private static Husk shieldTarget;

	private ArmorPenetrationDevCheck() {
	}

	static List<String> start(ServerPlayer player) {
		cleanupEntities();
		FAILURES.clear();
		ServerLevel level = player.serverLevel();
		normalTarget = husk(level, 38.0D, 28.0D);
		bossTarget = husk(level, 100.0D, 50.0D);
		checkPlayerFormula();
		checkActualHits(level);
		checkContextBoundaries(level);
		checkCorrosionDefaultsAndConfig(level);
		checkPenetrationThroughShield(level);
		return drain();
	}

	static List<String> checkShortWindow() {
		if (corrosionCustom != null) {
			ThreatDevCheck.checkDetailed("A7 腐蚀 2 秒配置到期后护甲恢复 100",
					close(corrosionCustom.getAttributeValue(Attributes.ARMOR), 100.0D),
					"armor=" + corrosionCustom.getAttributeValue(Attributes.ARMOR));
		}
		if (corrosionDefault != null) {
			ThreatDevCheck.checkDetailed("A7 默认 6 秒腐蚀在 2.4 秒时仍生效",
					close(corrosionDefault.getAttributeValue(Attributes.ARMOR), 70.0D),
					"armor=" + corrosionDefault.getAttributeValue(Attributes.ARMOR));
		}
		return drain();
	}

	static List<String> finish() {
		if (corrosionDefault != null) {
			ThreatDevCheck.checkDetailed("A7 默认 6 秒腐蚀到期后护甲恢复 100",
					close(corrosionDefault.getAttributeValue(Attributes.ARMOR), 100.0D),
					"armor=" + corrosionDefault.getAttributeValue(Attributes.ARMOR));
		}
		cleanupEntities();
		SummyThreat.LOGGER.info("[A7] 护甲穿透/削甲检查结束：失败 {}", FAILURES.size());
		return drain();
	}

	private static void checkPlayerFormula() {
		double base = playerDr(30.0D, 18.0D, 0.0D);
		double pen30 = playerDr(30.0D, 18.0D, 0.30D);
		double pen80 = playerDr(30.0D, 18.0D, 0.80D);
		ThreatDevCheck.checkDetailed("A7 玩家 A30/T18 原始减伤 76.7%",
				close(base * 100.0D, 76.74D), "dr=" + base);
		ThreatDevCheck.checkDetailed("A7 玩家 A30/T18 30% 穿甲后 69.8%",
				close(pen30 * 100.0D, 69.79D), "dr=" + pen30);
		ThreatDevCheck.checkDetailed("A7 玩家 A30/T18 80% 穿甲后 39.8%",
				close(pen80 * 100.0D, 39.76D), "dr=" + pen80);
	}

	private static void checkActualHits(ServerLevel level) {
		Husk attacker = husk(level, 0.0D, 0.0D);
		DamageSource source = level.damageSources().mobAttack(attacker);
		checkDamage("A7 TL20 普通怪无穿甲 100→10", normalTarget, source, 0.0D, 10.0D);
		checkDamage("A7 负穿甲按 0 处理", normalTarget, source, -0.5D, 10.0D);
		checkDamage("A7 TL20 普通怪 30% 穿甲 100→21.4", normalTarget, source, 0.30D, 21.42D);
		checkDamage("A7 TL20 普通怪 80% 穿甲 100→58", normalTarget, source, 0.80D, 58.00D);
		checkDamage("A7 Boss 型目标 30% 穿甲仍 100→10", bossTarget, source, 0.30D, 10.0D);
		checkDamage("A7 Boss 型目标 80% 穿甲 100→16.2", bossTarget, source, 0.80D, 16.17D);
		checkDamage("A7 100% 穿甲移除后台护甲减伤", normalTarget, source, 1.0D, 100.0D);
		checkDamage("A7 超过 100% 的穿甲被截断", normalTarget, source, 1.5D, 100.0D);
		attacker.discard();
	}

	private static void checkContextBoundaries(ServerLevel level) {
		Husk attacker = husk(level, 0.0D, 0.0D);
		DamageSource source = level.damageSources().mobAttack(attacker);
		normalTarget.invulnerableTime = 20;
		normalTarget.hurtTime = 10;
		float before = normalTarget.getHealth();
		boolean landed = ArmorPenetration.hurt(normalTarget, source, 100.0F, 0.8D);
		ThreatDevCheck.checkDetailed("A7 受击帧拒绝时不落地", !landed
				&& close(normalTarget.getHealth(), before), "landed=" + landed
				+ " health=" + normalTarget.getHealth());
		normalTarget.invulnerableTime = 0;
		normalTarget.hurtTime = 0;
		checkDamage("A7 失败上下文不泄漏到下一击", normalTarget, source, 0.0D, 10.0D);
		checkDamage("A7 穿甲不累积到第二击", normalTarget, source, 0.0D, 10.0D);
		attacker.discard();
	}

	private static void checkCorrosionDefaultsAndConfig(ServerLevel level) {
		corrosionDefault = husk(level, 100.0D, 0.0D);
		for (int i = 0; i < 3; i++) {
			ArmorShred.apply(corrosionDefault, "corrosion_dev_default", 10.0D, 3, 120);
		}
		ThreatDevCheck.checkDetailed("A7 默认腐蚀三层 100→70",
				close(corrosionDefault.getAttributeValue(Attributes.ARMOR), 70.0D),
				"armor=" + corrosionDefault.getAttributeValue(Attributes.ARMOR));
		ArmorShred.apply(corrosionDefault, "corrosion_dev_default", 10.0D, 3, 120);
		ThreatDevCheck.checkDetailed("A7 第四层刷新最旧层且不超过上限",
				close(corrosionDefault.getAttributeValue(Attributes.ARMOR), 70.0D),
				"armor=" + corrosionDefault.getAttributeValue(Attributes.ARMOR));

		corrosionCustom = husk(level, 100.0D, 0.0D);
		ArmorShred.apply(corrosionCustom, "corrosion_dev_custom", 20.0D, 2, 40);
		ArmorShred.apply(corrosionCustom, "corrosion_dev_custom", 20.0D, 2, 40);
		ThreatDevCheck.checkDetailed("A7 参数 20%×2 层 100→60",
				close(corrosionCustom.getAttributeValue(Attributes.ARMOR), 60.0D),
				"armor=" + corrosionCustom.getAttributeValue(Attributes.ARMOR));
		ArmorShred.apply(corrosionCustom, "shred_dev_other", 5.0D, 1, 40);
		ThreatDevCheck.checkDetailed("A7 不同削甲来源按加法汇总 100→55",
				close(corrosionCustom.getAttributeValue(Attributes.ARMOR), 55.0D),
				"armor=" + corrosionCustom.getAttributeValue(Attributes.ARMOR));
	}

	private static void checkPenetrationThroughShield(ServerLevel level) {
		shieldTarget = husk(level, 100.0D, 0.0D);
		shieldTarget.getPersistentData().putBoolean(MonsterScaler.TAG_EXIMUS, true);
		EximusShield.refresh(shieldTarget, 20, true);
		Husk attacker = husk(level, 0.0D, 0.0D);
		DamageSource source = level.damageSources().mobAttack(attacker);

		double before = EximusShield.current(shieldTarget);
		hurt(shieldTarget, source, 0.0D);
		double ordinaryLoss = before - EximusShield.current(shieldTarget);
		EximusShield.refresh(shieldTarget, 20, true);
		before = EximusShield.current(shieldTarget);
		hurt(shieldTarget, source, 0.80D);
		double penetratedLoss = before - EximusShield.current(shieldTarget);
		ThreatDevCheck.checkDetailed("A7 穿甲进入卓越护盾且增加吸收量",
				close(ordinaryLoss, 10.0D) && close(penetratedLoss, 59.75D),
				"ordinary=" + ordinaryLoss + " penetrated=" + penetratedLoss);
		attacker.discard();
	}

	private static void checkDamage(String label, LivingEntity target, DamageSource source,
			double percent, double expected) {
		float dealt = hurt(target, source, percent);
		ThreatDevCheck.checkDetailed(label, close(dealt, expected),
				"dealt=" + dealt + " expected=" + expected + " armor="
						+ target.getAttributeValue(Attributes.ARMOR));
	}

	private static float hurt(LivingEntity target, DamageSource source, double percent) {
		target.invulnerableTime = 0;
		target.hurtTime = 0;
		target.setHealth(target.getMaxHealth());
		float before = target.getHealth();
		ArmorPenetration.hurt(target, source, 100.0F, percent);
		return before - target.getHealth();
	}

	private static double playerDr(double armor, double toughness, double penetration) {
		double remainingArmor = Math.max(0.0D, armor * (1.0D - penetration));
		double ea = ArmorDamageFormula.effectiveArmor(remainingArmor, toughness, 15.0D);
		return ArmorDamageFormula.playerReduction(ea, 20.0D, 0.0D, 0.85D);
	}

	private static Husk husk(ServerLevel level, double armor, double toughness) {
		Husk husk = EntityType.HUSK.create(level);
		if (husk == null) {
			throw new IllegalStateException("无法创建 A7 测试 Husk");
		}
		husk.setPos(level.getSharedSpawnPos().getX() + 40.0D,
				level.getSharedSpawnPos().getY() + 5.0D,
				level.getSharedSpawnPos().getZ() + 40.0D);
		level.addFreshEntity(husk);
		husk.setNoAi(true);
		husk.setNoGravity(true);
		husk.setPersistenceRequired();
		setAttributeValue(husk, Attributes.MAX_HEALTH, 1000.0D);
		setAttributeValue(husk, Attributes.ARMOR, armor);
		setAttributeValue(husk, Attributes.ARMOR_TOUGHNESS, toughness);
		husk.setHealth(husk.getMaxHealth());
		return husk;
	}

	/** 抵消出生存档留下的属性 modifier，让测试目标最终值精确等于期望值。 */
	private static void setAttributeValue(LivingEntity entity,
			net.minecraft.world.entity.ai.attributes.Attribute attribute, double desired) {
		net.minecraft.world.entity.ai.attributes.AttributeInstance instance =
				entity.getAttribute(attribute);
		if (instance == null) {
			throw new IllegalStateException("目标缺少属性：" + attribute);
		}
		double modifierDelta = instance.getValue() - instance.getBaseValue();
		instance.setBaseValue(Math.max(0.0D, desired - modifierDelta));
	}

	private static List<String> drain() {
		List<String> out = new ArrayList<>(FAILURES);
		FAILURES.clear();
		return out;
	}

	private static void cleanupEntities() {
		for (Husk husk : new Husk[]{normalTarget, bossTarget, corrosionDefault,
				corrosionCustom, shieldTarget}) {
			if (husk != null) {
				husk.discard();
			}
		}
		normalTarget = null;
		bossTarget = null;
		corrosionDefault = null;
		corrosionCustom = null;
		shieldTarget = null;
	}

	private static boolean close(double actual, double expected) {
		return Math.abs(actual - expected) <= EPSILON;
	}
}
