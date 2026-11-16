package com.summy.threat.equipment;

import com.summy.threat.Config;
import com.summy.threat.api.event.ThreatConfigChangedEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.ItemAttributeModifierEvent;
import net.minecraftforge.event.entity.living.LivingEquipmentChangeEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.UUID;

/** I 阶卓越盔甲的四槽物品级固定属性。 */
public final class EximusArmorAttributes {
	public static final double ARMOR_BONUS = 2.0D;
	public static final double TOUGHNESS_BONUS = 1.5D;
	public static final double KNOCKBACK_BONUS = 0.05D;
	private static final EquipmentSlot[] ARMOR_SLOTS = {
			EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
	};
	private static final UUID[] ARMOR_IDS = ids("armor", 0x611);
	private static final UUID[] TOUGHNESS_IDS = ids("toughness", 0x621);
	private static final UUID[] KNOCKBACK_IDS = ids("knockback", 0x631);

	private EximusArmorAttributes() {
	}

	@SubscribeEvent
	public static void onItemAttributes(ItemAttributeModifierEvent event) {
		if (!Config.SPEC.isLoaded() || !Config.EXIMUS_EQUIPMENT_ENABLED.get()
				|| EximusEquipmentData.upgradeLevel(event.getItemStack()) < 1) {
			return;
		}
		ItemStack stack = event.getItemStack();
		if (!(stack.getItem() instanceof ArmorItem armor)
				|| event.getSlotType() != armor.getType().getSlot()) {
			return;
		}
		int index = slotIndex(event.getSlotType());
		if (index < 0) {
			return;
		}
		event.addModifier(Attributes.ARMOR, modifier(ARMOR_IDS[index],
				"summy_threat:eximus_armor/" + event.getSlotType().getName(), ARMOR_BONUS));
		event.addModifier(Attributes.ARMOR_TOUGHNESS, modifier(TOUGHNESS_IDS[index],
				"summy_threat:eximus_toughness/" + event.getSlotType().getName(),
				TOUGHNESS_BONUS));
		event.addModifier(Attributes.KNOCKBACK_RESISTANCE, modifier(KNOCKBACK_IDS[index],
				"summy_threat:eximus_knockback/" + event.getSlotType().getName(),
				KNOCKBACK_BONUS));
	}

	@SubscribeEvent
	public static void onEquipmentChange(LivingEquipmentChangeEvent event) {
		// 事件属性由原版在装备变化时自动替换；这里只处理从损坏数据恢复为可读物品的情况。
		if (event.getSlot().getType() == EquipmentSlot.Type.ARMOR) {
			refreshSlot(event.getEntity(), event.getSlot());
		}
	}

	@SubscribeEvent
	public static void onServerStarted(ServerStartedEvent event) {
		refreshAll(event.getServer());
	}

	@SubscribeEvent
	public static void onThreatConfigChanged(ThreatConfigChangedEvent event) {
		refreshAll(ServerLifecycleHooks.getCurrentServer());
	}

	public static void refreshAll(net.minecraft.server.MinecraftServer server) {
		if (server == null) {
			return;
		}
		for (ServerLevel level : server.getAllLevels()) {
			for (var entity : level.getAllEntities()) {
				if (entity instanceof LivingEntity living) {
					for (EquipmentSlot slot : ARMOR_SLOTS) {
						refreshSlot(living, slot);
					}
				}
			}
		}
	}

	public static void refreshPlayer(ServerPlayer player) {
		if (player != null) {
			for (EquipmentSlot slot : ARMOR_SLOTS) {
				refreshSlot(player, slot);
			}
		}
	}

	private static void refreshSlot(LivingEntity entity, EquipmentSlot slot) {
		int index = slotIndex(slot);
		if (index < 0) {
			return;
		}
		remove(entity, Attributes.ARMOR, ARMOR_IDS[index]);
		remove(entity, Attributes.ARMOR_TOUGHNESS, TOUGHNESS_IDS[index]);
		remove(entity, Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_IDS[index]);
		if (!Config.SPEC.isLoaded() || !Config.EXIMUS_EQUIPMENT_ENABLED.get()) {
			return;
		}
		ItemStack stack = entity.getItemBySlot(slot);
		if (EximusEquipmentData.upgradeLevel(stack) < 1
				|| !(stack.getItem() instanceof ArmorItem armor)
				|| armor.getType().getSlot() != slot) {
			return;
		}
		add(entity, Attributes.ARMOR, modifier(ARMOR_IDS[index],
				"summy_threat:eximus_armor_manual/" + slot.getName(), ARMOR_BONUS));
		add(entity, Attributes.ARMOR_TOUGHNESS, modifier(TOUGHNESS_IDS[index],
				"summy_threat:eximus_toughness_manual/" + slot.getName(), TOUGHNESS_BONUS));
		add(entity, Attributes.KNOCKBACK_RESISTANCE, modifier(KNOCKBACK_IDS[index],
				"summy_threat:eximus_knockback_manual/" + slot.getName(), KNOCKBACK_BONUS));
	}

	private static void add(LivingEntity entity, Attribute attribute, AttributeModifier modifier) {
		AttributeInstance instance = entity.getAttribute(attribute);
		if (instance != null && instance.getModifier(modifier.getId()) == null) {
			instance.addTransientModifier(modifier);
		}
	}

	private static void remove(LivingEntity entity, Attribute attribute, UUID id) {
		AttributeInstance instance = entity.getAttribute(attribute);
		if (instance != null) {
			instance.removeModifier(id);
		}
	}

	private static AttributeModifier modifier(UUID id, String name, double amount) {
		return new AttributeModifier(id, name, amount, AttributeModifier.Operation.ADDITION);
	}

	private static int slotIndex(EquipmentSlot slot) {
		return switch (slot) {
			case HEAD -> 0;
			case CHEST -> 1;
			case LEGS -> 2;
			case FEET -> 3;
			default -> -1;
		};
	}

	private static UUID[] ids(String kind, int seed) {
		UUID[] out = new UUID[4];
		for (int i = 0; i < out.length; i++) {
			out[i] = UUID.fromString("6f5c1a2e-0015-4a0b-9d1e-%012d".formatted(seed + i));
		}
		return out;
	}
}
