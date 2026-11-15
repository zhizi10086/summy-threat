package com.summy.threat.devcheck;

import com.summy.threat.SummyThreat;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.ModList;

import java.util.ArrayList;
import java.util.List;

/** 只供无客户端 devcheck 启动的 B16 掉落规则自检。 */
@Mod.EventBusSubscriber(modid = SummyThreat.MOD_ID,
		bus = Mod.EventBusSubscriber.Bus.FORGE,
		value = net.minecraftforge.api.distmarker.Dist.DEDICATED_SERVER)
public final class EquipmentDropServerDevCheck {
	private static boolean ran;
	private static boolean srRan;

	private EquipmentDropServerDevCheck() {
	}

	@SubscribeEvent
	public static void onServerStarted(ServerStartedEvent event) {
		if (ran || !Boolean.getBoolean("summy_threat.devcheck")) {
			return;
		}
		ran = true;
		ServerLevel level = event.getServer().overworld();
		ServerPlayer onlinePlayer = event.getServer().getPlayerList().getPlayers().stream()
				.findFirst().orElse(null);
		ServerPlayer player = onlinePlayer != null
				? onlinePlayer : FakePlayerFactory.getMinecraft(level);
		List<String> failures = new ArrayList<>(EquipmentDropDevCheck.run(player));
		if (onlinePlayer != null && runSrIntegration(onlinePlayer)) {
			srRan = true;
			failures.addAll(EquipmentDropSrIntegrationDevCheck.run(onlinePlayer));
		}
		logResult(failures);
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
		if (srRan || !Boolean.getBoolean("summy_threat.devcheck")
				|| !(event.getEntity() instanceof ServerPlayer player)) {
			return;
		}
		srRan = true;
		List<String> failures = runSrIntegration(player)
				? EquipmentDropSrIntegrationDevCheck.run(player) : List.of();
		logResult(failures);
	}

	private static boolean runSrIntegration(ServerPlayer player) {
		return Boolean.getBoolean("summy.devcheck")
				&& ModList.get().isLoaded("summy_reliquary")
				&& player.getServer() != null
				&& player.getServer().getPlayerList().getPlayer(player.getUUID()) == player;
	}

	private static void logResult(List<String> failures) {
		if (failures.isEmpty()) {
			SummyThreat.LOGGER.info("[EQUIPMENT] 服务端专项自检通过");
		} else {
			SummyThreat.LOGGER.error("[EQUIPMENT] 服务端专项自检失败：{}", failures);
		}
	}
}
