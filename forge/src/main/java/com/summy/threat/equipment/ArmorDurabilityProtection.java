package com.summy.threat.equipment;

import com.summy.threat.Config;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;

/** II 阶护甲在原始耐久损耗入口截断单次损失。 */
public final class ArmorDurabilityProtection {
	private ArmorDurabilityProtection() {
	}

	public static int capLoss(int maxDamage, int requested, int upgradeLevel,
			int divisor, int min, int max) {
		if (upgradeLevel < 2 || requested <= 0 || maxDamage <= 0) {
			return requested;
		}
		int perHit = Math.max(1, (maxDamage + divisor - 1) / divisor);
		int capped = Math.max(min, Math.min(max, perHit));
		return Math.min(requested, capped);
	}

	public static int capFor(ItemStack stack, int requested, ServerPlayer player) {
		if (player == null || stack.isEmpty() || !Config.SPEC.isLoaded()
				|| !Config.EXIMUS_EQUIPMENT_ENABLED.get()
				|| !(stack.getItem() instanceof ArmorItem)
				|| EximusEquipmentData.upgradeLevel(stack) < 2) {
			return requested;
		}
		return capLoss(stack.getMaxDamage(), requested,
				EximusEquipmentData.upgradeLevel(stack),
				Config.ARMOR_DURABILITY_CAP_DIVISOR.get(),
				Config.ARMOR_DURABILITY_CAP_MIN.get(),
				Config.ARMOR_DURABILITY_CAP_MAX.get());
	}
}
