package com.summy.threat.equipment;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RangedShotStateTest {
	@Test
	void eachOnceEffectIsClaimedOnlyForFirstEffectiveHit() {
		RangedShotState state = new RangedShotState(
				UUID.fromString("6f5c1a2e-9200-4a0b-9d1e-000000000a01"),
				true, true, true, 100L);

		assertTrue(state.claim("sidereal_aim"));
		assertFalse(state.claim("sidereal_aim"));
		assertTrue(state.claim("terminal_star_shot"));
		assertFalse(state.claim("terminal_star_shot"));
		assertTrue(state.claim("mark"));
		assertFalse(state.claim("mark"));
		assertTrue(state.claim("starfall_cycle"));
		assertFalse(state.claim("starfall_cycle"));
	}

	@Test
	void actualHealthAndEnemyHitClaimsRemainIndependent() {
		RangedShotState state = new RangedShotState(
				UUID.fromString("6f5c1a2e-9200-4a0b-9d1e-000000000a02"),
				false, false, false, 100L);

		assertTrue(state.claim("starlight_dew"));
		assertTrue(state.claim("void_quiver"));
		assertFalse(state.claim("starlight_dew"));
		assertFalse(state.claim("void_quiver"));
	}

	@Test
	void stateExpiresUsingLastActivity() {
		RangedShotState state = new RangedShotState(
				UUID.fromString("6f5c1a2e-9200-4a0b-9d1e-000000000a03"),
				false, false, false, 100L);

		state.touch(500L);
		assertFalse(state.expired(6499L, 6000L));
		assertTrue(state.expired(6500L, 6000L));
	}
}
