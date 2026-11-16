package com.summy.threat.equipment;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeaponRuntimeStateTest {
	private static final UUID TARGET_A = UUID.fromString(
			"6f5c1a2e-9100-4a0b-9d1e-000000000a01");
	private static final UUID TARGET_B = UUID.fromString(
			"6f5c1a2e-9100-4a0b-9d1e-000000000b01");

	@Test
	void sharedMarkOnlyBenefitsLaterHitsAndTransfers() {
		WeaponRuntimeState state = new WeaponRuntimeState();

		assertFalse(state.sharedMarkActive(TARGET_A, 20L));
		state.markHit(TARGET_A, 20L, 80);
		assertTrue(state.sharedMarkActive(TARGET_A, 99L));
		assertFalse(state.sharedMarkActive(TARGET_B, 99L));

		state.markHit(TARGET_B, 30L, 80);
		assertFalse(state.sharedMarkActive(TARGET_A, 31L));
		assertTrue(state.sharedMarkActive(TARGET_B, 109L));
		assertFalse(state.sharedMarkActive(TARGET_B, 110L));
	}

	@Test
	void pursuitFirstHitStacksWithoutBenefitAndResetsOnWeaponChange() {
		WeaponRuntimeState state = new WeaponRuntimeState();

		assertEquals(0.0D, state.pursuitMultiplier(TARGET_A, "sword-a", 20L, 60), 1.0E-9D);
		state.pursuitHit(TARGET_A, "sword-a", 20L, 5, 60);
		assertEquals(0.06D, state.pursuitMultiplier(TARGET_A, "sword-a", 21L, 60), 1.0E-9D);

		state.pursuitHit(TARGET_A, "sword-a", 21L, 5, 60);
		assertEquals(0.12D, state.pursuitMultiplier(TARGET_A, "sword-a", 22L, 60), 1.0E-9D);
		assertEquals(0.0D, state.pursuitMultiplier(TARGET_B, "sword-a", 22L, 60), 1.0E-9D);

		state.pursuitHit(TARGET_B, "sword-a", 22L, 5, 60);
		assertEquals(0.0D, state.pursuitMultiplier(TARGET_B, "sword-b", 23L, 60), 1.0E-9D);
	}

	@Test
	void pursuitStacksCapAndExpireAfterIdleTicks() {
		WeaponRuntimeState state = new WeaponRuntimeState();
		long now = 100L;
		for (int i = 0; i < 8; i++) {
			state.pursuitHit(TARGET_A, "sword-a", now++, 5, 60);
		}

		assertEquals(0.30D, state.pursuitMultiplier(TARGET_A, "sword-a", now, 60), 1.0E-9D);
		assertEquals(0.0D, state.pursuitMultiplier(TARGET_A, "sword-a", now + 60L, 60),
				1.0E-9D);
	}

	@Test
	void pursuitUsesConfiguredResetTicks() {
		WeaponRuntimeState state = new WeaponRuntimeState();
		state.pursuitHit(TARGET_A, "sword-a", 20L, 5, 40);

		assertEquals(0.06D, state.pursuitMultiplier(TARGET_A, "sword-a", 59L, 40),
				1.0E-9D);
		assertEquals(0.0D, state.pursuitMultiplier(TARGET_A, "sword-a", 60L, 40),
				1.0E-9D);
	}

	@Test
	void pursuitDamageUsesConfiguredPercentPerStack() {
		WeaponRuntimeState state = new WeaponRuntimeState();
		state.pursuitHit(TARGET_A, "sword-a", 20L, 2, 60);
		state.pursuitHit(TARGET_A, "sword-a", 21L, 2, 60);

		assertEquals(0.16D,
				state.pursuitMultiplier(TARGET_A, "sword-a", 22L, 60, 8.0D), 1.0E-9D);
		assertEquals(2, state.pursuitStacks());
	}

	@Test
	void activePursuitStacksExpireWithoutMoreHits() {
		WeaponRuntimeState state = new WeaponRuntimeState();
		state.pursuitHit(TARGET_A, "sword-a", 20L, 5, 60);

		assertEquals(1, state.activePursuitStacks(40L, 60));
		assertEquals(0, state.activePursuitStacks(80L, 60));
	}

	@Test
	void starfireAndStarfallPersistAcrossWeaponChangesUntilSessionReset() {
		WeaponRuntimeState state = new WeaponRuntimeState();
		assertFalse(state.starfireHit(4));
		assertFalse(state.starfireHit(4));
		assertFalse(state.starfireHit(4));
		state.changeMainhand("bow-b");
		assertTrue(state.starfireHit(4));

		assertFalse(state.starfallHit(3));
		assertFalse(state.starfallHit(3));
		state.changeMainhand("sword-b");
		assertTrue(state.starfallHit(3));

		state.clearSession();
		assertFalse(state.starfireHit(4));
		assertFalse(state.starfallHit(3));
	}

	@Test
	void rangedWeaponChangeResetsPursuit() {
		WeaponRuntimeState state = new WeaponRuntimeState();
		state.changeRangedMainhand("bow-a");
		state.pursuitHit(TARGET_A, "bow-a", 20L, 5, 100);

		assertEquals(0.15D,
				state.pursuitMultiplier(TARGET_A, "bow-a", 21L, 100, 15.0D), 1.0E-9D);
		state.changeRangedMainhand("bow-b");
		assertEquals(0.0D,
				state.pursuitMultiplier(TARGET_A, "bow-b", 22L, 100, 15.0D), 1.0E-9D);
	}
}
