package com.summy.threat.devcheck;

import com.summy.threat.SummyThreat;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

import java.lang.reflect.Method;

/**
 * A11 恶魔毕业配置的 dev-only 反射桥。
 *
 * <p>用 SR 自己的「创世纪」全量重置清掉天使 / 启示 / 旧七罪 / 旧恶魔状态，再设置：
 * 恶魔标记、已签约、邪恶 1000、全部 EvilUnlock 位图、恶魔王冠已发放。
 * 随后同步 SR 的阶段栏位与恶魔契约栏位。
 *
 * <p>正式 jar 不包含 devcheck 包；任一关键反射失败都由调用方明确中止。
 */
final class DemonSetupBridge {
	private static final Class<?> GENESIS = findClass("com.summy.reliquary.item.GenesisItem");
	private static final Method GENESIS_RESET = findDeclaredMethod(GENESIS, "performReset", ServerPlayer.class);
	private static final Class<?> FLAGS = findClass("com.summy.reliquary.effect.PlayerFlags");
	private static final Method SET_ANGEL = findMethod(FLAGS, "setAngel", ServerPlayer.class, boolean.class);
	private static final Method SET_DEMON = findMethod(FLAGS, "setDemon", ServerPlayer.class, boolean.class);
	private static final Method SET_DEMON_SEALED = findMethod(FLAGS, "setDemonSealed", ServerPlayer.class, boolean.class);
	private static final Method SET_REVELATION = findMethod(FLAGS, "setRevelationObtained", ServerPlayer.class, boolean.class);
	private static final Method SET_EVIL = findMethod(FLAGS, "setEvil", ServerPlayer.class, double.class);
	private static final Method SET_UNLOCKS = findMethod(FLAGS, "setEvilUnlocks", ServerPlayer.class, int.class);
	private static final Method SET_CROWN = findMethod(FLAGS, "setDevilCrownGranted", ServerPlayer.class, boolean.class);
	private static final Class<?> EVIL_UNLOCK = findClass("com.summy.reliquary.effect.EvilUnlock");
	private static final Method UNLOCK_VALUES = findMethod(EVIL_UNLOCK, "values");
	private static final Method UNLOCK_BIT = findMethod(EVIL_UNLOCK, "bit");
	private static final Class<?> PACT = findClass("com.summy.reliquary.effect.DemonPact");
	private static final Method PACT_SYNC = findMethod(PACT, "syncSlot", ServerPlayer.class);
	private static final Class<?> SLOTS = findClass("com.summy.reliquary.effect.SlotSizing");
	private static final Method SLOT_SYNC = findMethod(SLOTS, "syncNow", ServerPlayer.class);
	private static final Class<?> ATTRIBUTES = findClass("com.summy.reliquary.effect.AttributeManager");
	private static final Method ATTRIBUTE_APPLY = findMethod(ATTRIBUTES, "apply", LivingEntity.class);

	private DemonSetupBridge() {
	}

	static boolean available() {
		return GENESIS_RESET != null && SET_ANGEL != null && SET_DEMON != null && SET_DEMON_SEALED != null
				&& SET_REVELATION != null && SET_EVIL != null && SET_UNLOCKS != null && SET_CROWN != null
				&& UNLOCK_VALUES != null && UNLOCK_BIT != null && PACT_SYNC != null && SLOT_SYNC != null
				&& ATTRIBUTE_APPLY != null;
	}

	/** SR「创世纪」全量重置；会传送玩家并播放表现，调用方需重新 teleport。 */
	static boolean performFullReset(ServerPlayer player) {
		if (GENESIS_RESET == null) {
			return false;
		}
		try {
			GENESIS_RESET.invoke(null, player);
			return true;
		} catch (Throwable throwable) {
			SummyThreat.LOGGER.error("[A11] 恶魔配置：GenesisItem.performReset 失败", throwable);
			return false;
		}
	}

	/** 设置恶魔毕业状态并同步栏位/属性。 */
	static boolean applyDemonState(ServerPlayer player) {
		if (!available()) {
			SummyThreat.LOGGER.error("[A11] 恶魔配置：反射桥不完整，无法设置恶魔状态");
			return false;
		}
		try {
			SET_ANGEL.invoke(null, player, false);
			SET_DEMON.invoke(null, player, true);
			SET_DEMON_SEALED.invoke(null, player, true);
			SET_REVELATION.invoke(null, player, false);
			SET_EVIL.invoke(null, player, 1000.0D);
			SET_UNLOCKS.invoke(null, player, allUnlockBits());
			SET_CROWN.invoke(null, player, true);
			PACT_SYNC.invoke(null, player);
			SLOT_SYNC.invoke(null, player);
			ATTRIBUTE_APPLY.invoke(null, player);
			return true;
		} catch (Throwable throwable) {
			SummyThreat.LOGGER.error("[A11] 恶魔配置：设置恶魔状态失败", throwable);
			return false;
		}
	}

	/** 只同步 SR 的阶段栏位（天使 / 恶魔自动佩戴前都可用）。 */
	static boolean syncSlots(ServerPlayer player) {
		if (SLOT_SYNC == null) {
			return false;
		}
		try {
			SLOT_SYNC.invoke(null, player);
			return true;
		} catch (Throwable throwable) {
			SummyThreat.LOGGER.error("[A11] SR 栏位同步失败", throwable);
			return false;
		}
	}

	private static int allUnlockBits() throws ReflectiveOperationException {
		Object[] values = (Object[]) UNLOCK_VALUES.invoke(null);
		int bits = 0;
		for (Object value : values) {
			bits |= ((Number) UNLOCK_BIT.invoke(value)).intValue();
		}
		return bits;
	}

	private static Class<?> findClass(String name) {
		try {
			return Class.forName(name);
		} catch (Throwable throwable) {
			SummyThreat.LOGGER.warn("[A11] 恶魔配置：找不到 {}（未安装 SR 时属正常）", name);
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
			SummyThreat.LOGGER.warn("[A11] 恶魔配置：找不到 {}.{}", type.getName(), name);
			return null;
		}
	}

	private static Method findDeclaredMethod(Class<?> type, String name, Class<?>... parameterTypes) {
		if (type == null) {
			return null;
		}
		try {
			Method method = type.getDeclaredMethod(name, parameterTypes);
			method.setAccessible(true);
			return method;
		} catch (Throwable throwable) {
			SummyThreat.LOGGER.warn("[A11] 恶魔配置：找不到私有方法 {}.{}", type.getName(), name);
			return null;
		}
	}
}
