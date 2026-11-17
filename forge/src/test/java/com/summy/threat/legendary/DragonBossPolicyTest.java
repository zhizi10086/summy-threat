package com.summy.threat.legendary;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DragonBossPolicyTest {
	@Test
	void onlyUntamedStageFourOrFiveIceFireDragonsAreLegendaryDragonBosses() {
		String fire = "iceandfire:fire_dragon";
		assertFalse(DragonBossPolicy.isEligibleDragonBoss(fire, 3, false, true));
		assertTrue(DragonBossPolicy.isEligibleDragonBoss(fire, 4, false, true));
		assertTrue(DragonBossPolicy.isEligibleDragonBoss(fire, 5, false, true));
		assertFalse(DragonBossPolicy.isEligibleDragonBoss(fire, 5, true, true));
		assertFalse(DragonBossPolicy.isEligibleDragonBoss(fire, 5, false, false));
	}

	@Test
	void enderDragonIsAlwaysADragonBossWhenBossClassificationSucceeds() {
		assertTrue(DragonBossPolicy.isEligibleDragonBoss(
				"minecraft:ender_dragon", 0, false, true));
		assertFalse(DragonBossPolicy.isEligibleDragonBoss(
				"minecraft:ender_dragon", 0, false, false));
	}
}
