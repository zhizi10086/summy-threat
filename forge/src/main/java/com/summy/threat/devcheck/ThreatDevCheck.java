package com.summy.threat.devcheck;

import com.summy.threat.SummyThreat;
import com.summy.threat.armor.PlayerEximusShield;
import com.summy.threat.item.ModItems;
import com.summy.threat.pcf.CdfManager;
import com.summy.threat.pcf.PcfManager;
import com.summy.threat.scaling.MonsterScaler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.BlockPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Summy Threat 的真实客户端自检。
 *
 * <p>只通过 {@code gradlew runClient -Pdevcheck --args="--quickPlaySingleplayer DevCheckWorld"}
 * 启用；正式 jar 会排除本包。这里做代码/数值关系的无头可判定断言，复杂视觉仍由人工确认。
 */
@Mod.EventBusSubscriber(modid = SummyThreat.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ThreatDevCheck {
	private static final boolean ENABLED = Boolean.getBoolean("summy_threat.devcheck");
	private static final int IDLE_STOP_TICKS = 600;
	private static final int TOTAL_STOP_TICKS = 6000;
	private static final int CLIENT_STOP_TICK = 360;
	private static int serverTick;
	private static int clientTick;
	private static int idleTicks;
	private static int totalClientTicks;
	private static boolean failed;

	private ThreatDevCheck() {
	}

	@SubscribeEvent
	public static void onServerTick(TickEvent.ServerTickEvent event) {
		if (!ENABLED || event.phase != TickEvent.Phase.END) {
			return;
		}
		if (event.getServer().getPlayerList().getPlayers().isEmpty()) {
			serverTick = 0;
			return;
		}
		ServerPlayer player = event.getServer().getPlayerList().getPlayers().get(0);
		serverTick++;
		try {
			switch (serverTick) {
				case 20 -> prepareWorld(player);
				case 40 -> prepareEquipment(player);
				case 140 -> checkEquipmentAndSetPcf(player);
				case 160 -> checkTl(player, false);
				case 180 -> checkTl(player, true);
				case 200 -> checkManualTl(player);
				case 220 -> prepareHud(player);
				case 320 -> cleanup(player);
				default -> {
				}
			}
		} catch (Throwable throwable) {
			failed = true;
			log("步骤 " + serverTick + " 异常：" + throwable);
			throwable.printStackTrace();
		}
	}

	@SubscribeEvent
	public static void onClientTick(TickEvent.ClientTickEvent event) {
		if (!ENABLED || event.phase != TickEvent.Phase.END) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		totalClientTicks++;
		if (totalClientTicks > TOTAL_STOP_TICKS) {
			log("硬停止：客户端总 tick 超限");
			client.stop();
			return;
		}
		if (client.player == null || client.level == null) {
			idleTicks++;
			if (idleTicks == IDLE_STOP_TICKS) {
				log("硬停止：连续 " + IDLE_STOP_TICKS + " tick 没有玩家/关卡；请确认 DevCheckWorld 已创建");
			}
			if (idleTicks > IDLE_STOP_TICKS) {
				client.stop();
			}
			return;
		}
		idleTicks = 0;
		clientTick++;
		if (clientTick == 260) {
			screenshot(client, "st_devcheck_hud");
		} else if (clientTick == 300) {
			screenshot(client, "st_devcheck_scene");
		} else if (clientTick >= CLIENT_STOP_TICK) {
			log("===== 客户端自检结束（failed=" + failed + "）=====");
			client.stop();
		}
	}

	private static void prepareWorld(ServerPlayer player) {
		ServerLevel level = player.serverLevel();
		for (int x = -2; x <= 2; x++) {
			for (int z = -2; z <= 2; z++) {
				level.setBlockAndUpdate(new BlockPos(300 + x, 99, z), Blocks.STONE.defaultBlockState());
			}
		}
		player.teleportTo(level, 300.0D, 100.0D, 0.0D, player.getYRot(), player.getXRot());
		run(player, "gamerule doMobSpawning false");
		run(player, "gamerule keepInventory true");
		run(player, "gamerule doDaylightCycle false");
		run(player, "difficulty normal");
		run(player, "summythreat tier full");
		run(player, "summythreat tl auto");
		log("世界准备完成（x=300，normal，关闭自然刷怪）");
	}

	private static void prepareEquipment(ServerPlayer player) {
		player.getInventory().clearContent();
		player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ModItems.ECHO_HELMET.get()));
		player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(ModItems.ECHO_CHESTPLATE.get()));
		player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(ModItems.ECHO_LEGGINGS.get()));
		player.setItemSlot(EquipmentSlot.FEET, new ItemStack(ModItems.ECHO_BOOTS.get()));
		player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.NETHERITE_SWORD));
		PcfManager.setValue(player, 0.0D);
	}

	private static void checkEquipmentAndSetPcf(ServerPlayer player) {
		PcfManager.Snapshot snapshot = PcfManager.snapshot(player);
		check("PCF 装备分约 18.4", Math.abs(snapshot.equipment() - 18.4D) < 0.2D,
				"equipment=" + snapshot.equipment());
		PcfManager.setValue(player, 10.0D);
		CdfManager.setCurrent(player.serverLevel(), player.blockPosition(), 5.0D);
	}

	private static void checkTl(ServerPlayer player, boolean eximus) {
		Zombie zombie = EntityType.ZOMBIE.create(player.serverLevel());
		if (zombie == null) {
			check("生成测试僵尸", false, "create 返回 null");
			return;
		}
		zombie.moveTo(player.getX() + 2.0D, player.getY(), player.getZ());
		if (eximus) {
			zombie.getPersistentData().putBoolean(MonsterScaler.TAG_EXIMUS, true);
		}
		player.serverLevel().addFreshEntity(zombie);
		int tl = zombie.getPersistentData().getInt(MonsterScaler.TAG_TL);
		int low = eximus ? 4 : 4;
		int high = eximus ? 6 : 6;
		check((eximus ? "卓越者" : "普通怪") + "自动 TL 在 4~6", tl >= low && tl <= high, "tl=" + tl);
		zombie.discard();
	}

	private static void checkManualTl(ServerPlayer player) {
		run(player, "summythreat tl 20");
		Zombie zombie = EntityType.ZOMBIE.create(player.serverLevel());
		if (zombie != null) {
			zombie.moveTo(player.getX() + 2.0D, player.getY(), player.getZ());
			player.serverLevel().addFreshEntity(zombie);
			int tl = zombie.getPersistentData().getInt(MonsterScaler.TAG_TL);
			check("手动 TL 覆盖为 20", tl == 20, "tl=" + tl);
			zombie.discard();
		}
		run(player, "summythreat tl auto");
	}

	private static void prepareHud(ServerPlayer player) {
		PlayerEximusShield.set(player, 50.0D);
		log("HUD 测试值已设为 50/100");
	}

	private static void cleanup(ServerPlayer player) {
		PlayerEximusShield.set(player, 0.0D);
		run(player, "summythreat tl auto");
		run(player, "summythreat tier auto");
	}

	private static void screenshot(Minecraft client, String name) {
		Screenshot.grab(client.gameDirectory, name, client.getMainRenderTarget(), message -> log("截图 " + name + "：" + message));
	}

	private static void run(ServerPlayer player, String command) {
		player.getServer().getCommands().performPrefixedCommand(
				player.createCommandSourceStack().withPermission(3), command);
	}

	private static void check(String label, boolean ok, String detail) {
		if (!ok) {
			failed = true;
		}
		log((ok ? "OK  " : "FAIL") + " " + label + "｜" + detail);
	}

	private static void log(String message) {
		SummyThreat.LOGGER.info("[DEVCHECK] " + message);
	}
}
