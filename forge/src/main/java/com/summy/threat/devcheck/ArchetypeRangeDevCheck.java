package com.summy.threat.devcheck;

import com.summy.threat.SummyThreat;
import com.summy.threat.ability.AbilityEffects;
import com.summy.threat.scaling.MonsterScaler;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Dev-only：范围边界与云团多目标。
 *
 * <p>只通过 {@code -Prangecheck} 启用；验证烈焰 10 格、剧毒 3 格、震荡 14 格边界，
 * 以及吸血/磁力三目标各自一团云。
 */
@Mod.EventBusSubscriber(modid = SummyThreat.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE,
		value = Dist.CLIENT)
public final class ArchetypeRangeDevCheck {
	private static final boolean ENABLED = Boolean.getBoolean("summy_threat.rangecheck");
	private static final List<String> FAILURES = new ArrayList<>();
	private static int tick;
	private static int checks;
	private static boolean finished;
	private static ServerPlayer player;
	private static Zombie owner;
	private static Zombie ally1;
	private static Zombie ally2;
	private static boolean vampireSlowSeen;
	private static float lastVampireHealth = -1.0F;
	private static float maxVampireHealStep;

	private ArchetypeRangeDevCheck() {
	}

	@SubscribeEvent
	public static void onServerTick(TickEvent.ServerTickEvent event) {
		if (!ENABLED || finished || event.phase != TickEvent.Phase.END
				|| event.getServer().getPlayerList().getPlayers().isEmpty()) {
			return;
		}
		player = event.getServer().getPlayerList().getPlayers().get(0);
		tick++;
		try {
			observeVampireSlow();
			observeVampireHeal();
			switch (tick) {
				case 20 -> prepare();
				case 40 -> flameNear();
				case 100 -> checkPlayerHit("烈焰 9.5 格命中", true);
				case 120 -> flameFar();
				case 180 -> checkPlayerHit("烈焰 10.5 格不命中", false);
				case 200 -> poisonNear();
				case 260 -> checkPlayerHit("剧毒 2.5 格命中", true);
				case 280 -> poisonFar();
				case 340 -> checkPlayerHit("剧毒 3.5 格不命中", false);
				case 360 -> shockNear();
				case 480 -> checkPlayerHit("震荡 13 格命中", true);
				case 500 -> shockFar();
				case 620 -> checkPlayerHit("震荡 14.5 格不命中", false);
				case 640 -> cloudCase("vampire");
				case 760 -> checkCloudCase("吸血");
				case 780 -> cloudCase("magnet");
				case 900 -> checkCloudCase("磁力");
				case 920 -> checkTargetClassification();
				case 960 -> checkUniqueTargets();
				case 1000 -> checkRandomGroundPoints();
				case 1020 -> finish("完成");
				default -> {
				}
			}
		} catch (Throwable throwable) {
			FAILURES.add("步骤 " + tick + " 异常：" + throwable);
			log("步骤 " + tick + " 异常：" + throwable);
			throwable.printStackTrace();
			finish("异常停止");
		}
	}

