package com.summy.threat.legendary;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/** 传奇动作 ID 到运行时处理器的注册表。 */
public final class LegendaryActionHandlerRegistry {
	private final Map<LegendaryActionId, LegendaryActionHandler> handlers =
			new EnumMap<>(LegendaryActionId.class);

	public boolean register(LegendaryActionId action, LegendaryActionHandler handler) {
		if (action == null || handler == null || handlers.containsKey(action)) {
			return false;
		}
		handlers.put(action, handler);
		return true;
	}

	public Optional<LegendaryActionHandler> handler(LegendaryActionId action) {
		return Optional.ofNullable(handlers.get(action));
	}
}
