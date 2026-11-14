package com.summy.threat.devcheck;

import com.summy.threat.leader.LeaderSpawnPolicy;
import com.summy.threat.leader.LeaderStructureData;
import com.summy.threat.api.ThreatConfigApi;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** 固定结构首领名额算法的无世界副作用断言。 */
final class LeaderSpawnPolicyDevCheck {
	private LeaderSpawnPolicyDevCheck() {
	}

	static List<String> run() {
		List<String> failures = new ArrayList<>();
		checkMonument(failures);
		checkBastion(failures);
		checkTlGate(failures);
		checkCandidateReallocation(failures);
		checkGeneralChance(failures);
		checkPersistence(failures);
		return failures;
	}

	private static void checkMonument(List<String> failures) {
		UUID first = id(1);
		UUID second = id(2);
		UUID third = id(3);
		Set<UUID> guaranteed = LeaderSpawnPolicy.elect(
				LeaderSpawnPolicy.StructureType.MONUMENT,
				List.of(
						candidate(first, 20, 10L, 0.9D),
						candidate(second, 20, 20L, 0.9D),
						candidate(third, 20, 30L, 0.9D)),
				0.25D, 0.10D);
		check(failures, "MONUMENT 始终至少有 1 只首领",
				guaranteed.size() == 1 && guaranteed.contains(first), guaranteed.toString());

		Set<UUID> twoCandidates = LeaderSpawnPolicy.elect(
				LeaderSpawnPolicy.StructureType.MONUMENT,
				List.of(candidate(first, 20, 10L, 0.0D),
						candidate(second, 20, 20L, 0.0D)),
				0.25D, 0.10D);
		check(failures, "MONUMENT 两候选仍保留至少 1 只普通个体",
				twoCandidates.equals(Set.of(first)), twoCandidates.toString());

		Set<UUID> capped = LeaderSpawnPolicy.elect(
				LeaderSpawnPolicy.StructureType.MONUMENT,
				List.of(
						candidate(first, 20, 10L, 0.0D),
						candidate(second, 20, 20L, 0.0D),
						candidate(third, 20, 30L, 0.0D)),
				0.25D, 0.10D);
		check(failures, "MONUMENT 最多 2 只且不会全员首领",
				capped.size() == 2 && capped.contains(first) && capped.contains(second),
				capped.toString());
	}

	private static void checkBastion(List<String> failures) {
		UUID first = id(11);
		UUID second = id(12);
		UUID third = id(13);
		UUID fourth = id(14);
		check(failures, "BASTION 单候选成为首领",
				LeaderSpawnPolicy.elect(LeaderSpawnPolicy.StructureType.BASTION,
						List.of(candidate(first, 20, 10L, 0.9D)),
						0.25D, 0.10D).equals(Set.of(first)), "");
		check(failures, "BASTION 双候选允许双首领",
				LeaderSpawnPolicy.elect(LeaderSpawnPolicy.StructureType.BASTION,
						List.of(candidate(first, 20, 10L, 0.9D),
								candidate(second, 20, 20L, 0.9D)),
						0.25D, 0.10D).equals(Set.of(first, second)), "");

		Set<UUID> three = LeaderSpawnPolicy.elect(
				LeaderSpawnPolicy.StructureType.BASTION,
				List.of(candidate(first, 20, 10L, 0.9D),
						candidate(second, 20, 20L, 0.9D),
						candidate(third, 20, 30L, 0.9D)),
				0.25D, 0.10D);
		check(failures, "BASTION 三候选保留至少 1 只普通",
				three.equals(Set.of(first, second)), three.toString());

		Set<UUID> four = LeaderSpawnPolicy.elect(
				LeaderSpawnPolicy.StructureType.BASTION,
				List.of(candidate(first, 20, 10L, 0.0D),
						candidate(second, 20, 20L, 0.0D),
						candidate(third, 20, 30L, 0.0D),
						candidate(fourth, 20, 40L, 0.0D)),
				0.25D, 0.10D);
		check(failures, "BASTION 概率补满时降级最末候选",
				four.equals(Set.of(first, second, third)) && !four.contains(fourth),
				four.toString());
	}

	private static void checkTlGate(List<String> failures) {
		UUID low = id(21);
		UUID valid = id(22);
		Set<UUID> leaders = LeaderSpawnPolicy.elect(
				LeaderSpawnPolicy.StructureType.BASTION,
				List.of(candidate(low, 9, 10L, 0.0D),
						candidate(valid, 10, 20L, 0.9D)),
				0.25D, 0.10D);
		check(failures, "结构算法排除 TL9 候选",
				leaders.equals(Set.of(valid)), leaders.toString());
	}

