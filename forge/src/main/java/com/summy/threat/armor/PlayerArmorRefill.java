package com.summy.threat.armor;

import com.summy.threat.Config;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.ItemAttributeModifierEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.UUID;

/**
 * 玩家护甲重填（交接文档 §3.1）：把原版六套护甲件的护甲 / 韧性改成设计值。
 *
 * <p>逐件表来源《玩家护甲-逐件数值.txt》（首轮核验通过）；列序为 头盔 / 胸甲 / 护腿 / 靴子。
 * 「下界合金（升级后）」因升级途径未定（待补 A1）暂缺，因此下界合金按"基础"档处理。
 * 只改这两项属性，不触碰其它修饰符。
 */
public final class PlayerArmorRefill {
	private static final UUID ID_ARMOR = UUID.fromString("6f5c1a2e-0008-4a0b-9d1e-000000000001");
	private static final UUID ID_TOUGHNESS = UUID.fromString("6f5c1a2e-0008-4a0b-9d1e-000000000002");

	/** 逐件表 [材质][槽位] = {护甲, 韧性}；材质顺序：皮革 / 锁链 / 黄金 / 铁 / 钻石 / 下界合金（基础） */
	private static final double[][][] TABLE = {
			{{1, 1}, {3, 3}, {2, 1}, {1, 1}},      // 皮革
			{{2, 2}, {4, 3}, {3, 2}, {1, 1}},      // 锁链
			{{2, 3}, {3, 4}, {2, 3}, {1, 2}},      // 黄金
			{{3, 2}, {6, 3}, {4, 2}, {2, 1}},      // 铁
			{{5, 1}, {9, 2}, {7, 1}, {4, 1}},      // 钻石
			{{5, 3}, {10, 4}, {7, 3}, {4, 2}},     // 下界合金（基础）
	};

	private PlayerArmorRefill() {
	}

	@SubscribeEvent
	public static void onItemAttributes(ItemAttributeModifierEvent event) {
		if (!Config.ENABLE_PLAYER_ARMOR_REFILL.get()) {
			return;
		}
		ItemStack stack = event.getItemStack();
		if (!(stack.getItem() instanceof ArmorItem armor)) {
			return;
		}
		int material = materialIndex(armor.getMaterial());
		if (material < 0) {
			return;
		}
		int slot = switch (armor.getType()) {
			case HELMET -> 0;
			case CHESTPLATE -> 1;
			case LEGGINGS -> 2;
			case BOOTS -> 3;
		};
		double[] values = TABLE[material][slot];

		event.removeAttribute(Attributes.ARMOR);
		event.removeAttribute(Attributes.ARMOR_TOUGHNESS);
		event.addModifier(Attributes.ARMOR, new AttributeModifier(ID_ARMOR, "summy_threat:armor_refill",
				values[0], AttributeModifier.Operation.ADDITION));
		event.addModifier(Attributes.ARMOR_TOUGHNESS, new AttributeModifier(ID_TOUGHNESS,
				"summy_threat:toughness_refill", values[1], AttributeModifier.Operation.ADDITION));
	}

	private static int materialIndex(ArmorMaterial material) {
		if (material == ArmorMaterials.LEATHER) {
			return 0;
		}
		if (material == ArmorMaterials.CHAIN) {
			return 1;
		}
		if (material == ArmorMaterials.GOLD) {
			return 2;
		}
		if (material == ArmorMaterials.IRON) {
			return 3;
		}
		if (material == ArmorMaterials.DIAMOND) {
			return 4;
		}
		if (material == ArmorMaterials.NETHERITE) {
			return 5;
		}
		return -1;
	}
}
