package com.summy.threat.equipment;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EquipmentCompatibilityTest {
	@Test
	void tinkersTagsUseArmorRangedMeleePriority() {
		assertEquals(EquipmentCategory.ARMOR, EquipmentCompatibility.categoryFor(
				"tconstruct:plate_chestplate", true, true, true));
		assertEquals(EquipmentCategory.RANGED, EquipmentCompatibility.categoryFor(
				"tconstruct:war_pick", false, true, true));
		assertEquals(EquipmentCategory.MELEE, EquipmentCompatibility.categoryFor(
				"tconstruct:cleaver", false, false, true));
		assertNull(EquipmentCompatibility.categoryFor(
				"tconstruct:pickaxe", false, false, false));
	}

	@Test
	void tetraOnlyAcceptsCombatForms() {
		assertEquals(EquipmentCategory.MELEE,
				EquipmentCompatibility.categoryFor("tetra:modular_sword", false, false, false));
		assertEquals(EquipmentCategory.MELEE,
				EquipmentCompatibility.categoryFor("tetra:modular_double", false, false, false));
		assertEquals(EquipmentCategory.RANGED,
				EquipmentCompatibility.categoryFor("tetra:modular_bow", false, false, false));
		assertEquals(EquipmentCategory.RANGED,
				EquipmentCompatibility.categoryFor("tetra:modular_crossbow", false, false, false));
		assertNull(EquipmentCompatibility.categoryFor(
				"tetra:modular_single", false, false, false));
		assertNull(EquipmentCompatibility.categoryFor(
				"tetra:dynamic_handheld", false, false, false));
	}

	@Test
	void onlyTetraBowsBypassGenericArrowLooseHandling() {
		assertTrue(EquipmentCompatibility.isTetraRanged("tetra:modular_bow"));
		assertTrue(EquipmentCompatibility.isTetraRanged("tetra:modular_crossbow"));
		assertFalse(EquipmentCompatibility.isTetraRanged("tconstruct:longbow"));
		assertFalse(EquipmentCompatibility.isTetraRanged("minecraft:bow"));
	}
}
