package com.summy.threat.devcheck;

import com.summy.threat.eximus.EximusShield;
import com.summy.threat.scaling.MonsterScaler;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import top.theillusivec4.curios.api.CuriosApi;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/** ST + SR 2.0 背叛联合自检；仅由 -Pbetrayalcheck 启用。 */
final class BetrayalIntegrationDevCheck {
	private static final String FLAGS = "com.summy.reliquary.effect.PlayerFlags";
	private static final String SLOT_SIZING = "com.summy.reliquary.effect.SlotSizing";
	private static final String BETRAYAL = "com.summy.reliquary.effect.Betrayal";
	private static final String TARGET_TAG = "summy_reliquary:betrayal_target";

	private BetrayalIntegrationDevCheck() {
	}

	static List<String> run(ServerPlayer player) {
		List<String> failures = new ArrayList<>();
		var container = ModList.get().getModContainerById("summy_reliquary").orElse(null);
		String version = container == null ? "" : container.getModInfo().getVersion().toString();
		check(failures, "背叛联调加载 SR 2.0", version.startsWith("2.0."), "version=" + version);
		Item betrayal = ForgeRegistries.ITEMS.getValue(new ResourceLocation("summy-reliquary", "betrayal"));
		check(failures, "背叛联调找到 betrayal 物品", betrayal != null, "item=" + betrayal);
		if (!version.startsWith("2.0.") || betrayal == null) {
			return failures;
		}

		boolean originalDemon = false;
		long originalCooldown = 0L;
		ItemStack originalCurio = ItemStack.EMPTY;
		List<Mob> mobs = new ArrayList<>();
		try {
			originalDemon = invokeBoolean(FLAGS, "isDemon", new Class<?>[]{LivingEntity.class}, player);
			originalCooldown = invokeLong(FLAGS, "betrayalCooldownUntil",
					new Class<?>[]{LivingEntity.class}, player);
			var inventory = CuriosApi.getCuriosInventory(player).orElse(null);
			if (inventory != null) {
				var stacks = inventory.getCurios().get("blessing");
				if (stacks != null) {
					originalCurio = stacks.getStacks().getStackInSlot(0).copy();
				}
			}

			invokeVoid(BETRAYAL, "clear");
			invokeVoid(FLAGS, "setDemon", new Class<?>[]{ServerPlayer.class, boolean.class}, player, true);
			invokeVoid(SLOT_SIZING, "syncNow", new Class<?>[]{ServerPlayer.class}, player);
			if (inventory == null) {
				failures.add("背叛联调：Curios inventory 缺失");
				return failures;
			}
			inventory.setEquippedCurio("blessing", 0, new ItemStack(betrayal));
			check(failures, "背叛饰品已实际装备",
					inventory.findFirstCurio(betrayal).isPresent(),
					"equipped=" + inventory.findFirstCurio(betrayal).isPresent());

			invokeVoid(BETRAYAL, "setChanceForTest", new Class<?>[]{Double.class}, 100.0D);
			invokeVoid(FLAGS, "setBetrayalCooldownUntil",
					new Class<?>[]{ServerPlayer.class, long.class}, player, 0L);

			ServerLevel level = player.serverLevel();
			Zombie shielded = spawn(level, player, 1.0D);
			Zombie near = spawn(level, player, 3.0D);
			Zombie far = spawn(level, player, 5.0D);
			mobs.add(shielded);
			mobs.add(near);
			mobs.add(far);
			shielded.getPersistentData().putDouble(EximusShield.TAG_SHIELD, 10.0D);
			shielded.getPersistentData().putDouble(EximusShield.TAG_SHIELD_MAX, 10.0D);

			invokeVoid(BETRAYAL, "tickPlayer", new Class<?>[]{ServerPlayer.class}, player);
			long now = level.getGameTime();
			boolean nearTagged = near.getPersistentData().getLong(TARGET_TAG) > now;
			boolean farTagged = far.getPersistentData().getLong(TARGET_TAG) > now;
			boolean shieldedTagged = shielded.getPersistentData().getLong(TARGET_TAG) > now;
			check(failures, "背叛联调：SR 写入最近普通目标标签", nearTagged && !farTagged,
					"near=" + nearTagged + " far=" + farTagged);
			check(failures, "背叛联调：卓越护盾目标被 SR 跳过", !shieldedTagged,
					"shielded=" + shieldedTagged);
			check(failures, "背叛联调：普通攻击者转火背叛目标", far.getTarget() == near,
					"target=" + far.getTarget());

			Mob owner = EntityType.ZOMBIE.create(level);
			if (owner == null) {
				failures.add("背叛联调：无法创建 ST 目标判定 owner");
			} else {
				mobs.add(owner);
				check(failures, "背叛联调：ST 攻击目标包含背叛者",
						invokeBoolean("com.summy.threat.eximus.EximusArchetypeEffects",
								"isOffensiveTarget",
								new Class<?>[]{LivingEntity.class, LivingEntity.class}, owner, near),
						"offensive=false");
				check(failures, "背叛联调：ST 支援目标排除背叛者",
						!invokeBoolean("com.summy.threat.eximus.EximusArchetypeEffects",
								"isSupportTarget",
								new Class<?>[]{LivingEntity.class, LivingEntity.class}, owner, near),
						"support=true");
			}
		} catch (ReflectiveOperationException exception) {
			failures.add("背叛联调反射失败：" + exception);
		} finally {
			try {
				invokeVoid(BETRAYAL, "clear");
			} catch (ReflectiveOperationException ignored) {
			}
			try {
				invokeVoid(FLAGS, "setBetrayalCooldownUntil",
						new Class<?>[]{ServerPlayer.class, long.class}, player, originalCooldown);
				invokeVoid(FLAGS, "setDemon", new Class<?>[]{ServerPlayer.class, boolean.class},
						player, originalDemon);
				invokeVoid(SLOT_SIZING, "syncNow", new Class<?>[]{ServerPlayer.class}, player);
			} catch (ReflectiveOperationException ignored) {
			}
			ItemStack restoreCurio = originalCurio;
			CuriosApi.getCuriosInventory(player).ifPresent(inventory ->
					inventory.setEquippedCurio("blessing", 0, restoreCurio));
			for (Mob mob : mobs) {
				mob.discard();
			}
		}
		return failures;
	}

