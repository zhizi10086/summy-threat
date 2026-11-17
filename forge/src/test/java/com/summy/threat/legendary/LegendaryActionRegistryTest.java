package com.summy.threat.legendary;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LegendaryActionRegistryTest {
	@Test
	void catalogContainsConfirmedDistributionAndWeights() {
		assertEquals(12, LegendaryActionId.values().length);
		assertEquals(7, count(LegendaryActionId.Pool.GENERIC));
		assertEquals(3, count(LegendaryActionId.Pool.BOSS_EXCLUSIVE));
		assertEquals(2, count(LegendaryActionId.Pool.DRAGON_EXCLUSIVE));
		assertEquals(1.0D, LegendaryActionId.JADE_LIGHT.weight());
		assertEquals(1.5D, LegendaryActionId.DRAGONFALL_EDICT.weight());
		assertEquals(1.5D, LegendaryActionId.FRIGHTFUL_PRESENCE.weight());
	}

	@Test
	void idsAreUniqueAndParseCaseInsensitively() {
		assertEquals(12, Arrays.stream(LegendaryActionId.values())
				.map(LegendaryActionId::id).distinct().count());
		assertEquals(LegendaryActionId.ECHO_SENTENCE,
				LegendaryActionId.byId(" ECHO_SENTENCE "));
		assertNull(LegendaryActionId.byId("missing_action"));
	}

	private static long count(LegendaryActionId.Pool pool) {
		return Arrays.stream(LegendaryActionId.values())
				.filter(id -> id.pool() == pool)
				.count();
	}
}
