package com.summy.threat.network;

import com.summy.threat.SummyThreat;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * 本模组的最小网络层：只同步“玩家卓越护盾”当前值，供客户端青色 HUD 使用。
 */
public final class ModNetwork {
	private static final String PROTOCOL = "1";
	public static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
			.named(new ResourceLocation(SummyThreat.MOD_ID, "main"))
			.networkProtocolVersion(() -> PROTOCOL)
			.clientAcceptedVersions(PROTOCOL::equals)
			.serverAcceptedVersions(PROTOCOL::equals)
			.simpleChannel();

	private ModNetwork() {
	}

	public static void register() {
		int id = 0;
		CHANNEL.messageBuilder(ShieldSyncPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
				.encoder(ShieldSyncPacket::encode)
				.decoder(ShieldSyncPacket::decode)
				.consumerMainThread(ShieldSyncPacket::handle)
				.add();
	}

	public static void sendShield(ServerPlayer player, double value) {
		if (player != null) {
			CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new ShieldSyncPacket(value));
		}
	}
}
