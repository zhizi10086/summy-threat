package com.summy.threat.equipment;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** 锻造台同步拆卸的唯一返还入口。 */
public final class EquipmentCraftingHandler {
	private EquipmentCraftingHandler() {
	}

	static ReturnPayload takeReturnPayload(CompoundTag outputTag) {
		if (outputTag == null
				|| !outputTag.contains(EximusEquipmentData.EQUIPMENT_ROOT,
						Tag.TAG_COMPOUND)) {
			return null;
		}
		CompoundTag root = outputTag.getCompound(EximusEquipmentData.EQUIPMENT_ROOT);
		if (!root.contains(EquipmentSmithingRecipe.RETURN_PRISMS, Tag.TAG_LIST)) {
			return null;
		}
		String transactionId = root.getString(EquipmentSmithingRecipe.RETURN_ID);
		ListTag stored = root.getList(EquipmentSmithingRecipe.RETURN_PRISMS,
				Tag.TAG_COMPOUND);
		ListTag returns = new ListTag();
		for (int i = 0; i < stored.size(); i++) {
			returns.add(stored.getCompound(i).copy());
		}
		root.remove(EquipmentSmithingRecipe.RETURN_PRISMS);
		root.remove(EquipmentSmithingRecipe.RETURN_ID);
		return new ReturnPayload(transactionId, returns);
	}

	record ReturnPayload(String transactionId, ListTag stacks) {
	}

	/** 由 SmithingMenuMixin 在真实取回路径调用；客户端和非锻造结果会直接忽略。 */
	public static void returnPrisms(Player player, ItemStack output) {
		if (!(player instanceof ServerPlayer serverPlayer)) {
			return;
		}
		ReturnPayload payload = takeReturnPayload(output.getTag());
		if (payload == null && output.isEmpty()) {
			payload = findPendingPayload(serverPlayer);
		}
		if (payload == null) {
			return;
		}
		stripMatchingPayloads(serverPlayer, payload.transactionId(), output);
		for (int i = 0; i < payload.stacks().size(); i++) {
			ItemStack prism = ItemStack.of(payload.stacks().getCompound(i).copy());
			if (prism.isEmpty()) {
				continue;
			}
			prism.setCount(1);
			if (!serverPlayer.getInventory().add(prism)) {
				serverPlayer.drop(prism, false);
			}
		}
		serverPlayer.containerMenu.broadcastChanges();
		serverPlayer.inventoryMenu.broadcastChanges();
	}

	private static ReturnPayload findPendingPayload(ServerPlayer player) {
		ReturnPayload carried = takeReturnPayload(
				player.containerMenu.getCarried().getTag());
		if (carried != null) {
			return carried;
		}
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			ReturnPayload payload = takeReturnPayload(
					player.getInventory().getItem(slot).getTag());
			if (payload != null) {
				return payload;
			}
		}
		return null;
	}

	private static void stripMatchingPayloads(ServerPlayer player,
			String transactionId, ItemStack output) {
		stripPayload(output, transactionId);
		stripPayload(player.containerMenu.getCarried(), transactionId);
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			stripPayload(player.getInventory().getItem(slot), transactionId);
		}
	}

	private static void stripPayload(ItemStack stack, String transactionId) {
		CompoundTag tag = stack.getTag();
		if (tag == null || !tag.contains(EximusEquipmentData.EQUIPMENT_ROOT,
				Tag.TAG_COMPOUND)) {
			return;
		}
		CompoundTag root = tag.getCompound(EximusEquipmentData.EQUIPMENT_ROOT);
		if (!root.contains(EquipmentSmithingRecipe.RETURN_PRISMS, Tag.TAG_LIST)) {
			return;
		}
		if (!transactionId.isBlank() && !transactionId.equals(
				root.getString(EquipmentSmithingRecipe.RETURN_ID))) {
			return;
		}
		root.remove(EquipmentSmithingRecipe.RETURN_PRISMS);
		root.remove(EquipmentSmithingRecipe.RETURN_ID);
	}
}
