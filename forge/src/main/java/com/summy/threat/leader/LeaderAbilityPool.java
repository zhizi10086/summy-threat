package com.summy.threat.leader;

import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;

/** 区域首领抽取：先锁一项光环，再从剩余全部能力抽第二项。 */
public final class LeaderAbilityPool {
	private LeaderAbilityPool() {
	}

	public static List<LeaderAbilityId> eligible(int tl) {
		List<LeaderAbilityId> out = new ArrayList<>();
		for (LeaderAbilityId id : LeaderAbilityId.values()) {
			if (id.unlockTl() <= tl) {
				out.add(id);
			}
		}
		return out;
	}

	public static List<LeaderAbilityId> roll(int tl, RandomSource random) {
		List<LeaderAbilityId> auras = new ArrayList<>();
		for (LeaderAbilityId id : LeaderAbilityId.auraValues()) {
			if (id.unlockTl() <= tl) {
				auras.add(id);
			}
		}
		if (auras.isEmpty()) {
			return List.of();
		}
		LeaderAbilityId aura = removeWeighted(auras, random);
		List<LeaderAbilityId> free = eligible(tl);
		free.remove(aura);
		if (free.isEmpty()) {
			return List.of(aura);
		}
		return List.of(aura, removeWeighted(free, random));
	}

	private static LeaderAbilityId removeWeighted(List<LeaderAbilityId> pool, RandomSource random) {
		int total = 0;
		for (LeaderAbilityId id : pool) {
			total += Math.max(1, id.weight());
		}
		int pick = random.nextInt(Math.max(1, total));
		for (int i = 0; i < pool.size(); i++) {
			LeaderAbilityId id = pool.get(i);
			pick -= Math.max(1, id.weight());
			if (pick < 0) {
				pool.remove(i);
				return id;
			}
		}
		return pool.remove(pool.size() - 1);
	}
}
