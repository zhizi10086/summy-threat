package com.summy.threat.devcheck;

import com.summy.threat.SummyThreat;
import com.summy.threat.scaling.MonsterScaler;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
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

import java.util.ArrayList;
import java.util.List;

/**
 * Dev-only：范围边界与云团多目标。
 *
 * <p>只通过 {@code -Prangecheck} 启用；验证烈焰 9 格、剧毒 3 格、震荡 12 格边界，
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
			switch (tick) {
				case 20 -> prepare();
				case 40 -> flameNear();
				case 100 -> checkPlayerHit("烈焰 8.5 格命中", true);
				case 120 -> flameFar();
				case 180 -> checkPlayerHit("烈焰 9.5 格不命中", false);
				case 200 -> poisonNear();
				case 260 -> checkPlayerHit("剧毒 2.5 格命中", true);
				case 280 -> poisonFar();
				case 340 -> checkPlayerHit("剧毒 3.5 格不命中", false);
				case 360 -> shockNear();
				case 480 -> checkPlayerHit("震荡 10 格命中", true);
				case 500 -> shockFar();
				case 620 -> checkPlayerHit("震荡 14 格不命中", false);
				case 640 -> cloudCase("vampire");
				case 760 -> checkCloudCase("吸血");
				case 780 -> cloudCase("magnet");
				case 900 -> checkCloudCase("磁力");
				case 920 -> finish("完成");
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
		beginAt(300.0D, 308.5D);
		owner = spawnEximus("flame", 300.0D);
		owner.setTarget(player);
	}

	private static void flameFar() {
		beginAt(300.0D, 309.5D);
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
		beginAt(300.0D, 310.0D);
		owner = spawnEximus("shock", 300.0D);
		owner.setTarget(player);
	}

	private static void shockFar() {
		// 最后一根柱在 12 格、半径 1.1；13 格仍可能擦到，14 格才是明确外圈。
		beginAt(300.0D, 314.0D);
		owner = spawnEximus("shock", 300.0D);
		owner.setTarget(player);
	}

	private static void cloudCase(String archetype) {
		beginAt(304.0D, 300.0D);
		owner = spawnEximus(archetype, 304.0D);
		owner.setTarget(player);
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
		endCase();
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
