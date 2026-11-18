package com.summy.threat.legendary;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegendaryActionHandlerRegistryTest {
	@Test
	void registersOneHandlerPerActionAndRejectsDuplicate() {
		LegendaryActionHandler first = context -> (ctx, age) -> false;
		LegendaryActionHandler second = context -> (ctx, age) -> false;
		LegendaryActionHandlerRegistry registry = new LegendaryActionHandlerRegistry();

		assertTrue(registry.register(LegendaryActionId.JADE_LIGHT, first));
		assertSame(first, registry.handler(LegendaryActionId.JADE_LIGHT).orElseThrow());
		assertFalse(registry.register(LegendaryActionId.JADE_LIGHT, second));
		assertSame(first, registry.handler(LegendaryActionId.JADE_LIGHT).orElseThrow());
	}
}
