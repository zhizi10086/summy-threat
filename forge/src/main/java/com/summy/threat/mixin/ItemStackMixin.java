package com.summy.threat.mixin;

import com.summy.threat.equipment.ArmorDurabilityProtection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** 在 Unbreaking 随机处理之前限制 II 阶护甲原始耐久损失。 */
@Mixin(ItemStack.class)
public abstract class ItemStackMixin {
	@ModifyVariable(method = "hurt", at = @At("HEAD"), argsOnly = true)
	private int summyThreat$capArmorDurabilityLoss(int amount, RandomSource random,
			ServerPlayer player) {
		return ArmorDurabilityProtection.capFor((ItemStack) (Object) this, amount, player);
	}
}
