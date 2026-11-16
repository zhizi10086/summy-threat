package com.summy.threat.equipment;

import java.util.Set;

/** 匠魂与 Tetra 的固定兼容边界，不引用外部模组类。 */
public final class EquipmentCompatibility {
	private static final Set<String> TETRA_MELEE = Set.of(
			"tetra:modular_sword",
			"tetra:modular_double");
	private static final Set<String> TETRA_RANGED = Set.of(
			"tetra:modular_bow",
			"tetra:modular_crossbow");

	private EquipmentCompatibility() {
	}

	public static EquipmentCategory categoryFor(String itemId, boolean tinkersArmor,
			boolean tinkersRanged, boolean tinkersMelee) {
		boolean armor = tinkersArmor;
		boolean ranged = tinkersRanged || TETRA_RANGED.contains(itemId);
		boolean melee = tinkersMelee || TETRA_MELEE.contains(itemId);
		return EquipmentCategory.resolve(armor, ranged, melee);
	}

	public static boolean isTetraRanged(String itemId) {
		return TETRA_RANGED.contains(itemId);
	}
}
