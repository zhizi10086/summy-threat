package com.summy.threat.equipment;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.function.Supplier;

/** ST 强制位移事件的单次上下文，用于区分技能位移与普通击退、传送。 */
public final class ThreatMovementContext {
	private static final ThreadLocal<Deque<Entry>> MOVEMENTS =
			ThreadLocal.withInitial(ArrayDeque::new);

	private ThreatMovementContext() {
	}

	public static void run(Object target, String eventId, long expiresAt, Runnable action) {
		call(target, eventId, expiresAt, () -> {
			action.run();
			return null;
		});
	}

	public static <T> T call(Object target, String eventId, long expiresAt,
			Supplier<T> action) {
		if (target == null || eventId == null || eventId.isBlank() || action == null) {
			return action == null ? null : action.get();
		}
		Deque<Entry> movements = MOVEMENTS.get();
		movements.push(new Entry(target, new Movement(eventId, expiresAt)));
		try {
			return action.get();
		} finally {
			movements.pop();
			if (movements.isEmpty()) {
				MOVEMENTS.remove();
			}
		}
	}

	public static Movement current(Object target) {
		if (target == null) {
			return null;
		}
		Iterator<Entry> iterator = MOVEMENTS.get().iterator();
		while (iterator.hasNext()) {
			Entry entry = iterator.next();
			if (entry.target() == target) {
				return entry.movement();
			}
		}
		return null;
	}

	public record Movement(String eventId, long expiresAt) {
	}

	private record Entry(Object target, Movement movement) {
	}
}
