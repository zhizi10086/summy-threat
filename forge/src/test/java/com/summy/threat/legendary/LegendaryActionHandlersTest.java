package com.summy.threat.legendary;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LegendaryActionHandlersTest {
	@Test
	void registersHandlerForEveryCatalogAction() {
		LegendaryActionHandlerRegistry registry = new LegendaryActionHandlerRegistry();
		LegendaryActionHandlers.registerAll(registry);
		for (LegendaryActionId action : LegendaryActionId.values()) {
			assertTrue(registry.handler(action).isPresent(), action.id());
		}
	}

	@SuppressWarnings("unchecked")
	@Test
	void riposteWindowsCanBeClearedByOwnerAndServerLifecycle() throws Exception {
		Field field = LegendaryRiposteService.class.getDeclaredField("DAMAGE");
		field.setAccessible(true);
		Map<UUID, ArrayDeque<Object>> damage =
				(Map<UUID, ArrayDeque<Object>>) field.get(null);
		UUID first = UUID.randomUUID();
		UUID second = UUID.randomUUID();
		damage.put(first, new ArrayDeque<>());
		damage.put(second, new ArrayDeque<>());

		LegendaryRiposteService.clearOwner(first);
		assertTrue(!damage.containsKey(first));
		assertTrue(damage.containsKey(second));

		LegendaryRiposteService.clearAll();
		assertTrue(damage.isEmpty());
	}
}
