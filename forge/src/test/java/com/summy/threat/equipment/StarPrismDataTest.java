package com.summy.threat.equipment;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StarPrismDataTest {
	@Test
	void prismDataStoresAllThreeCategoriesWithoutDroppingUnknownData() {
		CompoundTag prism = new CompoundTag();
		prism.putString("ExternalData", "keep-me");

		StarPrismData.write(prism, new StarPrismData(1, true, null,
				"astral_plating", "prism_edge", "starlit_bowstring"));

		StarPrismData data = StarPrismData.read(prism).orElseThrow();
		assertTrue(data.revealed());
		assertEquals("astral_plating", data.affix(EquipmentCategory.ARMOR));
		assertEquals("prism_edge", data.affix(EquipmentCategory.MELEE));
		assertEquals("starlit_bowstring", data.affix(EquipmentCategory.RANGED));
		assertEquals("keep-me", prism.getString("ExternalData"));
	}

	@Test
	void pendingRefreshSurvivesReadWriteRoundTrip() {
		CompoundTag prism = new CompoundTag();
		StarPrismData original = new StarPrismData(1, false, "advanced",
				null, null, null);

		StarPrismData.write(prism, original);
		StarPrismData decoded = StarPrismData.read(prism).orElseThrow();

		assertEquals("advanced", decoded.pendingRefresh());
		assertFalse(decoded.revealed());
	}
}
