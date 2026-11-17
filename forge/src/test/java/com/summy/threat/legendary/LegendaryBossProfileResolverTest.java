package com.summy.threat.legendary;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegendaryBossProfileResolverTest {
	@Test
	void resolvesVanillaBossFamilies() {
		assertTrue(LegendaryBossProfileResolver.fromFlags(
				"minecraft:ender_dragon", true, 0, false, true).enderDragonBoss());
		assertTrue(LegendaryBossProfileResolver.fromFlags(
				"minecraft:wither", true, 0, false, false).witherBoss());
		assertTrue(LegendaryBossProfileResolver.fromFlags(
				"minecraft:warden", true, 0, false, false).wardenBoss());

		LegendaryBossProfile generic = LegendaryBossProfileResolver.fromFlags(
				"minecraft:zombie", true, 0, false, false);
		assertTrue(generic.eligible());
		assertFalse(generic.dragonFamily());
	}

	@Test
	void iceFireDragonRequiresStageFourOrFiveAndUntamedState() {
		String ice = "iceandfire:ice_dragon";
		assertFalse(LegendaryBossProfileResolver.fromFlags(
				ice, true, 3, false, true).eligible());
		assertTrue(LegendaryBossProfileResolver.fromFlags(
				ice, true, 4, false, true).dragonFamily());
		assertFalse(LegendaryBossProfileResolver.fromFlags(
				ice, true, 5, true, true).eligible());
	}

	@Test
	void nonBossNeverReceivesLegendaryProfile() {
		assertEquals(LegendaryBossProfile.none(), LegendaryBossProfileResolver.fromFlags(
				"minecraft:ender_dragon", false, 5, false, true));
	}
}
