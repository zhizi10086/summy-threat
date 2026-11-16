package com.summy.threat.equipment;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WeaponDamageMathTest {
	@Test
	void concussiveDamageScalesLinearlyToThirtyFivePercent() {
		assertEquals(1.0D, WeaponDamageMath.concussiveMultiplier(0.0D, 35.0D), 1.0E-9D);
		assertEquals(1.175D, WeaponDamageMath.concussiveMultiplier(0.5D, 35.0D),
				1.0E-9D);
		assertEquals(1.35D, WeaponDamageMath.concussiveMultiplier(1.0D, 35.0D),
				1.0E-9D);
		assertEquals(1.35D, WeaponDamageMath.concussiveMultiplier(2.0D, 35.0D),
				1.0E-9D);
	}

	@Test
	void independentPercentBonusesMultiplyInsteadOfAdding() {
		assertEquals(1.43D, WeaponDamageMath.multiplyBonuses(10.0D, 30.0D),
				1.0E-9D);
		assertEquals(2.574D, WeaponDamageMath.multiplyBonuses(10.0D, 30.0D, 80.0D),
				1.0E-9D);
	}

	@Test
	void armorReductionPointsApplyToFinalReductionOnly() {
		assertEquals(0.85D, WeaponDamageMath.reduceArmorReduction(0.90D, 5.0D),
				1.0E-9D);
		assertEquals(0.84D, WeaponDamageMath.reduceArmorReduction(0.90D, 6.0D),
				1.0E-9D);
		assertEquals(0.0D, WeaponDamageMath.reduceArmorReduction(0.03D, 5.0D),
				1.0E-9D);
	}
}
