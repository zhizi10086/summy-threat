package com.summy.threat.combat;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

/**
 * 单次命中的后台护甲穿透上下文。
 *
 * <p>穿甲不写入实体属性或 ForgeData，只在 {@link #hurt(LivingEntity, DamageSource, float, double)}
 * 包裹的这一次受伤流程内有效。使用栈保存上下文，允许伤害事件内部再次触发受伤而不会串到外层命中。
 */
public final class ArmorPenetration {
	private static final ThreadLocal<Deque<Hit>> HITS =
			ThreadLocal.withInitial(ArrayDeque::new);

	private record Hit(LivingEntity target, DamageSource source, double percent) {
	}

	private ArmorPenetration() {
	}

	/**
	 * 以指定的后台护甲穿透比例结算一次伤害。
	 *
	 * @param percent 0~1；0 表示普通命中，1 表示本次忽略全部后台护甲
	 */
	public static boolean hurt(LivingEntity target, DamageSource source, float amount, double percent) {
		double normalized = clampPercent(percent);
		if (amount <= 0.0F || normalized <= 0.0D) {
			return target.hurt(source, amount);
		}
		Deque<Hit> hits = HITS.get();
		hits.push(new Hit(target, source, normalized));
		try {
			return target.hurt(source, amount);
		} finally {
			hits.pop();
			if (hits.isEmpty()) {
				HITS.remove();
			}
		}
	}

	/** 当前伤害流程对应的穿甲比例；没有显式标记时返回 0。 */
	static double percentFor(LivingEntity target, DamageSource source) {
		if (source == null) {
			return 0.0D;
		}
		Deque<Hit> hits = HITS.get();
		Iterator<Hit> iterator = hits.iterator();
		while (iterator.hasNext()) {
			Hit hit = iterator.next();
			if (hit.target() == target && hit.source() == source) {
				return hit.percent();
			}
		}
		return 0.0D;
	}

	private static double clampPercent(double percent) {
		if (!Double.isFinite(percent)) {
			return 0.0D;
		}
		return Math.max(0.0D, Math.min(1.0D, percent));
	}
}
