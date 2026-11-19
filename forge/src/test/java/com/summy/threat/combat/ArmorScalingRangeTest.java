package com.summy.threat.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ArmorScalingRangeTest {
	@Test
	void freeTierUsesEntireRangeWithoutOverridingReductionCap() {
		double cap = 100.0D;
		assertEquals(25.5D, ArmorDamageFormula.compressEffectiveArmor(50.0D, cap, 0.5D));
		assertEquals(50.0D, ArmorDamageFormula.compressEffectiveArmor(50.0D, cap, 1.0D));
		assertEquals(74.5D, ArmorDamageFormula.compressEffectiveArmor(50.0D, cap, 1.5D));
		assertEquals(99.0D, ArmorDamageFormula.compressEffectiveArmor(50.0D, cap, 2.0D));
		assertEquals(0.9D, ArmorDamageFormula.monsterReduction(
				ArmorDamageFormula.compressEffectiveArmor(80.0D, cap, 2.0D), 0.9D, cap));
	}
}