	private static void checkCandidateReallocation(List<String> failures) {
		UUID first = id(41);
		UUID second = id(42);
		UUID third = id(43);
		LeaderStructureData data = new LeaderStructureData();
		String key = "monument|3,4";
		data.add(key, LeaderSpawnPolicy.StructureType.MONUMENT,
				new LeaderStructureData.Candidate(first, 20, 10L, 0.9D, 0, 64, 0, false, false));
		data.add(key, LeaderSpawnPolicy.StructureType.MONUMENT,
				new LeaderStructureData.Candidate(second, 20, 20L, 0.9D, 1, 64, 0, false, false));
		data.add(key, LeaderSpawnPolicy.StructureType.MONUMENT,
				new LeaderStructureData.Candidate(third, 20, 30L, 0.9D, 2, 64, 0, false, false));
		Set<UUID> initial = elect(data, key);
		data.veto(key, first);
		Set<UUID> reassigned = elect(data, key);
		check(failures, "固定候选被取消后由下一优先候选补位",
				initial.equals(Set.of(first)) && reassigned.equals(Set.of(second)),
				"initial=" + initial + " reassigned=" + reassigned);

		Set<UUID> shuffled = LeaderSpawnPolicy.elect(
				LeaderSpawnPolicy.StructureType.MONUMENT,
				List.of(candidate(third, 20, 30L, 0.9D),
						candidate(first, 20, 10L, 0.9D),
						candidate(second, 20, 20L, 0.9D)),
				0.25D, 0.10D);
		check(failures, "结构选举不受候选加载顺序影响",
				shuffled.equals(Set.of(first)), shuffled.toString());
	}

	private static Set<UUID> elect(LeaderStructureData data, String key) {
		return LeaderSpawnPolicy.elect(
				LeaderSpawnPolicy.StructureType.MONUMENT,
				data.candidates(key).stream()
						.filter(candidate -> !candidate.vetoed())
						.map(candidate -> new LeaderSpawnPolicy.Candidate(
								candidate.id(), candidate.tl(), candidate.hash(), candidate.roll()))
						.toList(),
				0.25D, 0.10D);
	}

	private static void checkGeneralChance(List<String> failures) {
		RandomSource random = RandomSource.create(0x0A11CE5DL);
		int hits = 0;
		for (int i = 0; i < 100_000; i++) {
			if (LeaderSpawnPolicy.passesChance(random.nextDouble(), 0.001D)) {
				hits++;
			}
		}
		double ratio = hits / 100_000.0D;
		check(failures, "普通自然首领 0.1% 门控采样",
				ratio >= 0.0008D && ratio <= 0.0012D,
				"hits=" + hits + " ratio=" + ratio);
		var snapshot = ThreatConfigApi.snapshot();
		check(failures, "KJS 配置快照暴露 leader_spawn 三键",
				snapshot.containsKey("leader_spawn.natural_chance")
						&& snapshot.containsKey("leader_spawn.monument_extra_chance")
						&& snapshot.containsKey("leader_spawn.bastion_extra_chance"),
				"keys=" + snapshot.keySet().stream()
						.filter(key -> key.startsWith("leader_spawn.")).sorted().toList());
	}

	private static void checkPersistence(List<String> failures) {
		LeaderStructureData data = new LeaderStructureData();
		UUID id = id(31);
		LeaderStructureData.Candidate candidate = new LeaderStructureData.Candidate(
				id, 20, 123L, 0.25D, 10, 64, -8, false, false);
		boolean added = data.add("monument|1,2",
				LeaderSpawnPolicy.StructureType.MONUMENT, candidate);
		data.setLeaderRoles("monument|1,2", Set.of(id));
		candidate = new LeaderStructureData.Candidate(
				id, 20, 123L, 0.25D, 10, 64, -8, false, true);
		CompoundTag saved = data.save(new CompoundTag());
		LeaderStructureData loaded = LeaderStructureData.load(saved);
		check(failures, "结构候选表可跨存档读写",
				added && loaded.candidates("monument|1,2").equals(List.of(candidate)),
				"candidates=" + loaded.candidates("monument|1,2"));
		data.veto("monument|1,2", id);
		check(failures, "结构候选取消可持久标记",
				data.candidates("monument|1,2").get(0).vetoed(), "");
	}

	private static LeaderSpawnPolicy.Candidate candidate(
			UUID id, int tl, long hash, double extraRoll) {
		return new LeaderSpawnPolicy.Candidate(id, tl, hash, extraRoll);
	}

	private static UUID id(long raw) {
		return new UUID(0L, raw);
	}

	private static void check(List<String> failures, String label, boolean ok, String detail) {
		ThreatDevCheck.recordSubcheck();
		if (!ok) {
			failures.add(label + "：" + detail);
		}
	}
}
