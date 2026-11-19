package com.summy.threat.legendary;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegendaryActionMathTest {
	@Test
	void jadeVisualAnchorFollowsFeetWithoutChangingTheContactVolume() {
		double feet = 8.0D;
		double height = 1.8D;
		double contactTop = LegendaryActionMath.jadeContactAnchorY(feet, height);
		assertEquals(10.15D, contactTop, 1.0E-6D);
		assertEquals(6.15D, LegendaryActionMath.jadeContactMinY(contactTop, 4.0D), 1.0E-6D);
		assertEquals(feet, LegendaryActionMath.jadeVisualAnchorY(contactTop, height), 1.0E-6D);
		assertEquals(feet, LegendaryActionMath.jadeVisualAnchorY(
				LegendaryActionMath.jadeContactAnchorY(feet, 0.6D), 0.6D), 1.0E-6D);
	}

	@Test
	void damageAppliesAttackCurveAndPlayerSideFactor() {
		assertEquals(240.0F, LegendaryActionMath.damage(120.0D, 2.0D, 1.0D, true));
		assertEquals(2_400.0F, LegendaryActionMath.damage(120.0D, 2.0D, 1.0D, false));
	}

	@Test
	void jadeMultiplierDoublesUntilCap() {
		assertEquals(1, LegendaryActionMath.jadeMultiplier(0));
		assertEquals(2, LegendaryActionMath.jadeMultiplier(1));
		assertEquals(4, LegendaryActionMath.jadeMultiplier(2));
		assertEquals(8, LegendaryActionMath.jadeMultiplier(3));
		assertEquals(12, LegendaryActionMath.jadeMultiplier(4));
		assertEquals(12, LegendaryActionMath.jadeMultiplier(20));
	}

	@Test
	void jadeContactVolumeExtendsDownwardFromHeadAnchor() {
		assertEquals(6.0D, LegendaryActionMath.jadeContactMinY(10.0D, 4.0D));
		assertEquals(10.0D, LegendaryActionMath.jadeContactMaxY(10.0D));
	}

	@Test
	void doomClockFirstSectorCentersOnTargetDirection() {
		assertEquals(0.0D, LegendaryActionMath.clockwiseSectorOffset(0), 1.0E-9D);
		assertEquals(Math.PI / 2.0D,
				LegendaryActionMath.clockwiseSectorOffset(1), 1.0E-9D);
	}

	@Test
	void doomClockTelegraphsTheUpcomingSectorAndNeverWrapsAfterTheFourth() {
		int[] bursts = {30, 45, 60, 75};
		for (int sector = 0; sector < bursts.length; sector++) {
			int burst = bursts[sector];
			var warning = DoomClockAction.visualCue(burst - 5);
			assertEquals(sector, warning.sector());
			assertEquals(ThreatVisualEntity.WARNING, warning.phase());
			assertTrue(warning.intensity() > DoomClockAction.visualCue(burst - 6).intensity());
			var impact = DoomClockAction.visualCue(burst);
			assertEquals(sector, impact.sector());
			assertEquals(ThreatVisualEntity.IMPACT, impact.phase());
			assertEquals(sector, DoomClockAction.detonationAt(burst));
			assertEquals(-1, DoomClockAction.detonationAt(burst + 1));
		}
		assertEquals(ThreatVisualEntity.IMPACT, DoomClockAction.visualCue(78).phase());
		assertEquals(0.0F, DoomClockAction.visualCue(79).intensity());
		assertEquals(0.0F, DoomClockAction.visualCue(89).intensity());
	}

	@Test
	void finalWaveKeepsRunningUntilMaximumRadiusIsCleared() {
		assertEquals(78, LegendaryActionMath.waveEndExclusiveAge(45, 18.0D, 0.6D));
	}

	@Test
	void reverseGravityProducesNetUpwardMotionAgainstVanillaGravity() {
		double lift = LegendaryActionMath.reverseGravityLiftVelocity();
		assertTrue(lift > 0.08D);
		assertEquals(5.6D, (lift - 0.08D) * 40.0D, 0.2D);
	}
}
