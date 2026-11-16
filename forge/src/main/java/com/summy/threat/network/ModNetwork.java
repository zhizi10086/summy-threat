package com.summy.threat.network;

import com.summy.threat.SummyThreat;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/** 本模组最小网络层：同步 KJS 展示元数据与装备客户端配置快照。 */
public final class ModNetwork {
	private static final String PROTOCOL = "3";
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
		CHANNEL.messageBuilder(RegistrySyncPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
				.encoder(RegistrySyncPacket::encode)
				.decoder(RegistrySyncPacket::decode)
				.consumerMainThread(RegistrySyncPacket::handle)
				.add();
	}

	public static void sendRegistry(ServerPlayer player) {
		if (player != null) {
			CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), RegistrySyncPacket.current());
		}
	}

	public static void broadcastRegistry(net.minecraft.server.MinecraftServer server) {
		if (server != null && !server.getPlayerList().getPlayers().isEmpty()) {
			CHANNEL.send(PacketDistributor.ALL.noArg(), RegistrySyncPacket.current());
		}
	}
}
