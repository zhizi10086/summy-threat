package com.summy.threat.equipment;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** 玩家级冷却封装：主体保存在 ForgeData，死亡克隆时复制。 */
public final class PlayerEquipmentCooldowns {
	private PlayerEquipmentCooldowns() {
	}

	public static long now(ServerPlayer player) {
		return player.server.overworld().getGameTime();
	}

	public static boolean ready(ServerPlayer player, String id) {
		return EquipmentCooldownData.ready(player.getPersistentData(), id, now(player));
	}

	public static long remaining(ServerPlayer player, String id) {
		return EquipmentCooldownData.remaining(player.getPersistentData(), id, now(player));
	}

	public static void set(ServerPlayer player, String id, int ticks) {
		EquipmentCooldownData.set(player.getPersistentData(), id, now(player) + Math.max(1, ticks));
	}

	@SubscribeEvent
	public static void onClone(PlayerEvent.Clone event) {
		if (!(event.getEntity() instanceof ServerPlayer target)) {
			return;
		}
		CompoundTag original = event.getOriginal().getPersistentData();
		if (original.contains(EquipmentCooldownData.ROOT)) {
			target.getPersistentData().put(EquipmentCooldownData.ROOT,
					original.getCompound(EquipmentCooldownData.ROOT).copy());
		}
	}
}
