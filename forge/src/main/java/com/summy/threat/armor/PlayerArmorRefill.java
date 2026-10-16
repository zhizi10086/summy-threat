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
 *
 * <p><b>UUID 必须按部位各一个</b>（原版 {@code ArmorItem} 用 {@code ARMOR_MODIFIER_UUID_PER_TYPE} 就是这个道理）：
 * 原版的装备变更逻辑是"按 UUID 先移除、再加回"，若四件共用同一个 UUID，后穿上的会顶掉先穿上的，
 * 最终只剩一件生效（实测症状：全套铁只显示 6）。这里为 护甲 / 韧性 × 四个部位 各发一个稳定 UUID。
 */
public final class PlayerArmorRefill {
	/** 护甲修饰符 UUID：索引 = 部位（0 头盔 / 1 胸甲 / 2 护腿 / 3 靴子） */
	private static final UUID[] ID_ARMOR = {
			UUID.fromString("6f5c1a2e-0008-4a0b-9d1e-000000000011"),
			UUID.fromString("6f5c1a2e-0008-4a0b-9d1e-000000000012"),
			UUID.fromString("6f5c1a2e-0008-4a0b-9d1e-000000000013"),
			UUID.fromString("6f5c1a2e-0008-4a0b-9d1e-000000000014"),
	};
	/** 韧性修饰符 UUID：索引同上 */
	private static final UUID[] ID_TOUGHNESS = {
			UUID.fromString("6f5c1a2e-0008-4a0b-9d1e-000000000021"),
			UUID.fromString("6f5c1a2e-0008-4a0b-9d1e-000000000022"),
			UUID.fromString("6f5c1a2e-0008-4a0b-9d1e-000000000023"),
			UUID.fromString("6f5c1a2e-0008-4a0b-9d1e-000000000024"),
	};

	/** 部位名（用于修饰符命名与命令输出） */
	private static final String[] SLOT_NAMES = {"helmet", "chestplate", "leggings", "boots"};

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
		event.addModifier(Attributes.ARMOR, new AttributeModifier(ID_ARMOR[slot],
				"summy_threat:armor_refill/" + SLOT_NAMES[slot], values[0], AttributeModifier.Operation.ADDITION));
		event.addModifier(Attributes.ARMOR_TOUGHNESS, new AttributeModifier(ID_TOUGHNESS[slot],
				"summy_threat:toughness_refill/" + SLOT_NAMES[slot], values[1], AttributeModifier.Operation.ADDITION));
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
