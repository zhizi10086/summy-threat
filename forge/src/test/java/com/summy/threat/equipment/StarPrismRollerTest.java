package com.summy.threat.equipment;

import com.summy.threat.equipment.StarPrismRoller.WeightProvider;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StarPrismRollerTest {
	@Test
	void standardRollPicksOneAffixForEveryCategoryAtomically() {
		PrismRollSettings settings = new PrismRollSettings(100.0D, 0.0D, 0.0D,
				0.0D, 100.0D, 0.0D);

		StarPrismData data = StarPrismRoller.roll(PrismRollMode.STANDARD, settings,
				affix -> 1.0D, new Random(7L)).orElseThrow();

		assertTrue(data.revealed());
		assertTrue(data.armorAffix() != null);
		assertTrue(data.meleeAffix() != null);
		assertTrue(data.rangedAffix() != null);
		assertEquals(StarPrismTier.T1,
				StarPrismAffixes.require(data.armorAffix()).tier());
		assertEquals(StarPrismTier.T1,
				StarPrismAffixes.require(data.meleeAffix()).tier());
		assertEquals(StarPrismTier.T1,
				StarPrismAffixes.require(data.rangedAffix()).tier());
	}

	@Test
	void advancedRollUsesAdvancedTierWeights() {
		PrismRollSettings settings = new PrismRollSettings(100.0D, 0.0D, 0.0D,
				0.0D, 0.0D, 100.0D);

		StarPrismData data = StarPrismRoller.roll(PrismRollMode.ADVANCED, settings,
				affix -> 1.0D, new Random(11L)).orElseThrow();

		assertEquals(StarPrismTier.T3,
				StarPrismAffixes.require(data.armorAffix()).tier());
		assertEquals(StarPrismTier.T3,
				StarPrismAffixes.require(data.meleeAffix()).tier());
		assertEquals(StarPrismTier.T3,
				StarPrismAffixes.require(data.rangedAffix()).tier());
	}

	@Test
	void zeroWeightAndEmptyTierAreExcluded() {
		PrismRollSettings settings = new PrismRollSettings(0.0D, 100.0D, 0.0D,
				0.0D, 0.0D, 0.0D);
		WeightProvider onlyT3Armor = affix -> affix.category() == EquipmentCategory.ARMOR
				&& affix.tier() == StarPrismTier.T3 ? 1.0D : 0.0D;

		Optional<StarPrismData> result = StarPrismRoller.roll(PrismRollMode.STANDARD,
				settings, onlyT3Armor, new Random(3L));

		assertTrue(result.isEmpty());
	}
}
