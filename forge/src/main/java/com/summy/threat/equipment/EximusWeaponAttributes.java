package com.summy.threat.equipment;

import com.summy.threat.Config;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.ItemAttributeModifierEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.UUID;

/** I 阶卓越武器的物品级固定攻速；对实际使用该物品的实体同样生效。 */
public final class EximusWeaponAttributes {
	private static final UUID FIXED_ATTACK_SPEED =
			UUID.fromString("6f5c1a2e-0019-4a0b-9d1e-000000000901");

	private EximusWeaponAttributes() {
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void onItemAttributes(ItemAttributeModifierEvent event) {
		if (!Config.SPEC.isLoaded() || !Config.EXIMUS_EQUIPMENT_ENABLED.get()
				|| event.getSlotType() != EquipmentSlot.MAINHAND) {
			return;
		}
		ItemStack stack = event.getItemStack();
		if (EquipmentTypeResolver.category(stack) != EquipmentCategory.MELEE
				|| EximusEquipmentData.upgradeLevel(stack) < 1) {
			return;
		}
		event.addModifier(Attributes.ATTACK_SPEED, new AttributeModifier(FIXED_ATTACK_SPEED,
				"summy_threat:eximus_weapon_speed",
				EximusUpgradeValues.MELEE_ATTACK_SPEED_BONUS,
				AttributeModifier.Operation.MULTIPLY_TOTAL));
	}
}
