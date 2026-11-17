package com.summy.threat.legendary;

import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;

/** 按 Boss 类别构建传奇动作候选池并执行加权抽取。 */
public final class LegendaryActionPool {
	private LegendaryActionPool() {
	}

	public static List<LegendaryActionId> eligible(LegendaryBossProfile profile) {
		if (profile == null || !profile.eligible()) {
			return List.of();
		}
		List<LegendaryActionId> out = new ArrayList<>(10);
		for (LegendaryActionId action : LegendaryActionId.values()) {
			if (isAllowed(action, profile)) {
				out.add(action);
			}
		}
		return List.copyOf(out);
	}

	public static LegendaryActionId roll(LegendaryBossProfile profile, RandomSource random) {
		List<LegendaryActionId> pool = new ArrayList<>(eligible(profile));
		if (pool.isEmpty()) {
			return null;
		}
		double total = 0.0D;
		for (LegendaryActionId action : pool) {
			total += Math.max(0.000001D, action.weight());
		}
		double pick = random.nextDouble() * Math.max(0.000001D, total);
		for (LegendaryActionId action : pool) {
			pick -= Math.max(0.000001D, action.weight());
			if (pick < 0.0D) {
				return action;
			}
		}
		return pool.get(pool.size() - 1);
	}

	private static boolean isAllowed(LegendaryActionId action, LegendaryBossProfile profile) {
		return switch (action.pool()) {
			case GENERIC -> true;
			case DRAGON_EXCLUSIVE -> profile.dragonFamily();
			case BOSS_EXCLUSIVE -> switch (action) {
				case DRAGONFALL_EDICT -> profile.enderDragonBoss();
				case DECAY_TIDE -> profile.witherBoss();
				case ECHO_SENTENCE -> profile.wardenBoss();
				default -> false;
			};
		};
	}
}
