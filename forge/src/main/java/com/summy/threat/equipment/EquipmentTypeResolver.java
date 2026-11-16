package com.summy.threat.equipment;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.BuiltInRegistries;
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
	public static final TagKey<Item> TINKERS_ARMOR = tag("tconstruct", "modifiable/armor/worn");
	public static final TagKey<Item> TINKERS_RANGED = tag("tconstruct", "modifiable/ranged/bows");
	public static final TagKey<Item> TINKERS_CROSSBOW = tag("tconstruct", "modifiable/ranged/crossbows");
	public static final TagKey<Item> TINKERS_MELEE = tag("tconstruct", "modifiable/melee/primary");

	private EquipmentTypeResolver() {
	}

	public static EquipmentCategory category(ItemStack stack) {
		if (stack.isEmpty()) {
			return null;
		}
		ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
		EquipmentCategory compatibility = EquipmentCompatibility.categoryFor(
				itemId.toString(), stack.is(TINKERS_ARMOR),
				stack.is(TINKERS_RANGED), stack.is(TINKERS_MELEE));
		boolean armor = compatibility == EquipmentCategory.ARMOR
				|| stack.is(UPGRADEABLE_ARMOR)
				|| stack.getItem() instanceof ArmorItem
				&& !(stack.getItem() instanceof HorseArmorItem);
		boolean ranged = compatibility == EquipmentCategory.RANGED
				|| stack.is(UPGRADEABLE_RANGED)
				|| stack.getItem() instanceof BowItem
				|| stack.getItem() instanceof CrossbowItem;
		boolean melee = compatibility == EquipmentCategory.MELEE
				|| stack.is(UPGRADEABLE_MELEE)
				|| stack.getItem() instanceof SwordItem
				|| stack.getItem() instanceof AxeItem
				|| stack.getItem() instanceof TridentItem
				|| stack.is(ItemTags.SWORDS)
				|| stack.is(ItemTags.AXES);
		return EquipmentCategory.resolve(armor, ranged, melee);
	}

	public static String itemId(ItemStack stack) {
		return stack.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
	}

	private static TagKey<Item> tag(String path) {
		return tag("summy_threat", path);
	}

	private static TagKey<Item> tag(String namespace, String path) {
		return ItemTags.create(new ResourceLocation(namespace, path));
	}
}
