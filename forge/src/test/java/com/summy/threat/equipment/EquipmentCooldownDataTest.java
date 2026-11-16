package com.summy.threat.equipment;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EquipmentCooldownDataTest {
	@Test
	void cooldownStoresAbsoluteExpiryAndReportsRemaining() {
		CompoundTag data = new CompoundTag();
		EquipmentCooldownData.set(data, "last_light", 100L);

		assertEquals(50L, EquipmentCooldownData.remaining(data, "last_light", 50L));
		assertEquals(0L, EquipmentCooldownData.remaining(data, "last_light", 100L));
		assertFalse(EquipmentCooldownData.ready(data, "last_light", 99L));
		assertTrue(EquipmentCooldownData.ready(data, "last_light", 100L));
	}

	@Test
	void unknownCooldownIsReadyAndCopyPreservesValues() {
		CompoundTag data = new CompoundTag();
		assertTrue(EquipmentCooldownData.ready(data, "phase_anchor", 0L));
		EquipmentCooldownData.set(data, "phase_anchor", 400L);
		CompoundTag copy = data.copy();
		assertEquals(400L, copy.getCompound(EquipmentCooldownData.ROOT)
				.getLong("phase_anchor"));
	}
}
