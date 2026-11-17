package com.summy.threat.legendary;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegendaryTestTrackerTest {
	@Test
	void samePlayerCannotRunDuplicateActionId() {
		UUID player = UUID.randomUUID();
		LegendaryTestTracker tracker = new LegendaryTestTracker();

		assertTrue(tracker.begin(player, LegendaryActionId.JADE_LIGHT));
		assertFalse(tracker.begin(player, LegendaryActionId.JADE_LIGHT));
		assertTrue(tracker.begin(player, LegendaryActionId.MIRROR_STEP));
	}

	@Test
	void finishAndPlayerClearReleaseOnlyMatchingInstances() {
		UUID player = UUID.randomUUID();
		LegendaryTestTracker tracker = new LegendaryTestTracker();
		tracker.begin(player, LegendaryActionId.JADE_LIGHT);
		tracker.begin(player, LegendaryActionId.MIRROR_STEP);

		tracker.finish(player, LegendaryActionId.JADE_LIGHT);
		assertTrue(tracker.begin(player, LegendaryActionId.JADE_LIGHT));
		assertFalse(tracker.begin(player, LegendaryActionId.MIRROR_STEP));

		tracker.clear(player);
		assertTrue(tracker.begin(player, LegendaryActionId.MIRROR_STEP));
	}
}
