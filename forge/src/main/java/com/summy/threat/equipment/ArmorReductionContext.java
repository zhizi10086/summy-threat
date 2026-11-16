package com.summy.threat.equipment;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

/**
 * 单次命中直接降低最终护甲减伤率的临时上下文。
 *
 * <p>破界棱晶不修改实体 ARMOR 属性，只在本次目标护甲公式读取并消费指定百分点；
 * 以 DamageSource 对象身份匹配，避免同一 tick 内多目标或附伤互相串用。</p>
 */
public final class ArmorReductionContext {
	private static final ThreadLocal<Deque<Entry>> HITS =
			ThreadLocal.withInitial(ArrayDeque::new);

	private ArmorReductionContext() {
	}

	public static void add(LivingEntity target, DamageSource source, double points) {
		if (target == null || source == null || !Double.isFinite(points) || points <= 0.0D) {
			return;
		}
		HITS.get().push(new Entry(target, source, Math.max(0.0D, Math.min(100.0D, points))));
	}

	/** 读取并移除当前命中的固定百分点削减。 */
	public static double consume(LivingEntity target, DamageSource source) {
		if (target == null || source == null) {
			return 0.0D;
		}
		Deque<Entry> hits = HITS.get();
		Iterator<Entry> iterator = hits.iterator();
		while (iterator.hasNext()) {
			Entry entry = iterator.next();
			if (entry.target() == target && entry.source() == source) {
				iterator.remove();
				if (hits.isEmpty()) {
					HITS.remove();
				}
				return entry.points();
			}
		}
		return 0.0D;
	}

	public static void clear(LivingEntity target, DamageSource source) {
		if (target == null || source == null) {
			return;
		}
		Deque<Entry> hits = HITS.get();
		hits.removeIf(entry -> entry.target() == target && entry.source() == source);
		if (hits.isEmpty()) {
			HITS.remove();
		}
	}

	public static void clearAll() {
		HITS.remove();
	}

	private record Entry(LivingEntity target, DamageSource source, double points) {
	}
}
