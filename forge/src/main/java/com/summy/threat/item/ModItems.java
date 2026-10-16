package com.summy.threat.item;

import com.summy.threat.SummyThreat;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * 本模组的物品注册。
 *
 * <p>本轮新增「强化下界合金」所需的 5 个物品：
 * <ul>
 *     <li>回响下界合金四件（{@code echo_netherite_*}）——逐件数值见 {@code PlayerArmorRefill}；</li>
 *     <li>卓越者精华（{@code eximus_essence}）——本轮**只注册、无获取途径**，来源随"卓越者刷新"轮落地，
 *         测试期用 {@code /give}；贴图暂用原版下界之星占位。</li>
 * </ul>
 */
public final class ModItems {
	public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(net.minecraft.core.registries.Registries.ITEM,
			SummyThreat.MOD_ID);

	/** 回响下界合金的护甲材质（同名 netherite → 复用原版甲片贴图） */
	public static final ArmorMaterial ECHO_NETHERITE = new EchoArmorMaterial();

	public static final RegistryObject<Item> EXIMUS_ESSENCE = ITEMS.register("eximus_essence",
			() -> new Item(new Item.Properties()));

	public static final RegistryObject<ArmorItem> ECHO_HELMET = ITEMS.register("echo_netherite_helmet",
			() -> new ArmorItem(ECHO_NETHERITE, ArmorItem.Type.HELMET, new Item.Properties()));
	public static final RegistryObject<ArmorItem> ECHO_CHESTPLATE = ITEMS.register("echo_netherite_chestplate",
			() -> new ArmorItem(ECHO_NETHERITE, ArmorItem.Type.CHESTPLATE, new Item.Properties()));
	public static final RegistryObject<ArmorItem> ECHO_LEGGINGS = ITEMS.register("echo_netherite_leggings",
			() -> new ArmorItem(ECHO_NETHERITE, ArmorItem.Type.LEGGINGS, new Item.Properties()));
	public static final RegistryObject<ArmorItem> ECHO_BOOTS = ITEMS.register("echo_netherite_boots",
			() -> new ArmorItem(ECHO_NETHERITE, ArmorItem.Type.BOOTS, new Item.Properties()));

	private ModItems() {
	}
}
