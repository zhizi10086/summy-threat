package com.summy.threat.equipment;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PrismProcessingModeTest {
	@Test
	void missingDataRequestsStandardReveal() {
		assertEquals(PrismProcessingMode.REVEAL_STANDARD,
				PrismProcessingMode.resolve(Optional.empty()));
	}

	@Test
	void pendingRefreshOverridesInitialRevealMode() {
		assertEquals(PrismProcessingMode.REFRESH_ADVANCED,
				PrismProcessingMode.resolve(Optional.of(new StarPrismData(1, false,
						"advanced", null, null, null))));
	}

	@Test
	void revealedCleanPrismNeedsNoProcessing() {
		assertEquals(PrismProcessingMode.NONE,
				PrismProcessingMode.resolve(Optional.of(new StarPrismData(1, true,
						null, "astral_plating", "prism_edge", "starlit_bowstring"))));
	}
}
