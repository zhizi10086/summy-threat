package com.summy.threat.legendary;

import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegendaryActionPoolTest {
	@Test
	void bossProfilesExposeExpectedPools() {
		assertTrue(LegendaryActionPool.eligible(LegendaryBossProfile.none()).isEmpty());
		assertEquals(7, LegendaryActionPool.eligible(LegendaryBossProfile.genericBoss()).size());
		assertEquals(10, LegendaryActionPool.eligible(LegendaryBossProfile.enderDragon()).size());
		assertEquals(8, LegendaryActionPool.eligible(LegendaryBossProfile.wither()).size());
		assertEquals(8, LegendaryActionPool.eligible(LegendaryBossProfile.warden()).size());
		assertEquals(9, LegendaryActionPool.eligible(LegendaryBossProfile.dragonBoss()).size());
	}

	@Test
	void exclusiveAndDragonActionsOnlyAppearInAllowedPools() {
		List<LegendaryActionId> generic = LegendaryActionPool.eligible(
				LegendaryBossProfile.genericBoss());
		assertFalse(generic.contains(LegendaryActionId.DRAGONFALL_EDICT));
		assertFalse(generic.contains(LegendaryActionId.FRIGHTFUL_PRESENCE));

		List<LegendaryActionId> ender = LegendaryActionPool.eligible(
				LegendaryBossProfile.enderDragon());
		assertTrue(ender.contains(LegendaryActionId.DRAGONFALL_EDICT));
		assertTrue(ender.contains(LegendaryActionId.FRIGHTFUL_PRESENCE));
		assertTrue(ender.contains(LegendaryActionId.DRAGON_LAIR_PULSE));

		List<LegendaryActionId> dragon = LegendaryActionPool.eligible(
				LegendaryBossProfile.dragonBoss());
		assertFalse(dragon.contains(LegendaryActionId.DRAGONFALL_EDICT));
		assertTrue(dragon.contains(LegendaryActionId.FRIGHTFUL_PRESENCE));
		assertTrue(dragon.contains(LegendaryActionId.DRAGON_LAIR_PULSE));
	}

	@Test
	void weightedRollAlwaysReturnsEligibleAction() {
		LegendaryBossProfile profile = LegendaryBossProfile.enderDragon();
		List<LegendaryActionId> eligible = LegendaryActionPool.eligible(profile);
		RandomSource random = RandomSource.create(42L);
		for (int i = 0; i < 100; i++) {
			assertTrue(eligible.contains(LegendaryActionPool.roll(profile, random)));
		}
	}
}
