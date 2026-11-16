package com.summy.threat.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.summy.threat.equipment.ArmorDurabilityProtection;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;

/** 匠魂护甲绕过 ItemStack.hurt 时，仍套用 II 阶单次耐久上限。 */
@Mixin(targets = "slimeknights.tconstruct.library.tools.helper.ToolDamageUtil",
		remap = false)
public abstract class TConstructToolDamageMixin {
	@WrapOperation(
			method = "damage(Lslimeknights/tconstruct/library/tools/nbt/IToolStackView;ILnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lslimeknights/tconstruct/library/modifiers/ModifierId;)Z",
			at = @At(value = "INVOKE",
					target = "Lslimeknights/tconstruct/library/tools/helper/ToolDamageUtil;directDamage(Lslimeknights/tconstruct/library/tools/nbt/IToolStackView;ILnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;)Z"),
			require = 1)
	private static boolean summyThreat$capDirectDamage(IToolStackView tool, int amount,
			LivingEntity entity, ItemStack stack, Operation<Boolean> original) {
		int capped = ArmorDurabilityProtection.capFor(stack, amount, entity);
		return original.call(tool, capped, entity, stack);
	}
}
