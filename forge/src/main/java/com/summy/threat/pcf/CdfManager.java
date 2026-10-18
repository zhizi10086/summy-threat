package com.summy.threat.pcf;

import com.summy.threat.Config;
import com.summy.threat.scaling.MonsterScaler;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.level.ChunkDataEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 区块难度因子（CDF）：区块历史活动强度。
 *
 * <p>区块内有玩家时按玩家最高 PCF 缓慢上升；无玩家时按时间戳衰减，并以历史峰值的
 * {@code decay_floor_ratio} 为下限。数据保存在区块 NBT。
 */
public final class CdfManager {
	private static final String TAG = "summy_threat:chunk_difficulty";
	private static final String VALUE = "cdf_value";
	private static final String LAST_UPDATE = "last_update";
	private static final String PEAK_PCF = "peak_pcf";

	private static final Map<CdfKey, CdfState> STATES = new ConcurrentHashMap<>();

	private CdfManager() {
	}

	public static double current(ServerLevel level, BlockPos pos) {
		CdfKey key = key(level, pos);
		CdfState state = state(level, key);
		if (!hasPlayers(level, key) && state.decay(level.getGameTime())) {
			markDirty(level, key);
		}
		return state.value;
	}

	public static Snapshot snapshot(ServerLevel level, BlockPos pos) {
		CdfKey key = key(level, pos);
		CdfState state = state(level, key);
		if (!hasPlayers(level, key) && state.decay(level.getGameTime())) {
			markDirty(level, key);
		}
		return new Snapshot(state.value, maxPcf(level, key), state.peakPcf,
				state.killBonus, state.deathPenaltyUntil, hasPlayers(level, key), state.lastUpdate);
	}

	public static void setCurrent(ServerLevel level, BlockPos pos, double value) {
		CdfKey key = key(level, pos);
		CdfState state = state(level, key);
		state.value = clamp(value, 0.0D, 20.0D);
		state.peakPcf = Math.max(state.peakPcf, state.value);
		state.lastUpdate = level.getGameTime();
		state.killBonus = 0.0D;
		state.deathPenaltyUntil = 0L;
		markDirty(level, key);
	}

	@SubscribeEvent
	public static void onChunkLoad(ChunkDataEvent.Load event) {
		if (!(event.getLevel() instanceof ServerLevel level)) {
			return;
		}
		CompoundTag data = event.getData();
		if (!data.contains(TAG, CompoundTag.TAG_COMPOUND)) {
			return;
		}
		CompoundTag saved = data.getCompound(TAG);
		CdfState state = new CdfState();
		state.value = clamp(saved.getDouble(VALUE), 0.0D, 20.0D);
		state.lastUpdate = saved.getLong(LAST_UPDATE);
		state.peakPcf = clamp(saved.getDouble(PEAK_PCF), 0.0D, 20.0D);
		STATES.put(key(level, event.getChunk().getPos()), state);
	}

	@SubscribeEvent
	public static void onChunkSave(ChunkDataEvent.Save event) {
		if (!(event.getLevel() instanceof ServerLevel level)) {
			return;
		}
		CdfKey key = key(level, event.getChunk().getPos());
		CdfState state = STATES.get(key);
		if (state == null) {
			return;
		}
		if (!hasPlayers(level, key)) {
			state.decay(level.getGameTime());
		}
		CompoundTag saved = new CompoundTag();
		saved.putDouble(VALUE, state.value);
		saved.putLong(LAST_UPDATE, state.lastUpdate);
		saved.putDouble(PEAK_PCF, state.peakPcf);
		event.getData().put(TAG, saved);
	}

	@SubscribeEvent
	public static void onChunkUnload(ChunkEvent.Unload event) {
		// 区块卸载可能发生在 Save 事件之前（停服路径尤其明显）；这里不主动移除状态，
		// 由 Save 事件写入 NBT。运行期状态由 ServerStoppedEvent 统一清理。
	}

	@SubscribeEvent
	public static void onServerTick(TickEvent.ServerTickEvent event) {
		if (event.phase != TickEvent.Phase.END || event.getServer().getTickCount() % 20 != 0) {
			return;
		}
		MinecraftServer server = event.getServer();
		long now = server.overworld().getGameTime();
		Map<CdfKey, List<ServerPlayer>> groups = new HashMap<>();
		for (ServerLevel level : server.getAllLevels()) {
			for (ServerPlayer player : level.players()) {
				groups.computeIfAbsent(key(level, player.blockPosition()), ignored -> new ArrayList<>()).add(player);
			}
		}
		for (Map.Entry<CdfKey, List<ServerPlayer>> entry : groups.entrySet()) {
			List<ServerPlayer> players = entry.getValue();
			if (players.isEmpty()) {
				continue;
			}
			ServerLevel level = players.get(0).serverLevel();
			CdfState state = state(level, entry.getKey());
			double target = maxPcf(players);
			state.peakPcf = Math.max(state.peakPcf, target);
			double rate = doubleCfg(Config.CDF_RISE_RATE, 0.01D)
					+ Math.min(state.killBonus, doubleCfg(Config.CDF_KILL_RISE_MAX_BONUS, 0.05D))
					- (now < state.deathPenaltyUntil ? doubleCfg(Config.CDF_DEATH_RISE_PENALTY, 0.10D) : 0.0D);
			rate = Math.max(0.0D, rate);
			if (target > state.value) {
				state.value = Math.max(state.value, state.value + (target - state.value) * rate);
				markDirty(level, entry.getKey());
			}
			state.lastUpdate = now;
		}
	}

