package com.summy.threat.network;

import com.summy.threat.legendary.FrightfulPresenceOverlay;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** 仅通知对应玩家自己的畏怖状态，持续时间为零时立即清除。 */
record FrightfulPresencePacket(int duration, int color) {
	static void encode(FrightfulPresencePacket packet, FriendlyByteBuf buffer) {
		buffer.writeVarInt(packet.duration);
		buffer.writeInt(packet.color);
	}

	static FrightfulPresencePacket decode(FriendlyByteBuf buffer) {
		return new FrightfulPresencePacket(buffer.readVarInt(), buffer.readInt());
	}

	static void handle(FrightfulPresencePacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
		DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
				() -> () -> FrightfulPresenceOverlay.apply(packet.duration, packet.color));
		contextSupplier.get().setPacketHandled(true);
	}
}
