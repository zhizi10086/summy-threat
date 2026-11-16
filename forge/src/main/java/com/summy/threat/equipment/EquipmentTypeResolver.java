package com.summy.threat.equipment;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.HorseArmorItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;

/** 物品类别判定；盔甲 > 远程 > 近战。 */
public final class EquipmentTypeResolver {
	public static final TagKey<Item> UPGRADEABLE_ARMOR = tag("eximus_upgradeable_armor");
	public static final TagKey<Item> UPGRADEABLE_MELEE = tag("eximus_upgradeable_melee");
	public static final TagKey<Item> UPGRADEABLE_RANGED = tag("eximus_upgradeable_ranged");

	private EquipmentTypeResolver() {
	}

	public static EquipmentCategory category(ItemStack stack) {
		if (stack.isEmpty()) {
			return null;
		}
		boolean armor = stack.is(UPGRADEABLE_ARMOR)
				|| stack.getItem() instanceof ArmorItem
				&& !(stack.getItem() instanceof HorseArmorItem);
		boolean ranged = stack.is(UPGRADEABLE_RANGED)
				|| stack.getItem() instanceof BowItem
				|| stack.getItem() instanceof CrossbowItem;
		boolean melee = stack.is(UPGRADEABLE_MELEE)
				|| stack.getItem() instanceof SwordItem
				|| stack.getItem() instanceof AxeItem
				|| stack.getItem() instanceof TridentItem
				|| stack.is(ItemTags.SWORDS)
				|| stack.is(ItemTags.AXES);
		return EquipmentCategory.resolve(armor, ranged, melee);
	}

	private static TagKey<Item> tag(String path) {
		return ItemTags.create(new ResourceLocation("summy_threat", path));
	}
}
