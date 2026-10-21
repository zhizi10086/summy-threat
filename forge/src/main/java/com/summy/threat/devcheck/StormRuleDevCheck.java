package com.summy.threat.devcheck;

import com.summy.threat.SummyThreat;
import com.summy.threat.eximus.EximusArchetypeEffects;
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
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Scoreboard;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Dev-only：验证电涌落雷全单位、施法者排除、玩家友军 1 倍与普通单位 10 倍。
 *
 * <p>只通过 {@code -Pstormcheck} 启用；不参与正式构建。追踪/锁定观感仍由人工确认。
 */
@Mod.EventBusSubscriber(modid = SummyThreat.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE,
		value = Dist.CLIENT)
public final class StormRuleDevCheck {
	private static final boolean ENABLED = Boolean.getBoolean("summy_threat.stormcheck");
	private static final List<String> FAILURES = new ArrayList<>();
	private static int tick;
	private static int checks;
	private static boolean finished;
	private static ServerPlayer player;
	private static Zombie owner;
	private static Zombie normal;
	private static Zombie slotTarget;
	private static Zombie teamZombie;
	private static Wolf wolf;
	private static long observedCastStart = -1L;
	private static Vec3 trackingPoint;
	private static Vec3 frozenPoint;
	private static boolean movedPlayerOnce;
	private static boolean movedPlayerTwice;
	private static boolean killedSlotTarget;
	private static boolean movedDuringLock;
	private static boolean markerChecked;

