package com.summy.threat.equipment;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RangedWeaponVelocityTest {
	private static final double EPSILON = 1.0E-9D;

	@Test
	void levelOneWithoutCometFletchingUsesOnlyFixedVelocity() {
		assertEquals(1.10D,
				RangedWeaponRuntime.initialVelocityMultiplier(1, false, 15.0D),
				EPSILON);
	}

	@Test
	void levelOneWithCometFletchingUsesConfiguredVelocity() {
		assertEquals(1.10D * 1.15D,
				RangedWeaponRuntime.initialVelocityMultiplier(1, true, 15.0D),
				EPSILON);
	}

	@Test
	void cometFletchingVelocityHonorsNonDefaultConfiguration() {
		assertEquals(1.10D * 1.25D,
				RangedWeaponRuntime.initialVelocityMultiplier(1, true, 25.0D),
				EPSILON);
	}

	@Test
	void unupgradedWeaponStillHonorsCometFletching() {
		assertEquals(1.15D,
				RangedWeaponRuntime.initialVelocityMultiplier(0, true, 15.0D),
				EPSILON);
	}
}
