package com.summy.threat.pcf;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CdfRiseTest {
	@Test
	void combinedBonusCannotOvershootTargetOrTwenty() {
		assertEquals(13.0D, CdfManager.rise(4.0D, 13.0D, 2.0D));
		assertEquals(20.0D, CdfManager.rise(12.0D, 20.0D, 2.0D));
	}

	@Test
	void occupiedChunkNeverLosesProgress() {
		assertEquals(12.0D, CdfManager.rise(12.0D, 3.0D, 0.5D));
		assertEquals(12.0D, CdfManager.rise(12.0D, 15.0D, -0.5D));
	}
}
