package com.summy.threat.devcheck;

import com.summy.threat.SummyThreat;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.LivingEntity;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * A11 自动攻击的 dev-only 反射桥。
 *
 * <p>自动攻击必须走 {@code Player.attack}，这样锋利、魔剑、出其不意、圣光与
 * CombatMeter 都会按真实近战管线结算；这里只补两件原版/跨模组没有公开入口的东西：
 * <ul>
 *     <li>{@code LivingEntity.attackStrengthTicker}：每次攻击前补满蓄力；</li>
 *     <li>SR {@code HolyLightEffect} 的自检计数器：reset / triggerCount / tickServer / clearColumns。</li>
 * </ul>
 *
 * <p>反射失败时由调用方明确中止，不静默产生空样本。正式 jar 不包含 devcheck 包。
 */
final class AutoAttackDriver {
	private static final int FULL_CHARGE_TICKS = 100;
	private static final Field ATTACK_STRENGTH_TICKER = findAttackStrengthTicker();
	private static final Class<?> HOLY_LIGHT = findClass("com.summy.reliquary.effect.HolyLightEffect");
	private static final Method HOLY_LIGHT_RESET = findMethod(HOLY_LIGHT, "reset");
	private static final Method HOLY_LIGHT_COUNT = findMethod(HOLY_LIGHT, "triggerCount");
	private static final Method HOLY_LIGHT_TICK = findMethod(HOLY_LIGHT, "tickServer", MinecraftServer.class);
	private static final Method HOLY_LIGHT_CLEAR = findMethod(HOLY_LIGHT, "clearColumns");
	private static final Class<?> SE_COMBAT_FORMULAS = findClassQuietly("com.summy.enchantment.combat.CombatFormulas");
	private static final Method SE_STRENGTH_MULTIPLIER =
			findMethodQuietly(SE_COMBAT_FORMULAS, "strengthMultiplier", LivingEntity.class);
	private static boolean holyLightErrorLogged;

	private AutoAttackDriver() {
	}

	static boolean attackAvailable() {
		return ATTACK_STRENGTH_TICKER != null;
	}

	static boolean holyLightAvailable() {
		return HOLY_LIGHT_RESET != null && HOLY_LIGHT_COUNT != null
				&& HOLY_LIGHT_TICK != null && HOLY_LIGHT_CLEAR != null;
	}

	static boolean setAttackStrength(LivingEntity entity) {
		if (ATTACK_STRENGTH_TICKER == null) {
			return false;
		}
		try {
			ATTACK_STRENGTH_TICKER.setInt(entity, FULL_CHARGE_TICKS);
			return true;
		} catch (Throwable throwable) {
			SummyThreat.LOGGER.error("[A11] 无法补满 attackStrengthTicker", throwable);
			return false;
		}
	}

	static void resetHolyLight() {
		invoke(HOLY_LIGHT_RESET);
	}

	static int holyLightCount() {
		if (HOLY_LIGHT_COUNT == null) {
			return -1;
		}
		try {
			return ((Number) HOLY_LIGHT_COUNT.invoke(null)).intValue();
		} catch (Throwable throwable) {
			logHolyLightError(throwable);
			return -1;
		}
	}

	static void tickHolyLight(MinecraftServer server) {
		if (HOLY_LIGHT_TICK == null) {
			return;
		}
		try {
			HOLY_LIGHT_TICK.invoke(null, server);
		} catch (Throwable throwable) {
			logHolyLightError(throwable);
		}
	}

	static void clearHolyLightColumns() {
		invoke(HOLY_LIGHT_CLEAR);
	}

	/** 读取 SE 命中时实际使用的力量倍率；未安装 SE 时返回 1。 */
	static float seStrengthMultiplier(LivingEntity entity) {
		if (SE_STRENGTH_MULTIPLIER == null) {
			return 1.0F;
		}
		try {
			return ((Number) SE_STRENGTH_MULTIPLIER.invoke(null, entity)).floatValue();
		} catch (Throwable throwable) {
			SummyThreat.LOGGER.warn("[A11] 无法读取 SE 力量倍率", throwable);
			return 1.0F;
		}
	}

	private static void invoke(Method method) {
		if (method == null) {
			return;
		}
		try {
			method.invoke(null);
		} catch (Throwable throwable) {
			logHolyLightError(throwable);
		}
	}

	private static void logHolyLightError(Throwable throwable) {
		if (holyLightErrorLogged) {
			return;
		}
		holyLightErrorLogged = true;
		SummyThreat.LOGGER.warn("[A11] 圣光计数器反射失败，本轮圣光统计将为 -1", throwable);
	}

	private static Field findAttackStrengthTicker() {
		try {
			Field field = LivingEntity.class.getDeclaredField("attackStrengthTicker");
			field.setAccessible(true);
			return field;
		} catch (Throwable throwable) {
			SummyThreat.LOGGER.error("[A11] 找不到 attackStrengthTicker，自动攻击不可用", throwable);
			return null;
		}
	}

	private static Class<?> findClass(String name) {
		try {
			return Class.forName(name);
		} catch (Throwable ignored) {
			SummyThreat.LOGGER.info("[A11] 未找到 {}，圣光计数器跳过（未安装 SR 时属正常）", name);
			return null;
		}
	}

	private static Class<?> findClassQuietly(String name) {
		try {
			return Class.forName(name);
		} catch (Throwable ignored) {
			return null;
		}
	}

	private static Method findMethod(Class<?> type, String name, Class<?>... parameterTypes) {
		if (type == null) {
			return null;
		}
		try {
			return type.getMethod(name, parameterTypes);
		} catch (Throwable throwable) {
			SummyThreat.LOGGER.warn("[A11] 找不到 {}.{}，圣光计数器跳过", type.getName(), name);
			return null;
		}
	}

	private static Method findMethodQuietly(Class<?> type, String name, Class<?>... parameterTypes) {
		if (type == null) {
			return null;
		}
		try {
			return type.getMethod(name, parameterTypes);
		} catch (Throwable ignored) {
			return null;
		}
	}
}
