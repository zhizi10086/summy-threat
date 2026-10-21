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
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

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
	private static Zombie teamZombie;
	private static Wolf wolf;

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
		normal = spawnZombie(level, 305.0D, 100.0D, 0.0D, "normal");
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
		log("测试实体已生成：owner/normal/wolf/team");
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
		checkPlayerSideDamage("玩家", player);
		checkHealth("普通僵尸", normal, 700.0F);
		checkPlayerSideDamage("玩家驯服狼", wolf);
		checkHealth("同队僵尸", teamZombie, 970.0F);
		checkHealth("施法者自身", owner, 2112.0F);
	}

	/** 玩家与玩家友军吃原版/SR 的防御减伤，这里只断言确实被 1 倍落雷命中且不超过 30。 */
	private static void checkPlayerSideDamage(String label, LivingEntity entity) {
		float damage = 1000.0F - entity.getHealth();
		check("电涌落雷：" + label + " 被 1 倍命中", damage > 0.0F && damage <= 30.5F,
				"实际原始上限 30，最终扣血 %.1f（防御后）".formatted(damage));
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
