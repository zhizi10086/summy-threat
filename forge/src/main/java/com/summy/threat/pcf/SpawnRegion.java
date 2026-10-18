package com.summy.threat.pcf;

import com.summy.threat.Config;
import com.summy.threat.scaling.MonsterScaler;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 生成区域：只用于 PCF/CDF 的区域倍率与区域首领 TL 上限。
 *
 * <p>优先级：刷怪笼 > 出生点 200 格内 > 结构 > 下界/末地 > 普通群系。
 * 结构查询按区块缓存，避免每只怪重复扫描。
 */
public enum SpawnRegion {
	SPAWNER("spawner"),
	SPAWN_PROTECTION("spawn_protection"),
	STRUCTURE("structure"),
	NETHER_END("nether_end"),
	NORMAL_BIOME("normal_biome");

	public static final String TAG_SPAWN_TYPE = "summy_threat:spawn_type";
	private static final Map<ResourceKey<Level>, Map<Long, Boolean>> STRUCTURE_CACHE = new ConcurrentHashMap<>();

	private final String id;

	SpawnRegion(String id) {
		this.id = id;
	}

	public String id() {
		return id;
	}

	public double multiplier() {
		return switch (this) {
			case SPAWNER -> doubleCfg(Config.REGION_MULTIPLIER_SPAWNER, 0.3D);
			case SPAWN_PROTECTION -> doubleCfg(Config.REGION_MULTIPLIER_SPAWN_PROTECTION, 0.5D);
			case STRUCTURE -> doubleCfg(Config.REGION_MULTIPLIER_STRUCTURE, 1.5D);
			case NETHER_END -> doubleCfg(Config.REGION_MULTIPLIER_NETHER_END, 1.2D);
			case NORMAL_BIOME -> doubleCfg(Config.REGION_MULTIPLIER_NORMAL_BIOME, 1.0D);
		};
	}

	public int leaderCap() {
		return switch (this) {
			case SPAWNER -> intCfg(Config.REGION_TL_CAP_SPAWNER, 12);
			case SPAWN_PROTECTION -> intCfg(Config.REGION_TL_CAP_SPAWN_PROTECTION, 12);
			case STRUCTURE -> intCfg(Config.REGION_TL_CAP_STRUCTURE, 15);
			case NETHER_END -> intCfg(Config.REGION_TL_CAP_NETHER_END, 14);
			case NORMAL_BIOME -> intCfg(Config.REGION_TL_CAP_NORMAL_BIOME, 12);
		};
	}

	public static SpawnRegion detect(ServerLevel level, Vec3 pos, MobSpawnType spawnType) {
		if (spawnType == MobSpawnType.SPAWNER) {
			return SPAWNER;
		}
		BlockPos block = BlockPos.containing(pos);
		if (Vec3.atCenterOf(level.getSharedSpawnPos()).distanceToSqr(pos) <= 200.0D * 200.0D) {
			return SPAWN_PROTECTION;
		}
		if (hasStructure(level, block)) {
			return STRUCTURE;
		}
		if (level.dimension() == Level.NETHER || level.dimension() == Level.END) {
			return NETHER_END;
		}
		return NORMAL_BIOME;
	}

	public static SpawnRegion forEntity(ServerLevel level, LivingEntity entity) {
		return detect(level, entity.position(), spawnType(entity));
	}

	private static MobSpawnType spawnType(LivingEntity entity) {
		CompoundTag data = entity.getPersistentData();
		if (!data.contains(TAG_SPAWN_TYPE)) {
			return MobSpawnType.NATURAL;
		}
		try {
			return MobSpawnType.valueOf(data.getString(TAG_SPAWN_TYPE));
		} catch (IllegalArgumentException e) {
			return MobSpawnType.NATURAL;
		}
	}

	private static boolean hasStructure(ServerLevel level, BlockPos pos) {
		Map<Long, Boolean> cache = STRUCTURE_CACHE.computeIfAbsent(level.dimension(),
				ignored -> new ConcurrentHashMap<>());
		long chunk = ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
		return cache.computeIfAbsent(chunk, ignored -> !level.structureManager()
				.getAllStructuresAt(pos).isEmpty());
	}

	@SubscribeEvent
	public static void onFinalizeSpawn(MobSpawnEvent.FinalizeSpawn event) {
		Mob mob = event.getEntity();
		if (MonsterScaler.isScalable(mob)) {
			mob.getPersistentData().putString(TAG_SPAWN_TYPE, event.getSpawnType().name());
		}
	}

	@SubscribeEvent
	public static void onChunkUnload(ChunkEvent.Unload event) {
		if (event.getLevel() instanceof ServerLevel level) {
			Map<Long, Boolean> cache = STRUCTURE_CACHE.get(level.dimension());
			if (cache != null) {
				cache.remove(event.getChunk().getPos().toLong());
			}
		}
	}

	@SubscribeEvent
	public static void onServerAboutToStart(ServerAboutToStartEvent event) {
		STRUCTURE_CACHE.clear();
	}

	private static double doubleCfg(net.minecraftforge.common.ForgeConfigSpec.DoubleValue value, double fallback) {
		return Config.SPEC.isLoaded() ? value.get() : fallback;
	}

	private static int intCfg(net.minecraftforge.common.ForgeConfigSpec.IntValue value, int fallback) {
		return Config.SPEC.isLoaded() ? value.get() : fallback;
	}
}
