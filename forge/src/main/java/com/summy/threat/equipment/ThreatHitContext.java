package com.summy.threat.equipment;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.function.Supplier;

/**
 * ST 高威胁技能的单次伤害上下文。
 *
 * <p>上下文按目标与 DamageSource 对象身份匹配，只在对应 {@code hurt} 调用期间有效。
 * 使用栈保存嵌套结算，避免内部追加伤害覆盖外层技能来源。
 */
public final class ThreatHitContext {
	private static final ThreadLocal<Deque<Entry>> HITS =
			ThreadLocal.withInitial(ArrayDeque::new);

	private ThreatHitContext() {
	}

	public static void run(Object target, Object source, ThreatCategory category,
			String abilityId, Runnable action) {
		call(target, source, category, abilityId, () -> {
			action.run();
			return null;
		});
	}

	public static <T> T call(Object target, Object source, ThreatCategory category,
			String abilityId, Supplier<T> action) {
		if (target == null || source == null || category == null || abilityId == null
				|| abilityId.isBlank()) {
			return action.get();
		}
		Deque<Entry> hits = HITS.get();
		hits.push(new Entry(target, source, new Hit(category, abilityId)));
		try {
			return action.get();
		} finally {
			hits.pop();
			if (hits.isEmpty()) {
				HITS.remove();
			}
		}
	}

	public static Hit current(Object target, Object source) {
		if (target == null || source == null) {
			return null;
		}
		Iterator<Entry> iterator = HITS.get().iterator();
		while (iterator.hasNext()) {
			Entry entry = iterator.next();
			if (entry.target() == target && entry.source() == source) {
				return entry.hit();
			}
		}
		return null;
	}

	public record Hit(ThreatCategory category, String abilityId) {
	}

	private record Entry(Object target, Object source, Hit hit) {
	}
}