	private static void prepare() {
		ServerLevel level = player.serverLevel();
		for (int x = 295; x <= 320; x++) {
			for (int z = -5; z <= 5; z++) {
				level.setBlockAndUpdate(new BlockPos(x, 99, z), Blocks.STONE.defaultBlockState());
			}
		}
		player.setGameMode(GameType.SURVIVAL);
		clearPlayer(player);
		player.removeAllEffects();
		player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0D);
		player.setHealth(1000.0F);
		run("gamerule naturalRegeneration false");
		run("gamerule doMobSpawning false");
		run("kill @e[type=!minecraft:player]");
		run("summythreat tier full");
		run("summythreat tl off");
		log("世界准备完成");
	}

	private static void flameNear() {
		beginAt(300.0D, 309.5D);
		owner = spawnEximus("flame", 300.0D);
		owner.setTarget(player);
	}

	private static void flameFar() {
		beginAt(300.0D, 310.5D);
		owner = spawnEximus("flame", 300.0D);
		owner.setTarget(player);
	}

	private static void poisonNear() {
		beginAt(300.0D, 302.5D);
		owner = spawnEximus("poison", 300.0D);
		owner.setTarget(player);
	}

	private static void poisonFar() {
		beginAt(300.0D, 303.5D);
		owner = spawnEximus("poison", 300.0D);
		owner.setTarget(player);
	}

	private static void shockNear() {
		beginAt(300.0D, 313.0D);
		owner = spawnEximus("shock", 300.0D);
		owner.setTarget(player);
	}

	private static void shockFar() {
		// 起手索敌上限为 14 格；14.5 格必须保持不施法。
		beginAt(300.0D, 314.5D);
		owner = spawnEximus("shock", 300.0D);
		owner.setTarget(player);
	}

	private static void cloudCase(String archetype) {
		beginAt(304.0D, 300.0D);
		if ("vampire".equals(archetype)) {
			vampireSlowSeen = false;
		}
		owner = spawnEximus(archetype, 304.0D);
		owner.setTarget(player);
		if ("vampire".equals(archetype)) {
			owner.setHealth(owner.getMaxHealth() * 0.5F);
			lastVampireHealth = owner.getHealth();
			maxVampireHealStep = 0.0F;
		}
		ally1 = spawnAlly(301.0D, owner);
		ally2 = spawnAlly(302.0D, owner);
	}

	private static void checkPlayerHit(String label, boolean shouldHit) {
		float loss = 1000.0F - player.getHealth();
		check(label, shouldHit ? loss > 0.1F : loss <= 0.01F, "player loss=%.1f".formatted(loss));
		endCase();
	}

	private static void checkCloudCase(String label) {
		float p = 1000.0F - player.getHealth();
		float a1 = 1000.0F - ally1.getHealth();
		float a2 = 1000.0F - ally2.getHealth();
		check(label + "：玩家获得一团云", p > 0.1F, "player loss=%.1f".formatted(p));
		check(label + "：目标 1 获得一团云", a1 > 0.1F, "ally1 loss=%.1f".formatted(a1));
		check(label + "：目标 2 获得一团云", a2 > 0.1F, "ally2 loss=%.1f".formatted(a2));
		if ("吸血".equals(label)) {
			check("吸血：命中附加等价缓慢 I（−15% 移速）", vampireSlowSeen,
					"seen=" + vampireSlowSeen);
			float expectedStep = owner.getMaxHealth() * 0.15F;
			check("吸血：3 个不同目标同 tick 叠加回复 15% 最大生命",
					Math.abs(maxVampireHealStep - expectedStep) < 2.0F,
					"maxStep=%.1f expected=%.1f".formatted(maxVampireHealStep, expectedStep));
		}
		endCase();
	}

	private static void checkTargetClassification() {
		run("kill @e[type=!minecraft:player]");
		owner = spawnEximus("guardian", 300.0D);
		Zombie revenge = spawnAlly(301.0D, owner);
		Zombie unrelated = spawnAlly(302.0D, null);
		Zombie betrayal = spawnAlly(303.0D, owner);
		betrayal.getPersistentData().putLong("summy_reliquary:betrayal_target",
				player.serverLevel().getGameTime() + 200L);

		Wolf wolf = EntityType.WOLF.create(player.serverLevel());
		if (wolf == null) {
			check("目标分类：生成驯服狼", false, "create 返回 null");
			return;
		}
		wolf.moveTo(304.0D, 100.0D, 0.0D, 0.0F, 0.0F);
		wolf.setNoAi(true);
		wolf.setPersistenceRequired();
		wolf.setTame(true);
		wolf.setOwnerUUID(player.getUUID());
		player.serverLevel().addFreshEntity(wolf);

		Zombie team = spawnAlly(305.0D, null);
		run("team add rangetest");
		run("team join rangetest " + player.getScoreboardName());
		run("team join rangetest " + team.getStringUUID());

		checkReflect("攻击目标：生存玩家", "isOffensiveTarget", true, owner, player);
		checkReflect("攻击目标：反击者是有效目标", "isOffensiveTarget", true, owner, revenge);
		checkReflect("攻击目标：无关怪物不是有效目标", "isOffensiveTarget", false, owner, unrelated);
		checkReflect("攻击目标：背叛靶子是有效目标", "isOffensiveTarget", true, owner, betrayal);
		owner.setTarget(unrelated);
		checkReflect("攻击目标：当前仇恨对象有效", "isOffensiveTarget", true, owner, unrelated);
		owner.setTarget(null);

		player.setGameMode(GameType.CREATIVE);
		checkReflect("攻击目标：创造玩家被排除", "isOffensiveTarget", false, owner, player);
		player.setGameMode(GameType.SPECTATOR);
		checkReflect("攻击目标：旁观玩家被排除", "isOffensiveTarget", false, owner, player);
		player.setGameMode(GameType.SURVIVAL);

		checkReflect("玩家友军：玩家本人", "isPlayerAlly", true, player);
		checkReflect("玩家友军：玩家驯服狼", "isPlayerAlly", true, wolf);
		checkReflect("玩家友军：同队僵尸", "isPlayerAlly", true, team);
		checkReflect("玩家友军：普通无关僵尸", "isPlayerAlly", false, unrelated);

		checkReflect("支援目标：普通敌对怪物", "isSupportTarget", true, owner, unrelated);
		checkReflect("支援目标：玩家不获得冰盾/守护", "isSupportTarget", false, owner, player);
		checkReflect("支援目标：背叛靶子被排除", "isSupportTarget", false, owner, betrayal);
		owner.setTarget(unrelated);
		checkReflect("支援目标：当前仇恨对象被排除", "isSupportTarget", false, owner, unrelated);
		owner.setTarget(null);

		player.teleportTo(player.serverLevel(), 309.5D, 100.0D, 0.0D, 0.0F, 0.0F);
		owner.setTarget(player);
		checkReflect("索敌：烈焰 9.5 格可起手", "currentTargetInRange", true, owner, 10.0D);
		player.teleportTo(player.serverLevel(), 310.5D, 100.0D, 0.0D, 0.0F, 0.0F);
		checkReflect("索敌：烈焰 10.5 格不可起手", "currentTargetInRange", false, owner, 10.0D);
		player.teleportTo(player.serverLevel(), 313.5D, 100.0D, 0.0D, 0.0F, 0.0F);
		checkReflect("索敌：震荡 13.5 格可起手", "currentTargetInRange", true, owner, 14.0D);
		player.teleportTo(player.serverLevel(), 314.5D, 100.0D, 0.0D, 0.0F, 0.0F);
		checkReflect("索敌：震荡 14.5 格不可起手", "currentTargetInRange", false, owner, 14.0D);
		owner.setTarget(null);
		player.teleportTo(player.serverLevel(), 300.0D, 100.0D, 0.0D, 0.0F, 0.0F);
		run("kill @e[type=!minecraft:player]");
	}

	private static void checkUniqueTargets() {
		run("kill @e[type=!minecraft:player]");
		owner = spawnEximus("storm", 300.0D);
		Zombie near = spawnAlly(301.0D, owner);
		Zombie middle = spawnAlly(302.0D, owner);
		Zombie far = spawnAlly(303.0D, owner);
		List<LivingEntity> picked = selectUniqueTargets(3);
		check("唯一索敌：最多 3 个槽位", picked.size() == 3, "size=" + picked.size());
		check("唯一索敌：玩家优先", !picked.isEmpty() && picked.get(0) == player,
				"first=" + (picked.isEmpty() ? "无" : picked.get(0).getUUID()));
		Set<java.util.UUID> ids = new HashSet<>();
		picked.forEach(entity -> ids.add(entity.getUUID()));
		check("唯一索敌：同一目标不重复", ids.size() == picked.size(),
				"picked=" + picked.size() + " unique=" + ids.size());
		List<LivingEntity> two = selectUniqueTargets(2);
		check("唯一索敌：电涌槽位上限 2", two.size() == 2, "size=" + two.size());

		far.discard();
		middle.discard();
		List<LivingEntity> one = selectUniqueTargets(3);
		check("唯一索敌：玩家 + 1 个有效非玩家目标占 2 槽", one.size() == 2,
				"size=" + one.size() + " near=" + near.getUUID());
		run("kill @e[type=!minecraft:player]");
	}

	private static void checkRandomGroundPoints() {
		run("kill @e[type=!minecraft:player]");
		owner = spawnEximus("vampire", 300.0D);
		double min = Double.MAX_VALUE;
		double max = -1.0D;
		Set<String> distinct = new HashSet<>();
		int[] quadrants = new int[4];
		for (int i = 0; i < 1000; i++) {
			Object value = invoke("groundPoint", new Class<?>[]{
					ServerLevel.class, LivingEntity.class, double.class
			}, player.serverLevel(), owner, 12.0D);
			if (!(value instanceof net.minecraft.world.phys.Vec3 point)) {
				check("随机补位：坐标类型", false, String.valueOf(value));
				return;
			}
			double dx = point.x - owner.getX();
			double dz = point.z - owner.getZ();
			double distance = Math.sqrt(dx * dx + dz * dz);
			min = Math.min(min, distance);
			max = Math.max(max, distance);
			distinct.add("%.4f,%.4f".formatted(point.x, point.z));
			int quadrant = (dx >= 0.0D ? 1 : 0) + (dz >= 0.0D ? 2 : 0);
			quadrants[quadrant]++;
		}
		// 高度图会把连续随机点吸附到方块中心，单轴最多偏移约 0.71 格。
		check("随机补位 1000 次：距离保持在 2~12 格吸附容差内",
				min >= 1.3D && max <= 12.8D,
				"min=%.3f max=%.3f".formatted(min, max));
		check("随机补位 1000 次：至少覆盖 150 个不同方块中心", distinct.size() >= 150,
				"distinct=" + distinct.size());
		boolean allQuadrants = true;
		for (int count : quadrants) {
			allQuadrants &= count >= 150;
		}
		check("随机补位分布覆盖四个象限", allQuadrants,
				"quadrants=%d/%d/%d/%d".formatted(
						quadrants[0], quadrants[1], quadrants[2], quadrants[3]));
		run("kill @e[type=!minecraft:player]");
	}

	private static void checkReflect(String label, String method, boolean expected, Object... args) {
		Object value = invoke(method, inferTypes(args), args);
		check(label, value instanceof Boolean actual && actual == expected,
				"expected=" + expected + " actual=" + value);
	}

	private static void observeVampireSlow() {
		if (player == null) {
			return;
		}
		AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed != null && speed.getModifiers().stream()
				.anyMatch(modifier -> "summy_threat:vampire_slow_speed".equals(modifier.getName()))) {
			vampireSlowSeen = true;
		}
	}

	private static void observeVampireHeal() {
		if (owner == null || !"vampire".equals(
				owner.getPersistentData().getString(MonsterScaler.TAG_ARCHETYPE))) {
			return;
		}
		float now = owner.getHealth();
		if (lastVampireHealth >= 0.0F) {
			maxVampireHealStep = Math.max(maxVampireHealStep, now - lastVampireHealth);
		}
		lastVampireHealth = now;
	}

	@SuppressWarnings("unchecked")
	private static List<LivingEntity> selectUniqueTargets(int max) {
		Object value = invoke("selectUniqueTargets", new Class<?>[]{
				LivingEntity.class, double.class, int.class
		}, owner, 14.0D, max);
		return value instanceof List<?> list ? (List<LivingEntity>) list : List.of();
	}

	private static Object invoke(String name, Class<?>[] types, Object... args) {
		try {
			Method method = com.summy.threat.eximus.EximusArchetypeEffects.class
					.getDeclaredMethod(name, types);
			method.setAccessible(true);
			return method.invoke(null, args);
		} catch (ReflectiveOperationException exception) {
			throw new IllegalStateException("调用 " + name + " 失败", exception);
		}
	}

	private static Class<?>[] inferTypes(Object... args) {
		Class<?>[] types = new Class<?>[args.length];
		for (int i = 0; i < args.length; i++) {
			if (args[i] instanceof LivingEntity) {
				types[i] = LivingEntity.class;
			} else if (args[i] instanceof Double) {
				types[i] = double.class;
			} else if (args[i] instanceof Integer) {
				types[i] = int.class;
			} else if (args[i] instanceof Boolean) {
				types[i] = boolean.class;
			} else {
				types[i] = args[i].getClass();
			}
		}
		return types;
	}

	private static void beginAt(double ownerX, double playerX) {
		run("kill @e[type=!minecraft:player]");
		player.teleportTo(player.serverLevel(), playerX, 100.0D, 0.0D, 0.0F, 0.0F);
		player.setHealth(1000.0F);
		player.removeAllEffects();
		run("summythreat tl off");
	}

	private static Zombie spawnEximus(String archetype, double x) {
		ServerLevel level = player.serverLevel();
		Zombie eximus = EntityType.ZOMBIE.create(level);
		if (eximus == null) {
			throw new IllegalStateException("无法生成 " + archetype);
		}
		eximus.moveTo(x, 100.0D, 0.0D, 0.0F, 0.0F);
		eximus.getPersistentData().putBoolean(MonsterScaler.TAG_EXIMUS, true);
		eximus.getPersistentData().putString(MonsterScaler.TAG_ARCHETYPE, archetype);
		level.addFreshEntity(eximus);
		MonsterScaler.scaleNow(eximus, 20, MonsterScaler.Kind.EXIMUS);
		AbilityEffects.setAbilities(eximus, 20, "");
		eximus.setNoAi(true);
		eximus.setPersistenceRequired();
		eximus.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.0D);
		eximus.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(0.0D);
		return eximus;
	}

	private static Zombie spawnAlly(double x, LivingEntity target) {
		ServerLevel level = player.serverLevel();
		Zombie zombie = EntityType.ZOMBIE.create(level);
		if (zombie == null) {
			throw new IllegalStateException("无法生成云团目标");
		}
		zombie.moveTo(x, 100.0D, 0.0D, 0.0F, 0.0F);
		zombie.setNoAi(true);
		zombie.setPersistenceRequired();
		zombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0D);
		zombie.getAttribute(Attributes.ARMOR).setBaseValue(0.0D);
		zombie.setHealth(1000.0F);
		level.addFreshEntity(zombie);
		zombie.setTarget(target);
		return zombie;
	}

	private static void endCase() {
		run("kill @e[type=!minecraft:player]");
	}

	private static void check(String label, boolean ok, String detail) {
		checks++;
		if (!ok) {
			FAILURES.add(label);
		}
		log((ok ? "OK  " : "FAIL") + " " + label + "｜" + detail);
	}

	private static void clearPlayer(ServerPlayer player) {
		player.getInventory().clearContent();
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			player.setItemSlot(slot, ItemStack.EMPTY);
		}
		CuriosApi.getCuriosInventory(player).ifPresent(handler -> {
			for (ICurioStacksHandler stacks : handler.getCurios().values()) {
				clear(stacks.getStacks());
				clear(stacks.getCosmeticStacks());
			}
		});
	}

	private static void clear(IDynamicStackHandler handler) {
		for (int i = 0; i < handler.getSlots(); i++) {
			handler.setStackInSlot(i, ItemStack.EMPTY);
		}
	}

	private static void run(String command) {
		player.server.getCommands().performPrefixedCommand(player.createCommandSourceStack(), command);
	}

	private static void finish(String reason) {
		if (finished) {
			return;
		}
		finished = true;
		log("===== " + reason + "：检查 " + checks + " 项，失败 " + FAILURES.size() + " 项 =====");
		for (String failure : FAILURES) {
			log("FAILED｜" + failure);
		}
		Minecraft.getInstance().execute(() -> Minecraft.getInstance().stop());
	}

	private static void log(String message) {
		SummyThreat.LOGGER.info("[RANGECHECK] " + message);
	}
}
