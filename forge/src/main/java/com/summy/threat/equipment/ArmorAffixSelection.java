package com.summy.threat.equipment;

import net.minecraft.world.entity.EquipmentSlot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 已穿戴盔甲词条的确定性激活快照。 */
public final class ArmorAffixSelection {
	private static final EquipmentSlot[] PRIORITY = {
			EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
	};
	private static final Set<String> MULTI_STACK = Set.of(
			"astral_plating", "vital_constellation", "star_mantle");
	private final Map<EquipmentSlot, List<String>> active;
	private final Map<String, Integer> counts;
	private final Set<String> suppressed;

	private ArmorAffixSelection(Map<EquipmentSlot, List<String>> active,
			Map<String, Integer> counts, Set<String> suppressed) {
		this.active = active;
		this.counts = counts;
		this.suppressed = suppressed;
	}

	public static ArmorAffixSelection select(Map<EquipmentSlot, List<String>> worn,
			int basicStackLimit) {
		Map<EquipmentSlot, List<String>> active = new EnumMap<>(EquipmentSlot.class);
		for (EquipmentSlot slot : PRIORITY) {
			active.put(slot, new ArrayList<>());
		}
		Map<String, Integer> counts = new HashMap<>();
		Set<String> suppressed = new HashSet<>();
		int limit = Math.max(1, Math.min(4, basicStackLimit));
		for (EquipmentSlot slot : PRIORITY) {
			for (String id : worn.getOrDefault(slot, List.of())) {
				StarPrismAffix affix = StarPrismAffixes.find(id);
				if (affix == null || affix.category() != EquipmentCategory.ARMOR) {
					continue;
				}
				int max = MULTI_STACK.contains(id) ? limit : 1;
				int current = counts.getOrDefault(id, 0);
				if (current < max) {
					active.get(slot).add(id);
					counts.put(id, current + 1);
				} else {
					suppressed.add(slot.getName() + ":" + id);
				}
			}
		}
		Map<EquipmentSlot, List<String>> frozen = new EnumMap<>(EquipmentSlot.class);
		active.forEach((slot, ids) -> frozen.put(slot, List.copyOf(ids)));
		return new ArmorAffixSelection(Collections.unmodifiableMap(frozen),
				Map.copyOf(counts), Set.copyOf(suppressed));
	}

	public List<String> active(EquipmentSlot slot) {
		return active.getOrDefault(slot, List.of());
	}

	public boolean has(String id) {
		return count(id) > 0;
	}

	public int count(String id) {
		return counts.getOrDefault(id, 0);
	}

	public boolean suppressed(EquipmentSlot slot, String id) {
		return suppressed.contains(slot.getName() + ":" + id);
	}

	public Map<String, Integer> counts() {
		return counts;
	}
}
