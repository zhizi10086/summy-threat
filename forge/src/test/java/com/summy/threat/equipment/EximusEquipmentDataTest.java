package com.summy.threat.equipment;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EximusEquipmentDataTest {
	@Test
	void upgradeOneCreatesTwoSlotsAndPreservesUnknownRootData() {
		CompoundTag input = new CompoundTag();
		input.putString("ExternalData", "keep-me");

		CompoundTag upgraded = EximusEquipmentData.withUpgradeLevel(input, 1);

		assertEquals(1, EximusEquipmentData.upgradeLevel(upgraded));
		assertEquals(2, EximusEquipmentData.prismCount(upgraded));
		assertEquals("keep-me", upgraded.getString("ExternalData"));
		assertTrue(EximusEquipmentData.prismTag(upgraded, 0).isEmpty());
		assertTrue(EximusEquipmentData.prismTag(upgraded, 1).isEmpty());
	}

	@Test
	void upgradeTwoPreservesAnExistingFirstSlotPrism() {
		CompoundTag input = new CompoundTag();
		CompoundTag prism = new CompoundTag();
		prism.putString("Marker", "slot-one");
		CompoundTag levelOne = EximusEquipmentData.withPrism(
				EximusEquipmentData.withUpgradeLevel(input, 1), 0, prism);

		CompoundTag levelTwo = EximusEquipmentData.withUpgradeLevel(levelOne, 2);

		assertEquals(2, EximusEquipmentData.upgradeLevel(levelTwo));
		assertEquals("slot-one", EximusEquipmentData.prismTag(levelTwo, 0).getString("Marker"));
		assertTrue(EximusEquipmentData.prismTag(levelTwo, 1).isEmpty());
	}

	@Test
	void firstEmptySlotUsesLowestIndex() {
		CompoundTag equipment = EximusEquipmentData.withUpgradeLevel(new CompoundTag(), 2);
		CompoundTag slotTwo = new CompoundTag();
		slotTwo.putString("Marker", "slot-two");

		CompoundTag withSecond = EximusEquipmentData.withPrism(equipment, 1, slotTwo);

		assertEquals(0, EximusEquipmentData.firstEmptySlot(withSecond));
		CompoundTag slotOne = new CompoundTag();
		slotOne.putString("Marker", "slot-one");
		CompoundTag withFirst = EximusEquipmentData.withPrism(withSecond, 0, slotOne);
		assertEquals(-1, EximusEquipmentData.firstEmptySlot(withFirst));
	}

	@Test
	void damagedSchemaIsRejectedWithoutOverwritingData() {
		CompoundTag equipment = EximusEquipmentData.withUpgradeLevel(new CompoundTag(), 2);
		CompoundTag root = equipment.getCompound(EximusEquipmentData.EQUIPMENT_ROOT);
		root.putInt("schema", 999);

		assertFalse(EximusEquipmentData.isValid(equipment));
		assertEquals(999, equipment.getCompound(EximusEquipmentData.EQUIPMENT_ROOT)
				.getInt("schema"));
	}

	@Test
	void modularToolRootsRemainUntouchedAcrossStOperations() {
		CompoundTag input = new CompoundTag();
		input.putString("tic_materials", "tinkers:iron");
		input.putString("tic_stats", "keep-stats");
		input.putString("sword/blade", "tetra:sword/short_blade");
		input.putString("sword/blade_material", "tetra:iron");
		input.putString("affix_data", "apotheosis-data");

		CompoundTag prism = new CompoundTag();
		prism.putString("Marker", "prism");
		CompoundTag output = EximusEquipmentData.withPrism(
				EximusEquipmentData.withUpgradeLevel(input, 2), 0, prism);

		assertEquals("tinkers:iron", output.getString("tic_materials"));
		assertEquals("keep-stats", output.getString("tic_stats"));
		assertEquals("tetra:sword/short_blade", output.getString("sword/blade"));
		assertEquals("tetra:iron", output.getString("sword/blade_material"));
		assertEquals("apotheosis-data", output.getString("affix_data"));
		assertEquals("prism", EximusEquipmentData.prismTag(output, 0)
				.getString("Marker"));
	}
}
