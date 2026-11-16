package com.summy.threat.equipment;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RangedShotSourceTest {
	@Test
	void tetraWaitsForItsProjectileSpawnEventWhileOtherShotsUseEntityJoin() {
		assertTrue(RangedShotSource.STANDARD.attachOnEntityJoin());
		assertTrue(RangedShotSource.TINKERS_CROSSBOW.attachOnEntityJoin());
		assertFalse(RangedShotSource.TETRA.attachOnEntityJoin());
	}
}
