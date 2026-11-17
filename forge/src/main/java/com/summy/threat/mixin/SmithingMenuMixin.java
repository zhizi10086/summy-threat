package com.summy.threat.mixin;

import com.summy.threat.equipment.EquipmentCraftingHandler;
import com.summy.threat.item.ModItems;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 锻造台结果取回时返还同步拆卸的棱晶；原版不会触发 ItemCraftedEvent。 */
@Mixin(SmithingMenu.class)
public abstract class SmithingMenuMixin {
	@Inject(method = "getSlotToQuickMoveTo", at = @At("HEAD"), cancellable = true)
	private void summyThreat$preferPrismInputSlot(ItemStack stack,
			CallbackInfoReturnable<Integer> callback) {
		if (!stack.is(ModItems.ABYSSAL_STAR_PRISM.get())) {
			return;
		}
		SmithingMenu menu = (SmithingMenu) (Object) this;
		callback.setReturnValue(menu.getSlot(SmithingMenu.BASE_SLOT).hasItem()
				? SmithingMenu.ADDITIONAL_SLOT : SmithingMenu.BASE_SLOT);
	}

	@Inject(method = "onTake", at = @At("HEAD"))
	private void summyThreat$returnDisassemblyPrisms(Player player,
			ItemStack output, CallbackInfo callback) {
		EquipmentCraftingHandler.returnPrisms(player, output);
	}
}
