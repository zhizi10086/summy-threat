package com.summy.threat.leader;

import com.summy.threat.Config;
import com.summy.threat.eximus.EximusShield;
import com.summy.threat.pcf.SpawnRegion;
import com.summy.threat.pcf.TlSelector;
import com.summy.threat.scaling.MonsterScaler;
import com.summy.threat.scaling.ScalingState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.ElderGuardian;
import net.minecraft.world.entity.monster.piglin.PiglinBrute;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/** 固定结构、普通自然生成与手动生成的首领身份控制。 */
public final class LeaderSpawnController {
	private static final String DATA_NAME = "summy_threat_fixed_leaders";
	private static final ResourceLocation MONUMENT =
			new ResourceLocation("minecraft", "monument");
	private static final ResourceLocation BASTION =
			new ResourceLocation("minecraft", "bastion_remnant");
	private static final long HASH_SALT = 0x9E3779B97F4A7C15L;
	private static final long ROLL_SALT = 0xD1B54A32D192ED03L;

	private LeaderSpawnController() {
	}

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public static void onPositionCheck(MobSpawnEvent.PositionCheck event) {
		if (event.getSpawner() == null && event.getSpawnType() != MobSpawnType.SPAWNER) {
			return;
		}
		normalizeSpawner(event.getEntity());
	}

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public static void onFinalizeSpawn(MobSpawnEvent.FinalizeSpawn event) {
		Mob mob = event.getEntity();
		if (event.getSpawnType() == MobSpawnType.SPAWNER || event.getSpawner() != null
				|| MonsterScaler.isSpawnerSource(mob)) {
			normalizeSpawner(mob);
			return;
		}
		if (!Config.SPEC.isLoaded() || !ScalingState.enabled()
				|| !MonsterScaler.isScalable(mob)
				|| MonsterScaler.kindOf(mob) == MonsterScaler.Kind.BOSS) {
			return;
		}
		ServerLevel level = event.getLevel().getLevel();
		if (MonsterScaler.isFixedLeaderType(mob)) {
			if (isManualType(event.getSpawnType())) {
				tryMarkManualLeader(level, mob, event.getSpawnType());
				return;
			}
			if (event.getSpawnType() == MobSpawnType.STRUCTURE
					&& registerStructureCandidate(level, mob, event.getSpawnType())) {
				return;
			}
		}
		tryGeneralNaturalLeader(level, mob, event.getSpawnType());
	}

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public static void onEntityJoin(EntityJoinLevelEvent event) {
		if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Mob mob)) {
			return;
		}
		ServerLevel level = (ServerLevel) event.getLevel();
		if (MonsterScaler.isSpawnerSource(mob) || hasSpawnerType(mob)) {
			normalizeSpawner(mob);
			return;
		}
		if (!Config.SPEC.isLoaded() || !ScalingState.enabled()
				|| MonsterScaler.kindOf(mob) == MonsterScaler.Kind.BOSS) {
			return;
		}
		String structureKey = mob.getPersistentData().getString(MonsterScaler.TAG_LEADER_STRUCTURE);
		if (!structureKey.isBlank()) {
			resolveStructureJoin(level, mob, structureKey);
			return;
		}
		if (mob.getPersistentData().getBoolean(MonsterScaler.TAG_LEADER_PENDING)) {
			applyPendingLeader(level, mob, false);
			return;
		}
		if (MonsterScaler.isFixedLeaderType(mob)
				&& !mob.getPersistentData().getBoolean(MonsterScaler.TAG_MANUAL_LEADER)) {
			if (registerStructureCandidate(level, mob, MobSpawnType.STRUCTURE)) {
				return;
			}
			tryMarkManualLeader(level, mob, MobSpawnType.COMMAND);
			if (mob.getPersistentData().getBoolean(MonsterScaler.TAG_LEADER_PENDING)) {
				applyPendingLeader(level, mob, false);
			}
		}
	}

	private static void tryMarkManualLeader(ServerLevel level, Mob mob, MobSpawnType spawnType) {
		int tl = TlSelector.select(level, mob, MonsterScaler.Kind.LEADER, spawnType);
		if (tl < 10) {
			MonsterScaler.clearLeaderCandidate(mob, true);
			return;
		}
		MonsterScaler.markLeaderCandidate(mob, tl, true);
	}

	private static void tryGeneralNaturalLeader(ServerLevel level, Mob mob, MobSpawnType spawnType) {
		if (!isGeneralNaturalType(spawnType) || !MonsterScaler.isLeaderEligible(mob)) {
			return;
		}
		int tl = TlSelector.select(level, mob, MonsterScaler.Kind.LEADER, spawnType);
		if (tl < 10 || !LeaderSpawnPolicy.passesChance(
				mob.getRandom().nextDouble(), Config.LEADER_GENERAL_NATURAL_CHANCE.get())) {
			return;
		}
		markPending(mob, tl);
	}

	private static boolean registerStructureCandidate(ServerLevel level, Mob mob,
			MobSpawnType spawnType) {
		StructureSpec spec = structureSpec(mob);
		if (spec == null) {
			return false;
		}
		StructureStart start = level.structureManager().getStructureWithPieceAt(
				mob.blockPosition(), spec.key());
		if (start == null || !start.isValid()) {
			return false;
		}
		int tl = TlSelector.select(level, mob, MonsterScaler.Kind.LEADER, spawnType);
		if (tl < 10) {
			return false;
		}
		String key = structureKey(spec, start);
		long hash = stableHash(key, mob.blockPosition());
		double roll = stableRoll(hash);
		LeaderStructureData data = structureData(level);
		LeaderStructureData.Candidate candidate = new LeaderStructureData.Candidate(
				mob.getUUID(), tl, hash, roll, mob.blockPosition().getX(),
				mob.blockPosition().getY(), mob.blockPosition().getZ(), false, false);
		data.add(key, spec.type(), candidate);
		mob.getPersistentData().putString(MonsterScaler.TAG_LEADER_STRUCTURE, key);
		Set<UUID> leaders = elect(data, key, spec.type());
		applyCurrentCandidate(mob, leaders.contains(mob.getUUID()), tl);
		applyExistingRoles(level, data, key, spec.type(), leaders, mob.getUUID());
		applyCurrentCandidate(mob,
				elect(data, key, spec.type()).contains(mob.getUUID()), tl);
		return true;
	}

	private static void resolveStructureJoin(ServerLevel level, Mob mob, String key) {
		LeaderStructureData data = structureData(level);
		LeaderSpawnPolicy.StructureType type = data.type(key);
		if (type == null) {
			mob.getPersistentData().remove(MonsterScaler.TAG_LEADER_STRUCTURE);
			return;
		}
		Set<UUID> leaders = elect(data, key, type);
		LeaderStructureData.Candidate candidate = data.candidates(key).stream()
				.filter(value -> value.id().equals(mob.getUUID()))
				.findFirst().orElse(null);
		if (candidate == null) {
			return;
		}
		boolean leader = leaders.contains(mob.getUUID()) && !candidate.vetoed();
		applyCurrentCandidate(mob, leader, candidate.tl());
		if (leader) {
			applyPendingLeader(level, mob, true);
		} else if (MonsterScaler.kindOf(mob) == MonsterScaler.Kind.LEADER
				&& mob.getPersistentData().getBoolean(MonsterScaler.TAG_FIXED_LEADER)) {
			demoteFixedLeader(level, mob, candidate.tl());
		}
		applyExistingRoles(level, data, key, type, elect(data, key, type), mob.getUUID());
		Set<UUID> resolved = elect(data, key, type);
		applyCurrentRole(level, mob, candidate, resolved.contains(mob.getUUID()));
		elect(data, key, type);
	}

	private static void applyCurrentCandidate(Mob mob, boolean leader, int tl) {
		if (leader) {
			markPending(mob, tl);
		} else {
			clearPending(mob, true);
		}
	}

	private static void applyCurrentRole(ServerLevel level, Mob mob,
			LeaderStructureData.Candidate candidate, boolean leader) {
		if (leader) {
			if (MonsterScaler.kindOf(mob) == MonsterScaler.Kind.LEADER
					&& mob.getPersistentData().getBoolean(MonsterScaler.TAG_FIXED_LEADER)) {
				return;
			}
			markPending(mob, candidate.tl());
			applyPendingLeader(level, mob, true);
		} else if (MonsterScaler.kindOf(mob) == MonsterScaler.Kind.LEADER
				&& mob.getPersistentData().getBoolean(MonsterScaler.TAG_FIXED_LEADER)) {
			demoteFixedLeader(level, mob, candidate.tl());
		}
	}

	private static void applyExistingRoles(ServerLevel level, LeaderStructureData data,
			String key, LeaderSpawnPolicy.StructureType type, Set<UUID> leaders, UUID current) {
		boolean changed;
		do {
			changed = false;
			for (LeaderStructureData.Candidate candidate : data.candidates(key)) {
				if (candidate.id().equals(current)) {
					continue;
				}
				Entity entity = level.getEntity(candidate.id());
				if (!(entity instanceof Mob mob) || mob.isRemoved()) {
					continue;
				}
				if (candidate.vetoed()) {
					continue;
				}
				if (MonsterScaler.kindOf(mob) == MonsterScaler.Kind.BOSS) {
					data.veto(key, candidate.id());
					changed = true;
					break;
				}
				if (leaders.contains(candidate.id()) && !candidate.vetoed()) {
					if (mob.getPersistentData().getBoolean(MonsterScaler.TAG_FIXED_LEADER)
							&& MonsterScaler.kindOf(mob) == MonsterScaler.Kind.LEADER) {
						continue;
					}
					markPending(mob, candidate.tl());
					if (!applyPendingLeader(level, mob, true)) {
						changed = true;
						break;
					}
				} else if (mob.getPersistentData().getBoolean(MonsterScaler.TAG_FIXED_LEADER)
						&& MonsterScaler.kindOf(mob) == MonsterScaler.Kind.LEADER) {
					demoteFixedLeader(level, mob, candidate.tl());
				}
			}
			if (changed) {
				leaders = elect(data, key, type);
			}
		} while (changed);
	}

	private static boolean applyPendingLeader(ServerLevel level, Mob mob, boolean fixed) {
		int tl = mob.getPersistentData().getInt(MonsterScaler.TAG_LEADER_PENDING_TL);
		if (tl < 10 || !MonsterScaler.scaleNow(mob, tl, MonsterScaler.Kind.LEADER)) {
			mob.getPersistentData().remove(MonsterScaler.TAG_LEADER_PENDING);
			mob.getPersistentData().remove(MonsterScaler.TAG_LEADER_PENDING_TL);
			mob.getPersistentData().remove(MonsterScaler.TAG_LEADER);
			mob.getPersistentData().remove(MonsterScaler.TAG_FIXED_LEADER);
			mob.getPersistentData().remove(MonsterScaler.TAG_MANUAL_LEADER);
			String key = mob.getPersistentData().getString(MonsterScaler.TAG_LEADER_STRUCTURE);
			if (!key.isBlank()) {
				structureData(level).veto(key, mob.getUUID());
			}
			return false;
		}
		if (MonsterScaler.kindOf(mob) == MonsterScaler.Kind.BOSS) {
			String key = mob.getPersistentData().getString(MonsterScaler.TAG_LEADER_STRUCTURE);
			if (!key.isBlank()) {
				structureData(level).veto(key, mob.getUUID());
			}
			mob.getPersistentData().remove(MonsterScaler.TAG_LEADER_PENDING);
			mob.getPersistentData().remove(MonsterScaler.TAG_LEADER_PENDING_TL);
			mob.getPersistentData().remove(MonsterScaler.TAG_FIXED_LEADER);
			return false;
		}
		if (fixed && MonsterScaler.kindOf(mob) == MonsterScaler.Kind.LEADER) {
			mob.getPersistentData().putBoolean(MonsterScaler.TAG_FIXED_LEADER, true);
		}
		mob.getPersistentData().remove(MonsterScaler.TAG_LEADER_PENDING);
		mob.getPersistentData().remove(MonsterScaler.TAG_LEADER_PENDING_TL);
		return MonsterScaler.kindOf(mob) == MonsterScaler.Kind.LEADER;
	}

	private static void demoteFixedLeader(ServerLevel level, Mob mob, int tl) {
		if (MonsterScaler.scaleNow(mob, tl, MonsterScaler.Kind.NORMAL)) {
			mob.getPersistentData().remove(MonsterScaler.TAG_FIXED_LEADER);
			mob.getPersistentData().remove(MonsterScaler.TAG_LEADER_PENDING);
			mob.getPersistentData().remove(MonsterScaler.TAG_LEADER_PENDING_TL);
		}
	}

	private static void markPending(Mob mob, int tl) {
		MonsterScaler.markLeaderCandidate(mob, tl, false);
	}

	private static void clearPending(Mob mob, boolean clearLeader) {
		MonsterScaler.clearLeaderCandidate(mob, clearLeader);
	}

	private static Set<UUID> elect(LeaderStructureData data, String key,
			LeaderSpawnPolicy.StructureType type) {
		List<LeaderSpawnPolicy.Candidate> candidates = data.candidates(key).stream()
				.filter(candidate -> !candidate.vetoed())
				.map(LeaderStructureData.Candidate::policyCandidate)
				.toList();
		Set<UUID> leaders = LeaderSpawnPolicy.elect(type, candidates,
				Config.LEADER_MONUMENT_EXTRA_CHANCE.get(),
				Config.LEADER_BASTION_EXTRA_CHANCE.get());
		data.setLeaderRoles(key, leaders);
		return leaders;
	}

	private static void normalizeSpawner(Mob mob) {
		var data = mob.getPersistentData();
		data.putString(SpawnRegion.TAG_SPAWN_TYPE, MobSpawnType.SPAWNER.name());
		data.putBoolean(MonsterScaler.TAG_BOSS, false);
		data.putBoolean(MonsterScaler.TAG_LEADER, false);
		data.putBoolean(MonsterScaler.TAG_EXIMUS, false);
		data.remove(MonsterScaler.TAG_ARCHETYPE);
		data.remove(MonsterScaler.TAG_FIXED_LEADER);
		data.remove(MonsterScaler.TAG_LEADER_PENDING);
		data.remove(MonsterScaler.TAG_LEADER_PENDING_TL);
		data.remove(MonsterScaler.TAG_LEADER_STRUCTURE);
		data.remove(MonsterScaler.TAG_MANUAL_LEADER);
		data.remove(MonsterScaler.TAG_HARDCODED_ATTACK_MULTIPLIER);
		data.remove(MonsterScaler.TAG_SCALED);
		LeaderAbilityState.clear(mob);
		LegendaryResistance.clear(mob);
		EximusShield.refresh(mob, 0, false);
	}

	private static boolean hasSpawnerType(Mob mob) {
		return mob.getPersistentData().getString(SpawnRegion.TAG_SPAWN_TYPE)
				.equals(MobSpawnType.SPAWNER.name());
	}

	public static boolean isManualType(MobSpawnType type) {
		return type == MobSpawnType.COMMAND || type == MobSpawnType.SPAWN_EGG;
	}

	public static boolean isGeneralNaturalType(MobSpawnType type) {
		return type == MobSpawnType.NATURAL
				|| type == MobSpawnType.CHUNK_GENERATION
				|| type == MobSpawnType.STRUCTURE
				|| type == MobSpawnType.PATROL;
	}

	private static StructureSpec structureSpec(Mob mob) {
		if (mob instanceof ElderGuardian) {
			return new StructureSpec(LeaderSpawnPolicy.StructureType.MONUMENT,
					ResourceKey.create(Registries.STRUCTURE, MONUMENT), "monument");
		}
		if (mob instanceof PiglinBrute) {
			return new StructureSpec(LeaderSpawnPolicy.StructureType.BASTION,
					ResourceKey.create(Registries.STRUCTURE, BASTION), "bastion");
		}
		return null;
	}

	private static String structureKey(StructureSpec spec, StructureStart start) {
		return spec.name() + "|" + start.getChunkPos().x + "," + start.getChunkPos().z;
	}

	private static LeaderStructureData structureData(ServerLevel level) {
		return level.getDataStorage().computeIfAbsent(
				LeaderStructureData::load, LeaderStructureData::new, DATA_NAME);
	}

	private static long stableHash(String key, BlockPos pos) {
		long value = key.hashCode();
		value = mix64(value ^ pos.asLong() ^ HASH_SALT);
		return value & Long.MAX_VALUE;
	}

	private static double stableRoll(long hash) {
		return (mix64(hash ^ ROLL_SALT) >>> 11) * 0x1.0p-53;
	}

	private static long mix64(long value) {
		value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
		value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
		return value ^ (value >>> 31);
	}

	private record StructureSpec(LeaderSpawnPolicy.StructureType type,
			ResourceKey<Structure> key, String name) {
	}
}