	private StormRuleDevCheck() {
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
			observeStormStage();
			switch (tick) {
				case 20 -> prepare();
				case 40 -> spawnActors();
				case 60 -> checkFormula();
				case 300 -> checkStormResult();
				case 320 -> finish("完成");
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
		for (int x = 295; x <= 305; x++) {
			for (int z = -5; z <= 5; z++) {
				level.setBlockAndUpdate(new BlockPos(x, 99, z), Blocks.STONE.defaultBlockState());
			}
		}
		player.setGameMode(GameType.SURVIVAL);
		clearPlayer(player);
		player.teleportTo(level, 300.0D, 100.0D, 0.0D, 0.0F, 0.0F);
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			player.setItemSlot(slot, ItemStack.EMPTY);
		}
		player.removeAllEffects();
		player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0D);
		player.setHealth(1000.0F);
		run("gamerule naturalRegeneration false");
		run("gamerule doMobSpawning false");
		run("kill @e[type=!minecraft:player]");
		run("summythreat tier full");
		run("summythreat tl off");
		log("世界准备完成：生存玩家 1000 血，tier=full，tl=off");
	}

	private static void spawnActors() {
		ServerLevel level = player.serverLevel();
		// tier 配置重载可能把全局 TL 拉回自动；生成前再确认关闭。
		run("summythreat tl off");
		normal = spawnZombie(level, 303.5D, 100.0D, 0.0D, "normal");
		slotTarget = spawnZombie(level, 303.0D, 100.0D, 0.0D, "slot");
		teamZombie = spawnZombie(level, 301.5D, 100.0D, 0.0D, "team");
		wolf = EntityType.WOLF.create(level);
		if (wolf == null) {
			throw new IllegalStateException("无法生成测试狼");
		}
		wolf.moveTo(301.0D, 100.0D, 0.0D, 0.0F, 0.0F);
		wolf.setNoAi(true);
		wolf.setPersistenceRequired();
		wolf.setTame(true);
		wolf.setOwnerUUID(player.getUUID());
		wolf.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0D);
		wolf.setHealth(1000.0F);
		level.addFreshEntity(wolf);

		Scoreboard scoreboard = player.server.getScoreboard();
		PlayerTeam team = scoreboard.getPlayerTeam("stormtest");
		if (team == null) {
			team = scoreboard.addPlayerTeam("stormtest");
		}
		scoreboard.addPlayerToTeam(player.getScoreboardName(), team);
		scoreboard.addPlayerToTeam(teamZombie.getStringUUID(), team);

		owner = EntityType.ZOMBIE.create(level);
		if (owner == null) {
			throw new IllegalStateException("无法生成电涌卓越者");
		}
		owner.moveTo(302.5D, 100.0D, 0.0D, 0.0F, 0.0F);
		owner.getPersistentData().putBoolean(MonsterScaler.TAG_EXIMUS, true);
		owner.getPersistentData().putString(MonsterScaler.TAG_ARCHETYPE, "storm");
		level.addFreshEntity(owner);
		MonsterScaler.scaleNow(owner, 20, MonsterScaler.Kind.EXIMUS);
		owner.setNoAi(true);
		owner.setPersistenceRequired();
		owner.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.0D);
		owner.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(0.0D);
		owner.setTarget(player);
		normal.setTarget(owner);
		slotTarget.setTarget(owner);
		log("测试实体已生成：owner/normal/slot/wolf/team");
	}

	private static Zombie spawnZombie(ServerLevel level, double x, double y, double z, String name) {
		Zombie zombie = EntityType.ZOMBIE.create(level);
		if (zombie == null) {
			throw new IllegalStateException("无法生成测试僵尸 " + name);
		}
		zombie.moveTo(x, y, z, 0.0F, 0.0F);
		zombie.setNoAi(true);
		zombie.setPersistenceRequired();
		zombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0D);
		zombie.getAttribute(Attributes.ARMOR).setBaseValue(0.0D);
		zombie.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(0.0D);
		zombie.setHealth(1000.0F);
		level.addFreshEntity(zombie);
		return zombie;
	}

	/** 清空主背包、护甲、副手与 Curios，避免 DevCheckWorld 上一轮 SR/SE 配置干扰。 */
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

	private static void checkFormula() throws Exception {
		Method method = EximusArchetypeEffects.class.getDeclaredMethod(
				"skillDamage", LivingEntity.class, LivingEntity.class, double.class);
		method.setAccessible(true);
		for (double d20 : new double[]{45.0D, 5.0D, 30.0D, 20.0D, 6.0D, 4.0D}) {
			float playerDamage = (float) method.invoke(null, owner, player, d20);
			float normalDamage = (float) method.invoke(null, owner, normal, d20);
			float wolfDamage = (float) method.invoke(null, owner, wolf, d20);
			float teamDamage = (float) method.invoke(null, owner, teamZombie, d20);
			check("倍率 d20=" + d20 + "：普通怪 10×",
					playerDamage > 0.0F && Math.abs(normalDamage / playerDamage - 10.0D) < 0.02D,
					"player=%.2f normal=%.2f".formatted(playerDamage, normalDamage));
			check("倍率 d20=" + d20 + "：玩家驯服狼 1×",
					playerDamage > 0.0F && Math.abs(wolfDamage / playerDamage - 1.0D) < 0.02D,
					"player=%.2f wolf=%.2f".formatted(playerDamage, wolfDamage));
			check("倍率 d20=" + d20 + "：同队僵尸 1×",
					playerDamage > 0.0F && Math.abs(teamDamage / playerDamage - 1.0D) < 0.02D,
					"player=%.2f team=%.2f".formatted(playerDamage, teamDamage));
		}
	}

	private static void checkStormResult() {
		checkDodged("玩家", player);
		checkHealth("普通僵尸", normal, 700.0F);
		checkDodged("玩家驯服狼", wolf);
		checkHealth("同队僵尸", teamZombie, 970.0F);
		checkHealth("施法者自身", owner, 2112.0F);
		check("电涌：冻结落点独立命中普通僵尸", markerChecked,
				"markerChecked=" + markerChecked);
	}

	/** 按游戏时间观察电涌追踪、冻结与落雷阶段，避免依赖 DevCheck 的固定总 tick。 */
	private static void observeStormStage() {
		if (owner == null || player == null || owner.isRemoved()) {
			return;
		}
		Object state = stormState();
		if (state == null) {
			return;
		}
		long castStart = (long) field(state, "castStart");
		if (castStart < 0L) {
			return;
		}
		if (observedCastStart < 0L) {
			observedCastStart = castStart;
			long nextCast = (long) field(state, "nextCast");
			check("电涌：技能间隔 160 tick", nextCast - castStart == 160L,
					"next-cast=" + (nextCast - castStart));
			check("电涌：跟随范围提升到 48", owner.getAttributeValue(Attributes.FOLLOW_RANGE) >= 47.999D,
					"follow=" + owner.getAttributeValue(Attributes.FOLLOW_RANGE));
			trackingPoint = stormPoint(player.getUUID());
		}
		long elapsed = player.serverLevel().getGameTime() - observedCastStart;
		if (elapsed == 10L && !movedPlayerOnce) {
			bindlessCheck("电涌：追踪阶段施法者未根定", !hasCastRoot(),
					"movement=" + owner.getAttributeValue(Attributes.MOVEMENT_SPEED));
			player.teleportTo(player.serverLevel(), 303.0D, 100.0D, 0.0D, 0.0F, 0.0F);
			movedPlayerOnce = true;
		}
		if (elapsed == 20L && trackingPoint != null) {
			Vec3 moved = stormPoint(player.getUUID());
			check("电涌：追踪点随移动玩家更新",
					moved != null && moved.distanceToSqr(trackingPoint) > 1.0D,
					"from=" + trackingPoint + " to=" + moved);
			trackingPoint = moved;
		}
		if (elapsed == 30L && !movedPlayerTwice) {
			player.teleportTo(player.serverLevel(), 306.0D, 100.0D, 0.0D, 0.0F, 0.0F);
			movedPlayerTwice = true;
		}
		if (elapsed == 40L && trackingPoint != null) {
			Vec3 moved = stormPoint(player.getUUID());
			check("电涌：追踪阶段持续跟随而非仅首次锁定",
					moved != null && moved.distanceToSqr(trackingPoint) > 1.0D,
					"from=" + trackingPoint + " to=" + moved);
			bindlessCheck("电涌：预警阶段结束前仍未根定", !hasCastRoot(),
					"movement=" + owner.getAttributeValue(Attributes.MOVEMENT_SPEED));
		}
		if (elapsed == 50L && !killedSlotTarget) {
			if (slotTarget != null) {
				slotTarget.discard();
			}
			killedSlotTarget = true;
		}
		if (elapsed == 55L) {
			check("电涌：追踪目标失效后槽位固定", slotFixed(slotTarget == null ? null : slotTarget.getUUID()),
					"slots=" + lockSlots());
		}
		if (elapsed == 59L) {
			trackingPoint = stormPoint(player.getUUID());
		}
		if (elapsed == 61L && frozenPoint == null) {
			frozenPoint = stormPoint(player.getUUID());
			check("电涌：第 60 tick 后落点冻结", frozenPoint != null
							&& trackingPoint != null && frozenPoint.distanceToSqr(trackingPoint) < 0.01D,
					"before=" + trackingPoint + " frozen=" + frozenPoint);
		}
		if (elapsed == 62L && !movedDuringLock) {
			bindlessCheck("电涌：1 秒锁定阶段施法者未根定", !hasCastRoot(),
					"movement=" + owner.getAttributeValue(Attributes.MOVEMENT_SPEED));
			player.teleportTo(player.serverLevel(), 312.0D, 100.0D, 0.0D, 0.0F, 0.0F);
			movedDuringLock = true;
		}
		if (elapsed == 70L && frozenPoint != null) {
			Vec3 current = stormPoint(player.getUUID());
			check("电涌：锁定期间移动不改变落点",
					current != null && current.distanceToSqr(frozenPoint) < 0.01D,
					"frozen=" + frozenPoint + " current=" + current);
		}
		if (elapsed == 78L) {
			markerChecked = normal != null && normal.isAlive();
		}
		if (elapsed == 84L) {
			markerChecked &= normal != null && normal.getHealth() < 1000.0F;
		}
	}

	private static boolean hasCastRoot() {
		AttributeInstance speed = owner.getAttribute(Attributes.MOVEMENT_SPEED);
		return speed != null && speed.getModifiers().stream()
				.anyMatch(modifier -> "summy_threat:archetype_cast_root".equals(modifier.getName()));
	}

	private static Object stormState() {
		try {
			Field field = com.summy.threat.eximus.EximusArchetypeEffects.class.getDeclaredField("STORM");
			field.setAccessible(true);
			Object raw = field.get(null);
			return raw instanceof Map<?, ?> map ? map.get(owner.getUUID()) : null;
		} catch (ReflectiveOperationException exception) {
			throw new IllegalStateException("读取 STORM 状态失败", exception);
		}
	}

	private static List<?> lockSlots() {
		Object state = stormState();
		if (state == null) {
			return List.of();
		}
		Object raw = field(state, "lockSlots");
		return raw instanceof List<?> list ? list : List.of();
	}

	private static Vec3 stormPoint(UUID targetId) {
		if (targetId == null) {
			return null;
		}
		for (Object slot : lockSlots()) {
			if (targetId.equals(field(slot, "targetId"))) {
				Object point = field(slot, "point");
				return point instanceof Vec3 vec ? vec : null;
			}
		}
		return null;
	}

	private static boolean slotFixed(UUID targetId) {
		if (targetId == null) {
			return false;
		}
		for (Object slot : lockSlots()) {
			if (targetId.equals(field(slot, "targetId"))) {
				return (boolean) field(slot, "fixed");
			}
		}
		return false;
	}

	private static Object field(Object target, String name) {
		try {
			Field field = target.getClass().getDeclaredField(name);
			field.setAccessible(true);
			return field.get(target);
		} catch (ReflectiveOperationException exception) {
			throw new IllegalStateException("读取字段 " + name + " 失败", exception);
		}
	}

	private static void bindlessCheck(String label, boolean ok, String detail) {
		check(label, ok, detail);
	}

	private static void checkDodged(String label, LivingEntity entity) {
		float damage = 1000.0F - entity.getHealth();
		check("电涌落雷：" + label + " 在冻结后移出半径成功躲避",
				damage >= 0.0F && damage < 0.5F,
				"实际扣血 %.1f（落点冻结后已移开）".formatted(damage));
	}

	private static void checkHealth(String label, LivingEntity entity, float expected) {
		float health = entity.getHealth();
		check("电涌落雷：" + label, Math.abs(health - expected) < 2.0F,
				"expected=%.1f actual=%.1f".formatted(expected, health));
	}

	private static void check(String label, boolean ok, String detail) {
		checks++;
		if (!ok) {
			FAILURES.add(label);
		}
		log((ok ? "OK  " : "FAIL") + " " + label + "｜" + detail);
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
		SummyThreat.LOGGER.info("[STORMCHECK] " + message);
	}
}
