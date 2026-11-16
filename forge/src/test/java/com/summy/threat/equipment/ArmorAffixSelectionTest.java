package com.summy.threat.equipment;

import net.minecraft.world.entity.EquipmentSlot;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArmorAffixSelectionTest {
	@Test
	void numericAffixesOnlyUseFirstTwoSlotsInPriorityOrder() {
		Map<EquipmentSlot, List<String>> worn = new EnumMap<>(EquipmentSlot.class);
		worn.put(EquipmentSlot.FEET, List.of("astral_plating"));
		worn.put(EquipmentSlot.LEGS, List.of("astral_plating"));
		worn.put(EquipmentSlot.CHEST, List.of("astral_plating"));
		worn.put(EquipmentSlot.HEAD, List.of("astral_plating"));

		ArmorAffixSelection selection = ArmorAffixSelection.select(worn, 2);

		assertEquals(2, selection.count("astral_plating"));
		assertTrue(selection.active(EquipmentSlot.HEAD).contains("astral_plating"));
		assertTrue(selection.active(EquipmentSlot.CHEST).contains("astral_plating"));
		assertTrue(selection.active(EquipmentSlot.LEGS).isEmpty());
		assertTrue(selection.active(EquipmentSlot.FEET).isEmpty());
	}

	@Test
	void functionalAffixesAreGloballyUnique() {
		Map<EquipmentSlot, List<String>> worn = new EnumMap<>(EquipmentSlot.class);
		worn.put(EquipmentSlot.HEAD, List.of("projectile_ward"));
		worn.put(EquipmentSlot.CHEST, List.of("projectile_ward"));

		ArmorAffixSelection selection = ArmorAffixSelection.select(worn, 2);

		assertEquals(1, selection.count("projectile_ward"));
		assertTrue(selection.active(EquipmentSlot.HEAD).contains("projectile_ward"));
		assertTrue(selection.active(EquipmentSlot.CHEST).isEmpty());
	}

	@Test
	void distinctAffixesOnDifferentSlotsAllRemainActive() {
		Map<EquipmentSlot, List<String>> worn = new EnumMap<>(EquipmentSlot.class);
		worn.put(EquipmentSlot.HEAD, List.of("vital_constellation"));
		worn.put(EquipmentSlot.CHEST, List.of("spellward_prism"));
		worn.put(EquipmentSlot.LEGS, List.of("steady_heart"));

		ArmorAffixSelection selection = ArmorAffixSelection.select(worn, 2);

		assertEquals(1, selection.count("vital_constellation"));
		assertEquals(1, selection.count("spellward_prism"));
		assertEquals(1, selection.count("steady_heart"));
	}
}
