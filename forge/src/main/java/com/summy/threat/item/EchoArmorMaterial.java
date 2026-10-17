package com.summy.threat.item;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * 回响下界合金的护甲材质（实现 1.20.1 的 {@link ArmorMaterial} 接口）。
 *
 * <p>取值对齐下界合金：耐久倍率 37（头盔 13×37 / 胸甲 16×37 / 护腿 15×37 / 靴子 11×37）、
 * 附魔值 15、击退抗性 0.1、装备音效与修复材料（下界合金锭）相同；
 * 回响套额外 +0.1/件的击退抗性由 {@code PlayerArmorRefill} 注入，满套合计 0.8。
 * 逐件防御按交接文档 §3.1「下界合金（升级后）」：头盔 6 / 胸甲 11 / 护腿 8 / 靴子 5（合计 30）。
 *
 * <p>{@code getName()} 返回 {@code "netherite"}，因此**穿戴渲染直接复用原版下界合金甲片贴图**；
 * 逐件韧性（4 / 6 / 5 / 3，合计 18）由 {@code PlayerArmorRefill} 按表覆盖
 * ——"每件 4.5"无法用材质单一常量表达。
 */
public final class EchoArmorMaterial implements ArmorMaterial {
	@Override
	public int getDurabilityForType(ArmorItem.Type type) {
		return switch (type) {
			case HELMET -> 13 * 37;
			case CHESTPLATE -> 16 * 37;
			case LEGGINGS -> 15 * 37;
			case BOOTS -> 11 * 37;
		};
	}

	@Override
	public int getDefenseForType(ArmorItem.Type type) {
		return switch (type) {
			case HELMET -> 6;
			case CHESTPLATE -> 11;
			case LEGGINGS -> 8;
			case BOOTS -> 5;
		};
	}

	@Override
	public int getEnchantmentValue() {
		return 15;
	}

	@Override
	public SoundEvent getEquipSound() {
		return SoundEvents.ARMOR_EQUIP_NETHERITE;
	}

	@Override
	public Ingredient getRepairIngredient() {
		return Ingredient.of(Items.NETHERITE_INGOT);
	}

	/** 与原版下界合金同名 → 复用原版甲片贴图（textures/models/armor/netherite_layer_1/2） */
	@Override
	public String getName() {
		return "netherite";
	}

	@Override
	public float getToughness() {
		return 3.0F;
	}

	@Override
	public float getKnockbackResistance() {
		return 0.1F;
	}
}
