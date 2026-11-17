package com.summy.threat.legendary;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** 测试命令的同玩家同 ID 去重状态。 */
public final class LegendaryTestTracker {
	private final Map<UUID, EnumSet<LegendaryActionId>> active = new HashMap<>();

	public synchronized boolean begin(UUID player, LegendaryActionId action) {
		EnumSet<LegendaryActionId> actions =
				active.computeIfAbsent(player, ignored -> EnumSet.noneOf(LegendaryActionId.class));
		return actions.add(action);
	}

	public synchronized void finish(UUID player, LegendaryActionId action) {
		EnumSet<LegendaryActionId> actions = active.get(player);
		if (actions == null) {
			return;
		}
		actions.remove(action);
		if (actions.isEmpty()) {
			active.remove(player);
		}
	}

	public synchronized void clear(UUID player) {
		active.remove(player);
	}

	public synchronized void clearAll() {
		active.clear();
	}
}
