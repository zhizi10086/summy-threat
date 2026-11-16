package com.summy.threat.equipment;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StarPrismConfigTest {
	@Test
	void everyRegisteredAffixHasOneConfigWeight() {
		assertEquals(StarPrismAffixes.all().size(),
				com.summy.threat.Config.STAR_PRISM_AFFIX_WEIGHTS.length);
		for (StarPrismAffix affix : StarPrismAffixes.all()) {
			assertEquals(1.0D, StarPrismConfig.weight(affix.id()), 1.0E-9D);
		}
	}

	@Test
	void armorDefaultsMatchFinalDesign() {
		assertEquals(2, StarPrismConfig.intValue("armor.basic_stack_limit"));
		assertEquals(6.0D,
				StarPrismConfig.doubleValue("armor.astral_plating.armor_value_per_stack"),
				1.0E-9D);
		assertEquals(35.0D,
				StarPrismConfig.doubleValue("armor.steady_heart.reduction_percent"),
				1.0E-9D);
	}

	@Test
	void standardAndAdvancedDefaultsAreDistinct() {
		PrismRollSettings settings = StarPrismConfig.rollSettings();
		assertTrue(settings.standardT1() > settings.standardT2());
		assertTrue(settings.advancedT2() > settings.advancedT1());
	}
}
