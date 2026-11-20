package com.summy.threat.legendary;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThreatFieldMotesTest {
	@Test
	void annularSamplesStayOutsideTheSafeCircle() {
		var band = new ThreatFieldMotes.Band(10.0D, 20.0D, 0.0D, Math.PI * 2);
		for (int i = 0; i < ThreatFieldMotes.BUDGET; i++) {
			var point = band.sample(42, i, 17.5F);
			assertTrue(point.horizontalDistance() > 10.0D);
			assertTrue(point.horizontalDistance() < 20.0D);
			assertEquals(point, band.sample(42, i, 17.5F));
		}
	}

	@Test
	void sectorSamplesRespectTheCurrentSectorAcrossAngleWrap() {
		double direction = -3.0D;
		var band = new ThreatFieldMotes.Band(0.0D, 20.0D, direction - Math.PI / 4, Math.PI / 2);
		for (int i = 0; i < ThreatFieldMotes.BUDGET; i++) {
			var point = band.sample(655, i, 201.0F);
			assertTrue(LegendaryVisuals.angularDistance(Math.atan2(point.z, point.x), direction) < Math.PI / 4);
		}
	}

	@Test
	void waveSamplesStayInTheBandAndOutOfTheRotatingGap() {
		var wave = LegendaryWavePattern.DRAGON;
		double base = 0.4D;
		double radius = 13.2D;
		for (int index = 0; index < LegendaryWavePattern.COUNT; index++) {
			double gap = wave.gapCenter(base, index);
			var band = new ThreatFieldMotes.Band(radius - wave.width, radius, gap + wave.gapHalfWidth,
					2 * Math.PI - 2 * wave.gapHalfWidth);
			for (int i = 0; i < ThreatFieldMotes.BUDGET; i++) {
				var point = band.sample(123, i, 8.0F);
				assertTrue(point.horizontalDistance() > radius - wave.width && point.horizontalDistance() < radius);
				assertTrue(wave.inDangerAngle(Math.atan2(point.z, point.x), base, index));
			}
		}
	}
}
