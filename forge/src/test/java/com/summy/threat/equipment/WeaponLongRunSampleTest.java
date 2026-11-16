package com.summy.threat.equipment;

import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B15 阶段 3 的 600 秒等效采样。
 *
 * <p>这里验证相对持续伤害：固定中性单次伤害和有效命中频率，不计目标生命、
 * 转阶段或玩家停手；这些属于 A34 的绝对 TTK 门禁。</p>
 */
class WeaponLongRunSampleTest {
	private static final double BASE_DAMAGE = 18.0D;
	private static final double MELEE_ARMOR_MULTIPLIER = 0.10D;
	private static final double BOSS_MAGIC_MULTIPLIER = 0.40D;
	private static final int SAMPLE_SECONDS = 600;
	private static final UUID TARGET = UUID.fromString(
			"6f5c1a2e-9300-4a0b-9d1e-000000000a01");

	@Test
	void sixHundredSecondSamplesRemainBoundedAndConditionalBuildsWin() {
		Sample stableMelee = sampleStableMelee();
		Sample conditionalMelee = sampleConditionalMelee();
		Sample stableRanged = sampleStableRanged();
		Sample conditionalRanged = sampleConditionalRanged();
		Sample magicMelee = sampleMagicMelee();
		Sample magicRanged = sampleMagicRanged();

		for (Sample sample : new Sample[] {
				stableMelee, conditionalMelee, stableRanged, conditionalRanged,
				magicMelee, magicRanged
		}) {
			System.out.printf(Locale.ROOT,
					"[B15-LONG] %s effectiveHits=%d triggers=%d actual=%.4fx expected=%.4fx%n",
					sample.name(), sample.effectiveHits(), sample.triggers(),
					sample.actualMultiplier(), sample.expectedMultiplier());
			assertTrue(sample.actualMultiplier() > 1.0D, sample.name());
			double relativeError = Math.abs(
					sample.actualMultiplier() - sample.expectedMultiplier())
					/ sample.expectedMultiplier();
			assertEquals(0.0D, relativeError, sample.tolerance(), sample.name());
		}

		assertTrue(conditionalMelee.actualMultiplier()
				> stableMelee.actualMultiplier());
		assertTrue(conditionalRanged.actualMultiplier()
				> stableRanged.actualMultiplier());
		assertTrue(magicMelee.actualMultiplier() > 1.0D);
		assertTrue(magicRanged.actualMultiplier() > 1.0D);
	}

	private static Sample sampleStableMelee() {
		int hits = (int) Math.round(SAMPLE_SECONDS * 1.54D);
		double upgradedBase = BASE_DAMAGE * 1.10D * MELEE_ARMOR_MULTIPLIER;
		double damage = upgradedBase * (26.5D / BASE_DAMAGE) * 1.35D * hits;
		return new Sample("melee-stable", hits, 0, damage,
				upgradedBase * hits, 1.9875D, 1.0E-9D);
	}

	private static Sample sampleConditionalMelee() {
		int hits = (int) Math.round(SAMPLE_SECONDS * 2.80D);
		WeaponRuntimeState state = new WeaponRuntimeState();
		Random random = new Random(0xB15_600L);
		double damage = 0.0D;
		double pursuitFactorSum = 0.0D;
		int triggers = 0;
		long now = 20L;
		for (int i = 0; i < hits; i++) {
			double pursuit = state.pursuitMultiplier(
					TARGET, "melee", now, 60, 6.0D);
			double pursuitFactor = 1.0D + pursuit;
			pursuitFactorSum += pursuitFactor;
			double amount = BASE_DAMAGE * 1.10D * pursuitFactor;
			if (random.nextDouble() < 0.10D) {
				amount *= 7.0D;
				triggers++;
			}
			damage += amount * MELEE_ARMOR_MULTIPLIER;
			state.pursuitHit(TARGET, "melee", now, 5, 60);
			now += 7L;
		}
		double baseline = BASE_DAMAGE * 1.10D * MELEE_ARMOR_MULTIPLIER * hits;
		double expected = 1.60D * pursuitFactorSum / hits;
		return new Sample("melee-terminal-pursuit", hits, triggers, damage,
				baseline, expected, 0.05D);
	}

	private static Sample sampleStableRanged() {
		int shots = SAMPLE_SECONDS;
		double upgradedBase = BASE_DAMAGE * 1.10D * MELEE_ARMOR_MULTIPLIER;
		double damage = upgradedBase * 1.30D * 1.60D * shots;
		return new Sample("ranged-stable", shots, 0, damage,
				upgradedBase * shots, 2.08D, 1.0E-9D);
	}

	private static Sample sampleConditionalRanged() {
		int shots = SAMPLE_SECONDS * 2;
		WeaponRuntimeState state = new WeaponRuntimeState();
		Random random = new Random(0xB15_601L);
		double damage = 0.0D;
		double pursuitFactorSum = 0.0D;
		int triggers = 0;
		long now = 40L;
		for (int i = 0; i < shots; i++) {
			double pursuit = state.pursuitMultiplier(
					TARGET, "bow", now, 100, 15.0D);
			double pursuitFactor = 1.0D + pursuit;
			pursuitFactorSum += pursuitFactor;
			double amount = BASE_DAMAGE * 1.10D * pursuitFactor;
			if (random.nextDouble() < 0.075D) {
				amount *= 11.0D;
				triggers++;
			}
			damage += amount * MELEE_ARMOR_MULTIPLIER;
			state.pursuitHit(TARGET, "bow", now, 5, 100);
			now += 10L;
		}
		double baseline = BASE_DAMAGE * 1.10D * MELEE_ARMOR_MULTIPLIER * shots;
		double expected = 1.75D * pursuitFactorSum / shots;
		return new Sample("ranged-terminal-hunt", shots, triggers, damage,
				baseline, expected, 0.04D);
	}

	private static Sample sampleMagicMelee() {
		int hits = (int) Math.round(SAMPLE_SECONDS * 1.54D);
		double physicalPerHit = BASE_DAMAGE * 1.10D * MELEE_ARMOR_MULTIPLIER;
		double arcanePerHit = physicalPerHit * 0.20D * BOSS_MAGIC_MULTIPLIER;
		double starfirePerHit = physicalPerHit * 1.00D * BOSS_MAGIC_MULTIPLIER / 4.0D;
		double damage = (physicalPerHit + arcanePerHit + starfirePerHit) * hits;
		double baseline = physicalPerHit * hits;
		return new Sample("melee-arcane-starfire", hits, hits / 4, damage,
				baseline, 1.0D + 0.08D + 0.10D, 1.0E-9D);
	}

	private static Sample sampleMagicRanged() {
		int shots = SAMPLE_SECONDS;
		double physicalPerShot = BASE_DAMAGE * 1.10D * MELEE_ARMOR_MULTIPLIER;
		double arcanePerShot = physicalPerShot * 0.20D * BOSS_MAGIC_MULTIPLIER;
		double starfallPerShot =
				physicalPerShot * 0.75D * BOSS_MAGIC_MULTIPLIER / 3.0D;
		double damage = (physicalPerShot + arcanePerShot + starfallPerShot) * shots;
		double baseline = physicalPerShot * shots;
		return new Sample("ranged-arcane-starfall", shots, shots / 3, damage,
				baseline, 1.0D + 0.08D + 0.10D, 1.0E-9D);
	}

	private record Sample(String name, int effectiveHits, int triggers,
			double damage, double baselineDamage, double expectedMultiplier,
			double tolerance) {
		private double actualMultiplier() {
			return damage / baselineDamage;
		}
	}
}
