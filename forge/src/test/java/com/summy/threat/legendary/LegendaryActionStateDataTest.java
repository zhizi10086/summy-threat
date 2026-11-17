package com.summy.threat.legendary;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegendaryActionStateDataTest {
	@Test
	void selectionIsPersistentAndDoesNotReroll() {
		CompoundTag data = new CompoundTag();
		LegendaryActionId first = LegendaryActionStateData.ensureSelection(
				data, LegendaryBossProfile.enderDragon(), RandomSource.create(7L));
		LegendaryActionId second = LegendaryActionStateData.ensureSelection(
				data, LegendaryBossProfile.enderDragon(), RandomSource.create(999L));

		assertEquals(first, second);
		assertEquals(first, LegendaryActionStateData.selection(data));
	}

	@Test
	void invalidProfileClearsSelectionAndCooldown() {
		CompoundTag data = new CompoundTag();
		LegendaryActionStateData.setSelection(data, LegendaryActionId.JADE_LIGHT);
		LegendaryActionStateData.markStarted(data, 100L);

		assertNull(LegendaryActionStateData.ensureSelection(
				data, LegendaryBossProfile.none(), RandomSource.create(1L)));
		assertNull(LegendaryActionStateData.selection(data));
		assertTrue(LegendaryActionStateData.ready(data, 0L));
	}

	@Test
	void corruptSelectionRerollsWithinProfile() {
		CompoundTag data = new CompoundTag();
		data.putString(LegendaryActionStateData.TAG_ACTION, "missing_action");

		LegendaryActionId selected = LegendaryActionStateData.ensureSelection(
				data, LegendaryBossProfile.wither(), RandomSource.create(3L));

		assertTrue(LegendaryActionPool.eligible(LegendaryBossProfile.wither()).contains(selected));
	}

	@Test
	void cooldownUsesAbsoluteExpiry() {
		CompoundTag data = new CompoundTag();
		assertTrue(LegendaryActionStateData.ready(data, 0L));
		LegendaryActionStateData.markStarted(data, 100L);
		assertFalse(LegendaryActionStateData.ready(data, 699L));
		assertTrue(LegendaryActionStateData.ready(data, 700L));
	}
}
