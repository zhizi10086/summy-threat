package com.summy.threat.compat.sr;

import com.summy.threat.SummyThreat;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.fml.ModList;

import javax.annotation.Nullable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * SR 慈善包击杀事务查询的可选桥接。
 *
 * <p>SR 未安装或没有 2.0.1 查询方法时直接返回 null；本类不加载任何 SR 类型，
 * 也不把 SR 加入 ST 的生产依赖。
 */
public final class SRBundleCompat {
	private static final String LOOT_CLASS = "com.summy.reliquary.loot.HumbleingBundleLoot";
	private static final String METHOD_NAME = "specialDropBonusPlayer";
	private static boolean resolved;
	private static boolean warnedMissing;
	@Nullable
	private static Method method;

	private SRBundleCompat() {
	}

	@Nullable
	public static ServerPlayer specialDropBonusPlayer(LivingEntity victim) {
		if (victim == null || !ModList.get().isLoaded("summy_reliquary")) {
			return null;
		}
		Method query = method();
		if (query == null) {
			return null;
		}
		try {
			Object result = query.invoke(null, victim);
			return result instanceof ServerPlayer player ? player : null;
		} catch (IllegalAccessException | InvocationTargetException exception) {
			warnOnce("调用 SR 慈善包查询失败，按无额外判定处理：" + exception.getClass().getSimpleName());
			return null;
		}
	}

	@Nullable
	private static synchronized Method method() {
		if (resolved) {
			return method;
		}
		resolved = true;
		try {
			Class<?> type = Class.forName(LOOT_CLASS, false, SRBundleCompat.class.getClassLoader());
			Method found = type.getMethod(METHOD_NAME, LivingEntity.class);
			if (found.getReturnType() != ServerPlayer.class) {
				warnOnce("SR 慈善包查询返回类型不是 ServerPlayer，已安全降级");
				return null;
			}
			method = found;
		} catch (ClassNotFoundException | NoSuchMethodException | LinkageError exception) {
			warnOnce("未找到 SR 2.0.1 慈善包查询接口，按无额外判定处理");
		}
		return method;
	}

	private static void warnOnce(String message) {
		if (!warnedMissing) {
			warnedMissing = true;
			SummyThreat.LOGGER.warn("[Summy Threat] {}", message);
		}
	}
}
