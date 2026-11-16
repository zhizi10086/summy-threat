package com.summy.threat.equipment;

import com.summy.threat.Config;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 从四个实际穿戴槽构造当前玩家词条快照。 */
public final class WornArmorState {
	private static final EquipmentSlot[] SLOTS = {
			EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
	};
	private static final Map<UUID, ArmorAffixSelection> CACHE = new HashMap<>();

	private WornArmorState() {
	}

	public static ArmorAffixSelection selection(ServerPlayer player) {
		ArmorAffixSelection cached = CACHE.get(player.getUUID());
		if (cached != null) {
			return cached;
		}
		ArmorAffixSelection built = build(player);
		CACHE.put(player.getUUID(), built);
		return built;
	}

	public static void invalidate(ServerPlayer player) {
		if (player != null) {
			CACHE.remove(player.getUUID());
		}
	}

	public static void clearAll() {
		CACHE.clear();
	}

	private static ArmorAffixSelection build(ServerPlayer player) {
		if (!Config.SPEC.isLoaded() || !Config.EXIMUS_EQUIPMENT_ENABLED.get()
				|| !Config.STAR_PRISM_ENABLED.get()) {
			return ArmorAffixSelection.select(Map.of(), 1);
		}
		Map<EquipmentSlot, List<String>> worn = new EnumMap<>(EquipmentSlot.class);
		for (EquipmentSlot slot : SLOTS) {
			List<String> ids = new ArrayList<>();
			ItemStack equipment = player.getItemBySlot(slot);
			if (EximusEquipmentData.isValid(equipment)
					&& EximusEquipmentData.upgradeLevel(equipment) >= 1) {
				for (int prismSlot = 0; prismSlot < EximusEquipmentData.SLOT_COUNT; prismSlot++) {
					StarPrismData.read(EximusEquipmentData.prism(equipment, prismSlot))
							.ifPresent(data -> {
								String id = data.affix(EquipmentCategory.ARMOR);
								StarPrismAffix affix = StarPrismAffixes.find(id);
								if (affix != null
										&& affix.category() == EquipmentCategory.ARMOR) {
									ids.add(id);
								}
							});
				}
			}
			worn.put(slot, ids);
		}
		int limit = StarPrismConfig.intValue("armor.basic_stack_limit");
		return ArmorAffixSelection.select(worn, limit);
	}
}