	@SubscribeEvent
	public static void onLivingDeath(LivingDeathEvent event) {
		long now = event.getEntity().level().getGameTime();
		if (event.getEntity() instanceof ServerPlayer player) {
			CdfKey key = key(player.serverLevel(), player.blockPosition());
			CdfState state = state(player.serverLevel(), key);
			state.deathPenaltyUntil = Math.max(state.deathPenaltyUntil,
					now + intCfg(Config.CDF_DEATH_PENALTY_DURATION, 60) * 20L);
			markDirty(player.serverLevel(), key);
			return;
		}
		if (!MonsterScaler.isScalable(event.getEntity())) {
			return;
		}
		ServerPlayer killer = playerOf(event.getSource());
		if (killer == null) {
			return;
		}
		CdfKey key = key(killer.serverLevel(), event.getEntity().blockPosition());
		CdfState state = state(killer.serverLevel(), key);
		state.killBonus = Math.min(
				doubleCfg(Config.CDF_KILL_RISE_MAX_BONUS, 0.05D),
				state.killBonus + doubleCfg(Config.CDF_KILL_RISE_BONUS, 0.005D));
		markDirty(killer.serverLevel(), key);
	}

	@SubscribeEvent
	public static void onServerAboutToStart(ServerAboutToStartEvent event) {
		STATES.clear();
	}

	@SubscribeEvent
	public static void onServerStopped(ServerStoppedEvent event) {
		STATES.clear();
	}

	private static CdfState state(ServerLevel level, CdfKey key) {
		return STATES.computeIfAbsent(key, ignored -> legacyState(level, key));
	}

	private static CdfState legacyState(ServerLevel level, CdfKey key) {
		CdfState state = new CdfState();
		long now = level.getGameTime();
		ChunkPos chunk = new ChunkPos(key.chunk());
		BlockPos center = new BlockPos(chunk.getMinBlockX() + 8, 64, chunk.getMinBlockZ() + 8);
		SpawnRegion region = SpawnRegion.detect(level, Vec3.atCenterOf(center), MobSpawnType.NATURAL);
		double max = maxPcf(level, key);
		state.value = Math.min(max, region.leaderCap()) * doubleCfg(Config.CDF_LEGACY_CHUNK_INIT_RATIO, 0.5D);
		state.lastUpdate = now;
		state.peakPcf = max;
		return state;
	}

	private static boolean hasPlayers(ServerLevel level, CdfKey key) {
		return !level.getPlayers(player -> key(level, player.blockPosition()).equals(key)).isEmpty();
	}

	private static double maxPcf(ServerLevel level, CdfKey key) {
		List<ServerPlayer> players = level.getPlayers(player -> key(level, player.blockPosition()).equals(key));
		return maxPcf(players);
	}

	private static double maxPcf(List<ServerPlayer> players) {
		double max = 0.0D;
		for (ServerPlayer player : players) {
			max = Math.max(max, PcfManager.value(player));
		}
		return max;
	}

	private static CdfKey key(ServerLevel level, BlockPos pos) {
		return new CdfKey(level.dimension(), ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4));
	}

	private static CdfKey key(ServerLevel level, ChunkPos chunk) {
		return new CdfKey(level.dimension(), chunk.toLong());
	}

	private static void markDirty(ServerLevel level, CdfKey key) {
		ChunkPos chunk = new ChunkPos(key.chunk());
		level.getChunkAt(new BlockPos(chunk.getMinBlockX(), 0, chunk.getMinBlockZ())).setUnsaved(true);
	}

	private static ServerPlayer playerOf(DamageSource source) {
		if (source.getEntity() instanceof ServerPlayer player) {
			return player;
		}
		if (source.getDirectEntity() instanceof Projectile projectile
				&& projectile.getOwner() instanceof ServerPlayer player) {
			return player;
		}
		return null;
	}

	private static double doubleCfg(ForgeConfigSpec.DoubleValue value, double fallback) {
		return Config.SPEC.isLoaded() ? value.get() : fallback;
	}

	private static int intCfg(ForgeConfigSpec.IntValue value, int fallback) {
		return Config.SPEC.isLoaded() ? value.get() : fallback;
	}

	private static double clamp(double value, double min, double max) {
		return Math.max(min, Math.min(max, value));
	}

	private record CdfKey(ResourceKey<Level> dimension, long chunk) {
	}

	private static final class CdfState {
		private double value;
		private long lastUpdate;
		private double peakPcf;
		private double killBonus;
		private long deathPenaltyUntil;

		private boolean decay(long now) {
			if (now <= lastUpdate) {
				return false;
			}
			long ticks = now - lastUpdate;
			double rate = doubleCfg(Config.CDF_DECAY_RATE, 0.0002D);
			value *= Math.pow(1.0D - clamp(rate, 0.0D, 1.0D), ticks);
			value = Math.max(value, peakPcf * doubleCfg(Config.CDF_DECAY_FLOOR_RATIO, 0.20D));
			lastUpdate = now;
			killBonus = 0.0D;
			return true;
		}
	}

	public record Snapshot(double value, double target, double peakPcf, double killBonus,
			long deathPenaltyUntil, boolean hasPlayers, long lastUpdate) {
	}
}