	private static Zombie spawn(ServerLevel level, ServerPlayer player, double dx) {
		Zombie zombie = EntityType.ZOMBIE.create(level);
		if (zombie == null) {
			throw new IllegalStateException("无法创建背叛联调僵尸");
		}
		zombie.moveTo(player.getX() + dx, player.getY(), player.getZ(), 0.0F, 0.0F);
		zombie.setNoAi(true);
		zombie.setPersistenceRequired();
		level.addFreshEntity(zombie);
		MonsterScaler.scaleNow(zombie, 20, MonsterScaler.Kind.NORMAL, false);
		return zombie;
	}

	private static void invokeVoid(String className, String methodName, Class<?>[] types, Object... args)
			throws ReflectiveOperationException {
		invoke(className, methodName, types, null, args);
	}

	private static void invokeVoid(String className, String methodName, Object... args)
			throws ReflectiveOperationException {
		invoke(className, methodName, new Class<?>[0], null, args);
	}

	private static boolean invokeBoolean(String className, String methodName, Class<?>[] types,
			Object... args) throws ReflectiveOperationException {
		return (Boolean) invoke(className, methodName, types, null, args);
	}

	private static long invokeLong(String className, String methodName, Class<?>[] types,
			Object... args) throws ReflectiveOperationException {
		return ((Number) invoke(className, methodName, types, null, args)).longValue();
	}

	private static Object invoke(String className, String methodName, Class<?>[] types,
			Object receiver, Object... args) throws ReflectiveOperationException {
		Class<?> type = Class.forName(className);
		Method method = type.getDeclaredMethod(methodName, types);
		method.setAccessible(true);
		try {
			return method.invoke(receiver, args);
		} catch (InvocationTargetException exception) {
			if (exception.getCause() instanceof ReflectiveOperationException reflective) {
				throw reflective;
			}
			throw new IllegalStateException(className + "#" + methodName + " 调用失败", exception.getCause());
		}
	}

	private static void check(List<String> failures, String label, boolean ok, String detail) {
		if (!ok) {
			failures.add(label + "（" + detail + "）");
		}
		if (ok) {
			System.out.println("[BETRAYAL] OK " + label);
		} else {
			System.out.println("[BETRAYAL] FAIL " + label + "：" + detail);
		}
	}
}
