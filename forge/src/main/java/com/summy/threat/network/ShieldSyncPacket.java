package com.summy.threat.network;

import com.summy.threat.client.ClientShieldState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** 服务端 → 客户端：同步玩家卓越护盾当前值。 */
public record ShieldSyncPacket(double value) {
	public static void encode(ShieldSyncPacket packet, FriendlyByteBuf buffer) {
		buffer.writeDouble(packet.value);
	}

	public static ShieldSyncPacket decode(FriendlyByteBuf buffer) {
		return new ShieldSyncPacket(buffer.readDouble());
	}

	public static void handle(ShieldSyncPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
		NetworkEvent.Context context = contextSupplier.get();
		context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
				() -> () -> ClientShieldState.set(packet.value)));
		context.setPacketHandled(true);
	}
}
