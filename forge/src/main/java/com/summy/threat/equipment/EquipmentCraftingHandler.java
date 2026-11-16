package com.summy.threat.equipment;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** 同步拆卸的唯一返还入口。 */
public final class EquipmentCraftingHandler {
	private EquipmentCraftingHandler() {
	}

	@SubscribeEvent
	public static void onCrafted(PlayerEvent.ItemCraftedEvent event) {
		ItemStack crafted = event.getCrafting();
		if (crafted.isEmpty() || crafted.getTag() == null
				|| !(event.getEntity() instanceof ServerPlayer player)) {
			return;
		}
		CompoundTag equipmentRoot = crafted.getTag()
				.getCompound(EximusEquipmentData.EQUIPMENT_ROOT);
		if (!equipmentRoot.contains(EquipmentSmithingRecipe.RETURN_PRISMS, Tag.TAG_LIST)) {
			return;
		}
		ListTag returns = equipmentRoot.getList(EquipmentSmithingRecipe.RETURN_PRISMS,
				Tag.TAG_COMPOUND);
		equipmentRoot.remove(EquipmentSmithingRecipe.RETURN_PRISMS);
		for (int i = 0; i < returns.size(); i++) {
			ItemStack prism = ItemStack.of(returns.getCompound(i).copy());
			if (prism.isEmpty()) {
				continue;
			}
			if (!player.getInventory().add(prism)) {
				player.drop(prism, false);
			}
		}
		player.inventoryMenu.broadcastChanges();
	}
}
