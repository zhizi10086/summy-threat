package com.summy.threat.equipment;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ArmorDurabilityProtectionTest {
	@Test
	void levelTwoArmorCapsLargeLossWithinConfiguredBounds() {
		assertEquals(6, ArmorDurabilityProtection.capLoss(1000, 20, 2, 100, 5, 6));
		assertEquals(5, ArmorDurabilityProtection.capLoss(100, 10, 2, 100, 5, 6));
	}

	@Test
	void smallerLossAndLevelOneRemainUnchanged() {
		assertEquals(2, ArmorDurabilityProtection.capLoss(1000, 2, 2, 100, 5, 6));
		assertEquals(20, ArmorDurabilityProtection.capLoss(1000, 20, 1, 100, 5, 6));
	}

	@Test
	void onlyLevelTwoArmorHeldByServerPlayerUsesCap() {
		assertEquals(6, ArmorDurabilityProtection.capForState(
				1000, 20, true, true, 2, 100, 5, 6));
		assertEquals(20, ArmorDurabilityProtection.capForState(
				1000, 20, false, true, 2, 100, 5, 6));
		assertEquals(20, ArmorDurabilityProtection.capForState(
				1000, 20, true, false, 2, 100, 5, 6));
	}
}
