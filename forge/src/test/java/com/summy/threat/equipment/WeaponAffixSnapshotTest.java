package com.summy.threat.equipment;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeaponAffixSnapshotTest {
	@Test
	void snapshotReadsBothSlotsInOrderForMatchingCategory() {
		CompoundTag equipment = EximusEquipmentData.withUpgradeLevel(new CompoundTag(), 2);
		equipment = EximusEquipmentData.withPrism(equipment, 0,
				prism("armor", "prism_edge", "starlit_bowstring", true, null));
		equipment = EximusEquipmentData.withPrism(equipment, 1,
				prism("armor", "terminal_star", "terminal_star_shot", true, null));

		WeaponAffixSnapshot melee = WeaponAffixSnapshot.read(equipment,
				EquipmentCategory.MELEE);

		assertEquals(2, melee.level());
		assertEquals(java.util.List.of("prism_edge", "terminal_star"), melee.affixes());
		assertTrue(melee.has("prism_edge"));
		assertFalse(melee.has("starlit_bowstring"));
	}

	@Test
	void snapshotIgnoresUnrevealedPendingAndDamagedData() {
		CompoundTag equipment = EximusEquipmentData.withUpgradeLevel(new CompoundTag(), 2);
		equipment = EximusEquipmentData.withPrism(equipment, 0,
				prism("armor", "prism_edge", "starlit_bowstring", false, null));
		equipment = EximusEquipmentData.withPrism(equipment, 1,
				prism("armor", "starfire_cycle", "starfall_cycle_arrow", true, "advanced"));

		WeaponAffixSnapshot snapshot = WeaponAffixSnapshot.read(equipment,
				EquipmentCategory.MELEE);

		assertEquals(0, snapshot.affixes().size());
		CompoundTag damaged = equipment.copy();
		damaged.getCompound(EximusEquipmentData.EQUIPMENT_ROOT).putInt("schema", 99);
		assertEquals(WeaponAffixSnapshot.empty(),
				WeaponAffixSnapshot.read(damaged, EquipmentCategory.MELEE));
	}

	private static CompoundTag prism(String armor, String melee, String ranged,
			boolean revealed, String pending) {
		CompoundTag tag = new CompoundTag();
		StarPrismData.write(tag, new StarPrismData(StarPrismData.SCHEMA,
				revealed, pending, armor, melee, ranged));
		return tag;
	}
}
