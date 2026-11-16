package com.summy.threat.item;

import com.summy.threat.SummyThreat;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * 本模组的物品注册。
 *
 * <p>当前只注册 B15 的两种材料：
 * <ul>
 *     <li>卓越者精华：通用两段装备升级与高级棱晶刷新材料。</li>
 *     <li>星渊棱晶：三系潜在线、刷新与镶嵌材料。</li>
 * </ul>
 */
public final class ModItems {
	public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(net.minecraft.core.registries.Registries.ITEM,
			SummyThreat.MOD_ID);

	public static final RegistryObject<Item> EXIMUS_ESSENCE = ITEMS.register("eximus_essence",
			() -> new Item(new Item.Properties()));
	public static final RegistryObject<Item> ABYSSAL_STAR_PRISM = ITEMS.register("abyssal_star_prism",
			() -> new AbyssalStarPrismItem(new Item.Properties().stacksTo(1)));

	private ModItems() {
	}
}
