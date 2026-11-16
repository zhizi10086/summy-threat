package com.summy.threat.equipment;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ThreatHitContextTest {
	@Test
	void nestedHitContextRestoresOuterContext() {
		Object target = new Object();
		Object source = new Object();

		ThreatHitContext.run(target, source, ThreatCategory.LEADER_ABILITY, "outer", () -> {
			assertEquals("outer", ThreatHitContext.current(target, source).abilityId());
			ThreatHitContext.run(target, source, ThreatCategory.EXIMUS_ABILITY, "inner",
					() -> assertEquals("inner",
							ThreatHitContext.current(target, source).abilityId()));
			assertEquals("outer", ThreatHitContext.current(target, source).abilityId());
		});

		assertNull(ThreatHitContext.current(target, source));
	}

	@Test
	void unrelatedDamageSourceDoesNotReadContext() {
		Object target = new Object();
		Object source = new Object();
		Object other = new Object();
		ThreatHitContext.run(target, source, ThreatCategory.TL_ABILITY, "thorns", () ->
				assertNull(ThreatHitContext.current(target, other)));
	}
}
