package com.summy.threat.equipment;

import com.summy.threat.SummyThreat;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Method;
import java.util.function.Consumer;

/**
 * Tetra 弓弩的可选事件桥。
 *
 * <p>这里通过事件类型名注册监听器，避免生产 JAR 引用或打包 Tetra 类。
 * 两个事件由 Tetra 同步连续触发，齐射内的每支投射物共用同一 shot ID。</p>
 */
public final class TetraRangedCompat {
	private static final String LOOSE_EVENT =
			"se.mickelus.tetra.event.ModularLooseProjectilesEvent";
	private static final String SPAWN_EVENT =
			"se.mickelus.tetra.event.ModularProjectileSpawnEvent";

	private static Method looseShooter;
	private static Method looseFiringStack;
	private static Method looseDrawProgress;
	private static Method looseCount;
	private static Method spawnShooter;
	private static Method spawnFiringStack;
	private static Method spawnProjectile;
	private static boolean failed;

	private TetraRangedCompat() {
	}

	public static void register() {
		if (!ModList.get().isLoaded("tetra") || !installMethods()) {
			return;
		}
		addListener(LOOSE_EVENT, TetraRangedCompat::onLoose);
		addListener(SPAWN_EVENT, TetraRangedCompat::onSpawn);
	}

	private static boolean installMethods() {
		try {
			Class<?> loose = Class.forName(LOOSE_EVENT);
			looseShooter = loose.getMethod("getShooter");
			looseFiringStack = loose.getMethod("getFiringStack");
			looseDrawProgress = loose.getMethod("getDrawProgress");
			looseCount = loose.getMethod("getCount");
			Class<?> spawn = Class.forName(SPAWN_EVENT);
			spawnShooter = spawn.getMethod("getShooter");
			spawnFiringStack = spawn.getMethod("getFiringStack");
			spawnProjectile = spawn.getMethod("getProjectileEntity");
			return true;
		} catch (Throwable throwable) {
			if (!failed) {
				failed = true;
				SummyThreat.LOGGER.error("[Tetra] 6.17.0 射击事件接口不可用，兼容桥停用", throwable);
			}
			return false;
		}
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private static void addListener(String eventClassName, Consumer<?> listener) {
		try {
			Class<?> eventClass = Class.forName(eventClassName);
			if (!Event.class.isAssignableFrom(eventClass)) {
				throw new IllegalArgumentException(eventClassName + " 不是 Forge Event");
			}
			MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false,
					(Class) eventClass, (Consumer) listener);
		} catch (Throwable throwable) {
			if (!failed) {
				failed = true;
				SummyThreat.LOGGER.error("[Tetra] 无法注册射击事件监听器", throwable);
			}
		}
	}

	private static void onLoose(Object event) {
		try {
			if (!(looseShooter.invoke(event) instanceof ServerPlayer player)) {
				return;
			}
			Object firing = looseFiringStack.invoke(event);
			if (!(firing instanceof ItemStack stack) || stack.isEmpty()
					|| (int) looseCount.invoke(event) <= 0) {
				return;
			}
			String itemId = EquipmentTypeResolver.itemId(stack);
			if (!EquipmentCompatibility.isTetraRanged(itemId)) {
				return;
			}
			int drawProgress = (int) looseDrawProgress.invoke(event);
			if ("tetra:modular_bow".equals(itemId) && drawProgress < 20) {
				return;
			}
			RangedWeaponRuntime.beginExternalShot(player, stack,
					RangedShotSource.TETRA);
		} catch (Throwable throwable) {
			logRuntimeFailure(throwable);
		}
	}

	private static void onSpawn(Object event) {
		try {
			if (!(spawnShooter.invoke(event) instanceof ServerPlayer player)
					|| !(spawnFiringStack.invoke(event) instanceof ItemStack stack)
					|| !(spawnProjectile.invoke(event) instanceof AbstractArrow arrow)) {
				return;
			}
			RangedWeaponRuntime.attachTetraProjectile(player, stack, arrow);
		} catch (Throwable throwable) {
			logRuntimeFailure(throwable);
		}
	}

	private static void logRuntimeFailure(Throwable throwable) {
		if (!failed) {
			failed = true;
			SummyThreat.LOGGER.error("[Tetra] 射击兼容桥运行失败，本次会话停用", throwable);
		}
	}
}
