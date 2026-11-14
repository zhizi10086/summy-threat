package com.summy.threat.leader;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** 固定结构候选的纯名额算法，不访问世界或实体。 */
public final class LeaderSpawnPolicy {
	public enum StructureType {
		MONUMENT,
		BASTION
	}

	public record Candidate(UUID id, int tl, long hash, double extraRoll) {
	}

	private static final Comparator<Candidate> PRIORITY =
			Comparator.comparingLong(Candidate::hash)
					.thenComparing(candidate -> candidate.id().toString());

	private LeaderSpawnPolicy() {
	}

	public static boolean passesChance(double roll, double chance) {
		return roll >= 0.0D && roll < chance;
	}

	public static Set<UUID> elect(StructureType type, List<Candidate> candidates,
			double monumentExtraChance, double bastionExtraChance) {
		List<Candidate> eligible = candidates.stream()
				.filter(candidate -> candidate.tl() >= 10)
				.sorted(PRIORITY)
				.toList();
		if (eligible.isEmpty()) {
			return Set.of();
		}
		Set<UUID> leaders = new LinkedHashSet<>();
		int guaranteed = type == StructureType.MONUMENT ? 1 : Math.min(2, eligible.size());
		for (int i = 0; i < guaranteed; i++) {
			leaders.add(eligible.get(i).id());
		}
		double extraChance = type == StructureType.MONUMENT
				? monumentExtraChance : bastionExtraChance;
		for (int i = guaranteed; i < eligible.size(); i++) {
			Candidate candidate = eligible.get(i);
			if (candidate.extraRoll() < extraChance) {
				leaders.add(candidate.id());
			}
		}
		if (type == StructureType.MONUMENT) {
			int maxLeaders = Math.min(2, Math.max(1, eligible.size() - 1));
			while (leaders.size() > maxLeaders) {
				leaders.remove(eligible.stream()
						.filter(candidate -> leaders.contains(candidate.id()))
						.reduce((first, second) -> second)
						.orElseThrow()
						.id());
			}
		} else if (eligible.size() >= 3 && leaders.size() == eligible.size()) {
			leaders.remove(eligible.get(eligible.size() - 1).id());
		}
		return Set.copyOf(leaders);
	}
}
