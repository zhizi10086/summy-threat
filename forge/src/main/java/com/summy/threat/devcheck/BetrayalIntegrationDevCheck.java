package com.summy.threat.devcheck;

import com.summy.threat.Config;
import com.summy.threat.SummyThreat;
import com.summy.threat.eximus.EximusShield;
import com.summy.threat.leader.LegendaryResistance;
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
	private static final String RESISTANCE_TAG = "summy_threat:legendary_resistance";
	private static final String IMMUNE_TAG = "summy_threat:legendary_resistance_betrayal_immune";
	private static int assertions;

	private BetrayalIntegrationDevCheck() {
	}

	static List<String> run(ServerPlayer player) {
		assertions = 0;
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
			invokeVoid(BETRAYAL, "clear");
			invokeVoid(SLOT_SIZING, "syncNow", new Class<?>[]{ServerPlayer.class}, player);
			if (!CuriosApi.getCuriosInventory(player).isPresent()) {
				// 快速单人存档重新载入玩家时，Forge 可能保留一个已失效的 caps 容器；
				// 这里只恢复本地自检玩家，不改变生产逻辑。
				player.reviveCaps();
			}
			check(failures, "背叛联调：Curios capability 可用",
					CuriosApi.getCuriosInventory(player).isPresent(),
					"entitySlots=" + CuriosApi.getEntitySlots(player).keySet());
			var inventory = CuriosApi.getCuriosInventory(player).orElse(null);
			if (inventory == null) {
				return failures;
			}
			var stacks = inventory.getCurios().get("blessing");
			if (stacks != null) {
				originalCurio = stacks.getStacks().getStackInSlot(0).copy();
			}
			invokeVoid(FLAGS, "setDemon", new Class<?>[]{ServerPlayer.class, boolean.class}, player, true);
			inventory.setEquippedCurio("blessing", 0, new ItemStack(betrayal));
			check(failures, "背叛饰品已实际装备",
					inventory.findFirstCurio(betrayal).isPresent(),
					"equipped=" + inventory.findFirstCurio(betrayal).isPresent());
			check(failures, "背叛佩戴判定生效",
					invokeBoolean(BETRAYAL, "wears", new Class<?>[]{LivingEntity.class}, player),
					"demon=" + invokeBoolean(FLAGS, "isDemon",
							new Class<?>[]{LivingEntity.class}, player));

			checkEximusShieldFilter(failures, player, mobs);
			checkLegendaryTagsAndBaseEligibility(failures, player, mobs);
			checkTargetBecomesImmune(failures, player, mobs);
			checkAttackerBecomesImmune(failures, player, mobs);
			checkAllImmuneAndRequalification(failures, player, mobs);
			checkStTargetClassification(failures, player, mobs);
		} catch (Exception exception) {
			failures.add("背叛联调执行失败：" + exception);
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
			SummyThreat.LOGGER.info("[BETRAYAL] RESULT assertions={} failures={}",
					assertions, failures.size());
		}
		return failures;
	}

	private static void checkEximusShieldFilter(List<String> failures, ServerPlayer player,
			List<Mob> mobs) throws ReflectiveOperationException {
		resetScenario(player, mobs);
		ServerLevel level = player.serverLevel();
		Zombie shielded = spawn(level, player, 1.0D, 0.0D);
		Zombie first = spawn(level, player, 2.0D, 0.0D);
		Zombie second = spawn(level, player, 3.0D, 0.0D);
		mobs.add(shielded);
		mobs.add(first);
		mobs.add(second);
		shielded.getPersistentData().putDouble(EximusShield.TAG_SHIELD, 10.0D);
		shielded.getPersistentData().putDouble(EximusShield.TAG_SHIELD_MAX, 10.0D);

		tickBetrayal(player);
		check(failures, "背叛联调：卓越护盾候选不写入目标标签",
				!hasTargetTag(shielded), "target=" + targetForTest(player));
		check(failures, "背叛联调：跳过护盾后选择最近普通候选",
				targetForTest(player) == first, "target=" + targetForTest(player));
		check(failures, "背叛联调：普通攻击者被强制转火",
				second.getTarget() == first, "target=" + second.getTarget());
	}

	private static void checkLegendaryTagsAndBaseEligibility(List<String> failures,
			ServerPlayer player, List<Mob> mobs) throws ReflectiveOperationException {
		resetScenario(player, mobs);
		ServerLevel level = player.serverLevel();
		Zombie leader = spawnRaw(level, player, 1.0D, 0.0D);
		mobs.add(leader);
		check(failures, "背叛联调：ST 可显式缩放区域首领",
				MonsterScaler.scaleNow(leader, 20, MonsterScaler.Kind.LEADER, false),
				"kind=" + MonsterScaler.kindOf(leader));
		check(failures, "背叛联调：ST 写入基础传奇抗性标签",
				leader.getPersistentData().getBoolean(RESISTANCE_TAG), "missing");
		check(failures, "背叛联调：ST 写入背叛免疫标签",
				leader.getPersistentData().getBoolean(IMMUNE_TAG), "missing");
		check(failures, "背叛联调：基础传奇抗性不是卓越护盾",
				EximusShield.current(leader) == 0.0D && EximusShield.max(leader) == 0.0D,
				"shield=" + EximusShield.current(leader) + "/" + EximusShield.max(leader));

		Zombie first = spawn(level, player, 2.0D, 0.0D);
		Zombie second = spawn(level, player, 3.0D, 0.0D);
		mobs.add(first);
		mobs.add(second);
		tickBetrayal(player);
		check(failures, "背叛联调：真实传奇首领被 SR 过滤",
				targetForTest(player) == first && !hasTargetTag(leader),
				"target=" + targetForTest(player));

		boolean originalBetrayalImmune = Config.LEGENDARY_BETRAYAL_IMMUNE.get();
		try {
			Config.LEGENDARY_BETRAYAL_IMMUNE.set(false);
			LegendaryResistance.refresh(leader);
			check(failures, "背叛联调：关闭配置只移除第二标签",
					leader.getPersistentData().getBoolean(RESISTANCE_TAG)
							&& !leader.getPersistentData().getBoolean(IMMUNE_TAG),
					"base=" + leader.getPersistentData().getBoolean(RESISTANCE_TAG)
							+ " immune=" + leader.getPersistentData().getBoolean(IMMUNE_TAG));
			resetBetrayalState(player);
			tickBetrayal(player);
			check(failures, "背叛联调：仅基础标签仍可成为背叛靶子",
					targetForTest(player) == leader, "target=" + targetForTest(player));
		} finally {
			Config.LEGENDARY_BETRAYAL_IMMUNE.set(originalBetrayalImmune);
			LegendaryResistance.refresh(leader);
		}
	}

	private static void checkTargetBecomesImmune(List<String> failures, ServerPlayer player,
			List<Mob> mobs) throws ReflectiveOperationException {
		resetScenario(player, mobs);
		ServerLevel level = player.serverLevel();
		Zombie target = spawn(level, player, 1.0D, 0.0D);
		Zombie attackerA = spawn(level, player, 2.0D, 0.0D);
		Zombie attackerB = spawn(level, player, 3.0D, 0.0D);
		Zombie previousA = spawn(level, player, 1.0D, 1.0D);
		Zombie previousB = spawn(level, player, 2.0D, 1.0D);
		mobs.add(target);
		mobs.add(attackerA);
		mobs.add(attackerB);
		mobs.add(previousA);
		mobs.add(previousB);
		attackerA.setTarget(previousA);
		attackerB.setTarget(previousB);

		tickBetrayal(player);
		check(failures, "背叛联调：目标中途失效场景已建立",
				targetForTest(player) == target && attackerA.getTarget() == target
						&& attackerB.getTarget() == target,
				"target=" + targetForTest(player));

		// 模拟其它模组在背叛期间给 B 换了新目标；结束时不得覆盖该新目标。
		attackerB.setTarget(previousB);
		target.getPersistentData().putBoolean(IMMUNE_TAG, true);
		long before = level.getGameTime();
		tickBetrayal(player);
		long cooldown = invokeLong(FLAGS, "betrayalCooldownUntil",
				new Class<?>[]{LivingEntity.class}, player);
		check(failures, "背叛联调：靶子中途免疫立即结束",
				targetForTest(player) == null, "target=" + targetForTest(player));
		check(failures, "背叛联调：结束背叛清除目标标签",
				!target.getPersistentData().contains(TARGET_TAG),
				"tag=" + target.getPersistentData().getLong(TARGET_TAG));
		check(failures, "背叛联调：仍指向靶子的攻击者恢复原目标",
				attackerA.getTarget() == previousA, "target=" + attackerA.getTarget());
		check(failures, "背叛联调：不覆盖其它模组写入的新目标",
				attackerB.getTarget() == previousB, "target=" + attackerB.getTarget());
		check(failures, "背叛联调：中途结束进入现有 5 秒冷却",
				cooldown >= before + 95L && cooldown <= before + 105L,
				"cooldown=" + cooldown + " now=" + before);
	}

	private static void checkAttackerBecomesImmune(List<String> failures, ServerPlayer player,
			List<Mob> mobs) throws ReflectiveOperationException {
		resetScenario(player, mobs);
		ServerLevel level = player.serverLevel();
		Zombie target = spawn(level, player, 1.0D, 0.0D);
		Zombie immuneAttacker = spawn(level, player, 2.0D, 0.0D);
		Zombie normalAttacker = spawn(level, player, 3.0D, 0.0D);
		Zombie previous = spawn(level, player, 1.0D, 1.0D);
		mobs.add(target);
		mobs.add(immuneAttacker);
		mobs.add(normalAttacker);
		mobs.add(previous);
		immuneAttacker.setTarget(previous);
		normalAttacker.setTarget(previous);

		tickBetrayal(player);
		check(failures, "背叛联调：免疫攻击者中途场景已建立",
				immuneAttacker.getTarget() == target && normalAttacker.getTarget() == target,
				"immuneTarget=" + immuneAttacker.getTarget());
		long triggerBefore = invokeLong(BETRAYAL, "triggerCount", new Class<?>[0]);
		immuneAttacker.getPersistentData().putBoolean(IMMUNE_TAG, true);
		tickBetrayal(player);
		check(failures, "背叛联调：免疫攻击者恢复原目标",
				immuneAttacker.getTarget() == previous, "target=" + immuneAttacker.getTarget());
		check(failures, "背叛联调：单个攻击者免疫不中断整轮背叛",
				targetForTest(player) == target && normalAttacker.getTarget() == target,
				"active=" + targetForTest(player) + " normal=" + normalAttacker.getTarget());
		check(failures, "背叛联调：攻击者免疫不重复触发",
				invokeLong(BETRAYAL, "triggerCount", new Class<?>[0]) == triggerBefore,
				"before=" + triggerBefore);

		immuneAttacker.getPersistentData().remove(IMMUNE_TAG);
		tickBetrayal(player);
		check(failures, "背叛联调：移除免疫后攻击者恢复强制转火",
				immuneAttacker.getTarget() == target, "target=" + immuneAttacker.getTarget());
	}

	private static void checkAllImmuneAndRequalification(List<String> failures,
			ServerPlayer player, List<Mob> mobs) throws ReflectiveOperationException {
		resetScenario(player, mobs);
		ServerLevel level = player.serverLevel();
		Zombie first = spawn(level, player, 1.0D, 0.0D);
		Zombie second = spawn(level, player, 2.0D, 0.0D);
		mobs.add(first);
		mobs.add(second);
		first.getPersistentData().putBoolean(IMMUNE_TAG, true);
		second.getPersistentData().putBoolean(IMMUNE_TAG, true);

		tickBetrayal(player);
		check(failures, "背叛联调：全部候选免疫时不启动",
				targetForTest(player) == null
						&& invokeLong(BETRAYAL, "triggerCount", new Class<?>[0]) == 0L,
				"target=" + targetForTest(player));
		check(failures, "背叛联调：候选不足不进入冷却",
				invokeLong(FLAGS, "betrayalCooldownUntil",
						new Class<?>[]{LivingEntity.class}, player) == 0L,
				"cooldown=" + invokeLong(FLAGS, "betrayalCooldownUntil",
						new Class<?>[]{LivingEntity.class}, player));

		first.getPersistentData().remove(IMMUNE_TAG);
		second.getPersistentData().remove(IMMUNE_TAG);
		tickBetrayal(player);
		check(failures, "背叛联调：移除免疫后下一轮恢复候选资格",
				targetForTest(player) == first
						&& invokeLong(BETRAYAL, "triggerCount", new Class<?>[0]) == 1L,
				"target=" + targetForTest(player));
	}

	private static void checkStTargetClassification(List<String> failures, ServerPlayer player,
			List<Mob> mobs) throws ReflectiveOperationException {
		resetScenario(player, mobs);
		ServerLevel level = player.serverLevel();
		Zombie betrayalTarget = spawn(level, player, 1.0D, 0.0D);
		Zombie unrelated = spawn(level, player, 2.0D, 0.0D);
		mobs.add(betrayalTarget);
		mobs.add(unrelated);
		betrayalTarget.getPersistentData().putLong(TARGET_TAG,
				level.getGameTime() + 200L);

		Mob owner = spawnRaw(level, player, 3.0D, 0.0D);
		mobs.add(owner);
		check(failures, "背叛联调：ST 攻击目标包含背叛者",
				invokeBoolean("com.summy.threat.eximus.EximusArchetypeEffects",
						"isOffensiveTarget",
						new Class<?>[]{LivingEntity.class, LivingEntity.class},
						owner, betrayalTarget),
				"offensive=false");
		check(failures, "背叛联调：ST 支援目标排除背叛者",
				!invokeBoolean("com.summy.threat.eximus.EximusArchetypeEffects",
						"isSupportTarget",
						new Class<?>[]{LivingEntity.class, LivingEntity.class},
						owner, betrayalTarget),
				"support=true");
		check(failures, "背叛联调：ST 支援目标仍包含普通怪物",
				invokeBoolean("com.summy.threat.eximus.EximusArchetypeEffects",
						"isSupportTarget",
						new Class<?>[]{LivingEntity.class, LivingEntity.class},
						owner, unrelated),
				"support=false");
	}

	private static void resetScenario(ServerPlayer player, List<Mob> mobs)
			throws ReflectiveOperationException {
		invokeVoid(BETRAYAL, "clear");
		for (Mob mob : mobs) {
			mob.discard();
		}
		mobs.clear();
		resetBetrayalState(player);
	}

	private static void resetBetrayalState(ServerPlayer player) throws ReflectiveOperationException {
		invokeVoid(BETRAYAL, "clear");
		invokeVoid(BETRAYAL, "setChanceForTest", new Class<?>[]{Double.class}, 100.0D);
		invokeVoid(FLAGS, "setBetrayalCooldownUntil",
				new Class<?>[]{ServerPlayer.class, long.class}, player, 0L);
	}

	private static void tickBetrayal(ServerPlayer player) throws ReflectiveOperationException {
		invokeVoid(BETRAYAL, "tickPlayer", new Class<?>[]{ServerPlayer.class}, player);
	}

	private static Mob targetForTest(ServerPlayer player) throws ReflectiveOperationException {
		return (Mob) invoke(BETRAYAL, "targetForTest",
				new Class<?>[]{ServerPlayer.class}, null, player);
	}

	private static boolean hasTargetTag(Mob mob) {
		return mob.getPersistentData().contains(TARGET_TAG);
	}

	private static Zombie spawn(ServerLevel level, ServerPlayer player, double dx, double dz) {
		Zombie zombie = spawnRaw(level, player, dx, dz);
		MonsterScaler.scaleNow(zombie, 20, MonsterScaler.Kind.NORMAL, false);
		return zombie;
	}

	private static Zombie spawnRaw(ServerLevel level, ServerPlayer player, double dx, double dz) {
		Zombie zombie = EntityType.ZOMBIE.create(level);
		if (zombie == null) {
			throw new IllegalStateException("无法创建背叛联调僵尸");
		}
		zombie.moveTo(player.getX() + dx, player.getY(), player.getZ() + dz, 0.0F, 0.0F);
		zombie.setNoAi(true);
		zombie.setPersistenceRequired();
		level.addFreshEntity(zombie);
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
		assertions++;
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
