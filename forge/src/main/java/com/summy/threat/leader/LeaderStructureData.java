package com.summy.threat.leader;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** 每维度持久保存的固定结构首领候选表。 */
public final class LeaderStructureData extends SavedData {
	private static final String STRUCTURES = "structures";
	private static final String KEY = "key";
	private static final String TYPE = "type";
	private static final String CANDIDATES = "candidates";
	private static final String ID = "id";
	private static final String TL = "tl";
	private static final String HASH = "hash";
	private static final String ROLL = "roll";
	private static final String X = "x";
	private static final String Y = "y";
	private static final String Z = "z";
	private static final String VETOED = "vetoed";
	private static final String LEADER = "leader";

	public record Candidate(UUID id, int tl, long hash, double roll, int x, int y, int z,
			boolean vetoed, boolean leader) {
		LeaderSpawnPolicy.Candidate policyCandidate() {
			return new LeaderSpawnPolicy.Candidate(id, tl, hash, roll);
		}
	}

	private record Structure(LeaderSpawnPolicy.StructureType type, Map<UUID, Candidate> candidates) {
	}

	private final Map<String, Structure> structures = new LinkedHashMap<>();

	public static LeaderStructureData load(CompoundTag tag) {
		LeaderStructureData data = new LeaderStructureData();
		ListTag saved = tag.getList(STRUCTURES, Tag.TAG_COMPOUND);
		for (int i = 0; i < saved.size(); i++) {
			CompoundTag raw = saved.getCompound(i);
			String key = raw.getString(KEY);
			LeaderSpawnPolicy.StructureType type;
			try {
				type = LeaderSpawnPolicy.StructureType.valueOf(raw.getString(TYPE));
			} catch (IllegalArgumentException exception) {
				continue;
			}
			Map<UUID, Candidate> candidates = new LinkedHashMap<>();
			ListTag list = raw.getList(CANDIDATES, Tag.TAG_COMPOUND);
			for (int j = 0; j < list.size(); j++) {
				CompoundTag savedCandidate = list.getCompound(j);
				try {
					UUID id = UUID.fromString(savedCandidate.getString(ID));
					Candidate candidate = new Candidate(id, savedCandidate.getInt(TL),
							savedCandidate.getLong(HASH), savedCandidate.getDouble(ROLL),
							savedCandidate.getInt(X), savedCandidate.getInt(Y), savedCandidate.getInt(Z),
							savedCandidate.getBoolean(VETOED), savedCandidate.getBoolean(LEADER));
					candidates.put(id, candidate);
				} catch (IllegalArgumentException exception) {
					// 单个损坏候选不应使整张结构表失效。
				}
			}
			if (!key.isBlank() && !candidates.isEmpty()) {
				data.structures.put(key, new Structure(type, candidates));
			}
		}
		return data;
	}

	public LeaderSpawnPolicy.StructureType type(String key) {
		Structure structure = structures.get(key);
		return structure == null ? null : structure.type();
	}

	public List<Candidate> candidates(String key) {
		Structure structure = structures.get(key);
		return structure == null ? List.of() : List.copyOf(structure.candidates().values());
	}

	public boolean add(String key, LeaderSpawnPolicy.StructureType type, Candidate candidate) {
		Structure structure = structures.computeIfAbsent(key,
				ignored -> new Structure(type, new LinkedHashMap<>()));
		if (structure.type() != type) {
			return false;
		}
		Candidate previous = structure.candidates().putIfAbsent(candidate.id(), candidate);
		if (previous != null) {
			return false;
		}
		setDirty();
		return true;
	}

	public void veto(String key, UUID id) {
		Structure structure = structures.get(key);
		if (structure == null) {
			return;
		}
		Candidate candidate = structure.candidates().get(id);
		if (candidate == null || candidate.vetoed()) {
			return;
		}
		structure.candidates().put(id, new Candidate(candidate.id(), candidate.tl(),
				candidate.hash(), candidate.roll(), candidate.x(), candidate.y(), candidate.z(),
				true, candidate.leader()));
		setDirty();
	}

	public void setLeaderRoles(String key, Set<UUID> leaders) {
		Structure structure = structures.get(key);
		if (structure == null) {
			return;
		}
		boolean changed = false;
		for (Map.Entry<UUID, Candidate> entry : structure.candidates().entrySet()) {
			Candidate candidate = entry.getValue();
			boolean leader = leaders.contains(candidate.id());
			if (candidate.leader() != leader) {
				entry.setValue(new Candidate(candidate.id(), candidate.tl(), candidate.hash(),
						candidate.roll(), candidate.x(), candidate.y(), candidate.z(),
						candidate.vetoed(), leader));
				changed = true;
			}
		}
		if (changed) {
			setDirty();
		}
	}

	@Override
	public CompoundTag save(CompoundTag tag) {
		ListTag savedStructures = new ListTag();
		for (Map.Entry<String, Structure> entry : structures.entrySet()) {
			CompoundTag raw = new CompoundTag();
			raw.putString(KEY, entry.getKey());
			raw.putString(TYPE, entry.getValue().type().name());
			ListTag savedCandidates = new ListTag();
			for (Candidate candidate : entry.getValue().candidates().values()) {
				CompoundTag savedCandidate = new CompoundTag();
				savedCandidate.putString(ID, candidate.id().toString());
				savedCandidate.putInt(TL, candidate.tl());
				savedCandidate.putLong(HASH, candidate.hash());
				savedCandidate.putDouble(ROLL, candidate.roll());
				savedCandidate.putInt(X, candidate.x());
				savedCandidate.putInt(Y, candidate.y());
				savedCandidate.putInt(Z, candidate.z());
				savedCandidate.putBoolean(VETOED, candidate.vetoed());
				savedCandidate.putBoolean(LEADER, candidate.leader());
				savedCandidates.add(savedCandidate);
			}
			raw.put(CANDIDATES, savedCandidates);
			savedStructures.add(raw);
		}
		tag.put(STRUCTURES, savedStructures);
		return tag;
	}
}
