package com.summy.threat.equipment;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class EquipmentOperationTest {
	@Test
	void operationJsonIdsAreStable() {
		assertEquals(EquipmentOperation.UPGRADE_I, EquipmentOperation.byId("upgrade_i"));
		assertEquals(EquipmentOperation.UPGRADE_II, EquipmentOperation.byId("upgrade_ii"));
		assertEquals(EquipmentOperation.EMBED_PRISM, EquipmentOperation.byId("embed_prism"));
		assertEquals(EquipmentOperation.REFRESH_STANDARD,
				EquipmentOperation.byId("refresh_standard"));
		assertEquals(EquipmentOperation.REFRESH_ADVANCED,
				EquipmentOperation.byId("refresh_advanced"));
		assertEquals(EquipmentOperation.DISASSEMBLE_ALL,
				EquipmentOperation.byId("disassemble_all"));
	}

	@Test
	void unknownOperationIsRejectedAsNull() {
		assertNull(EquipmentOperation.byId("replace_in_place"));
	}
}
