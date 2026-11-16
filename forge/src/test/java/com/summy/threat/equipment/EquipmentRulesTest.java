package com.summy.threat.equipment;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EquipmentRulesTest {
	@Test
	void categoryPriorityIsArmorThenRangedThenMelee() {
		assertEquals(EquipmentCategory.ARMOR,
				EquipmentCategory.resolve(true, true, true));
		assertEquals(EquipmentCategory.RANGED,
				EquipmentCategory.resolve(false, true, true));
		assertEquals(EquipmentCategory.MELEE,
				EquipmentCategory.resolve(false, false, true));
	}

	@Test
	void stageTwoAllowsEveryEquipmentCategory() {
		assertTrue(EquipmentRules.categoryEnabled(EquipmentCategory.ARMOR));
		assertTrue(EquipmentRules.categoryEnabled(EquipmentCategory.MELEE));
		assertTrue(EquipmentRules.categoryEnabled(EquipmentCategory.RANGED));
	}

	@Test
	void upgradeRulesEnforceExactNextLevelAndStageGate() {
		CompoundTag fresh = new CompoundTag();
		assertTrue(EquipmentRules.canUpgrade(fresh, 1, EquipmentCategory.ARMOR));
		assertTrue(EquipmentRules.canUpgrade(fresh, 1, EquipmentCategory.MELEE));
		assertTrue(EquipmentRules.canUpgrade(fresh, 1, EquipmentCategory.RANGED));

		CompoundTag levelOne = EximusEquipmentData.withUpgradeLevel(fresh, 1);
		assertTrue(EquipmentRules.canUpgrade(levelOne, 2, EquipmentCategory.ARMOR));
		assertFalse(EquipmentRules.canUpgrade(levelOne, 1, EquipmentCategory.ARMOR));

		CompoundTag levelTwo = EximusEquipmentData.withUpgradeLevel(levelOne, 2);
		assertFalse(EquipmentRules.canUpgrade(levelTwo, 2, EquipmentCategory.ARMOR));
	}

	@Test
	void embeddingRequiresRevealedCleanPrismAndUsesLowestEmptySlot() {
		CompoundTag equipment = EximusEquipmentData.withUpgradeLevel(new CompoundTag(), 2);
		CompoundTag prism = prism(true, null, "astral_plating", "prism_edge",
				"starlit_bowstring");

		assertEquals(0, EquipmentRules.embeddingSlot(equipment, prism,
				EquipmentCategory.ARMOR));

		CompoundTag first = EximusEquipmentData.withPrism(equipment, 0, prism);
		CompoundTag different = prism(true, null, "vital_constellation",
				"terminal_star", "terminal_star_shot");
		assertEquals(1, EquipmentRules.embeddingSlot(first, different,
				EquipmentCategory.ARMOR));
	}

	@Test
	void embeddingRejectsPendingUnrevealedDuplicateAndFullSockets() {
		CompoundTag equipment = EximusEquipmentData.withUpgradeLevel(new CompoundTag(), 2);
		CompoundTag valid = prism(true, null, "astral_plating", "prism_edge",
				"starlit_bowstring");

		assertEquals(-1, EquipmentRules.embeddingSlot(equipment,
				prism(false, null, "vital_constellation", "prism_edge",
						"starlit_bowstring"), EquipmentCategory.ARMOR));
		assertEquals(-1, EquipmentRules.embeddingSlot(equipment,
				prism(true, "advanced", "vital_constellation", "prism_edge",
						"starlit_bowstring"), EquipmentCategory.ARMOR));

		CompoundTag one = EximusEquipmentData.withPrism(equipment, 0, valid);
		assertEquals(-1, EquipmentRules.embeddingSlot(one,
				prism(true, null, "astral_plating", "prism_edge",
						"starlit_bowstring"), EquipmentCategory.ARMOR));

		CompoundTag two = EximusEquipmentData.withPrism(one, 1,
				prism(true, null, "vital_constellation", "terminal_star",
						"terminal_star_shot"));
		assertEquals(-1, EquipmentRules.embeddingSlot(two,
				prism(true, null, "steady_heart", "starfire_cycle",
						"starfall_cycle_arrow"), EquipmentCategory.ARMOR));
	}

	private static CompoundTag prism(boolean revealed, String pending, String armor,
			String melee, String ranged) {
		CompoundTag tag = new CompoundTag();
		StarPrismData.write(tag, new StarPrismData(1, revealed, pending,
				armor, melee, ranged));
		return tag;
	}
}
