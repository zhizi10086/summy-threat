package com.summy.threat.pcf;

import com.summy.threat.Config;
import com.summy.threat.scaling.MonsterScaler;
import com.summy.threat.scaling.ScalingState;
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
import net.minecraftforge.event.server.ServerStartedEvent;
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

	// 持久时钟只累计缩放启用的时间，跨停服和停用保留进度。
	private static final String CLOCK_ID = "summy_threat_cdf_clock";
	private static final Map<CdfKey, Long> LEAVING = new HashMap<>();
	private static volatile Map<CdfKey, List<ServerPlayer>> occupied = Map.of();
	private static CdfClock clock;
	private static volatile long effectiveNow;

	private CdfManager() {
	}

	public static double current(ServerLevel level, BlockPos pos) {
		return snapshot(level, pos).value();
	}

	public static Snapshot snapshot(ServerLevel level, BlockPos pos) {
		CdfKey key = key(level, pos);
		boolean present = hasPlayers(level, key);
		CdfState state = STATES.get(key);
		if (!ScalingState.enabled()) {
			return new Snapshot(state == null ? 0.0D : state.value, 0.0D,
					state == null ? 0.0D : state.peakPcf, 0.0D, 0L, present,
					state == null ? 0L : state.lastUpdate);
		}
		ensureClock(level.getServer());
		state = state(level, key);
		if (!present && !occupied.containsKey(key) && state.decay(effectiveNow)) {
			markDirty(level, key);
		}
		return new Snapshot(state.value, maxPcf(level, key), state.peakPcf,
				state.killBonus, state.deathPenaltyUntil, present, state.lastUpdate);
	}

	public static void setCurrent(ServerLevel level, BlockPos pos, double value) {
		if (!ScalingState.enabled()) {
			return;
		}
		ensureClock(level.getServer());
		CdfKey key = key(level, pos);
		CdfState state = state(level, key);
		state.value = clamp(value, 0.0D, 20.0D);
		state.peakPcf = Math.max(state.peakPcf, state.value);
		state.lastUpdate = effectiveNow;
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
		if (ScalingState.enabled() && !hasPlayers(level, key) && !occupied.containsKey(key)) {
			state.decay(effectiveNow);
		}
		CompoundTag saved = new CompoundTag();
		saved.putDouble(VALUE, state.value);
		saved.putLong(LAST_UPDATE, state.lastUpdate);
		saved.putDouble(PEAK_PCF, state.peakPcf);
		event.getData().put(TAG, saved);
	}

	@SubscribeEvent
	public static void onChunkReady(ChunkEvent.Load event) {
		if (event.getLevel() instanceof ServerLevel level) {
			CdfKey key = key(level, event.getChunk().getPos());
			LEAVING.remove(key);
			if (event.isNewChunk() && ScalingState.enabled()) {
				ensureClock(level.getServer());
				STATES.computeIfAbsent(key, ignored -> {
					CdfState state = new CdfState();
					state.lastUpdate = effectiveNow;
					return state;
				});
			}
		}
	}

	@SubscribeEvent
	public static void onChunkUnload(ChunkEvent.Unload event) {
		if (event.getLevel() instanceof ServerLevel level) {
			CdfKey key = key(level, event.getChunk().getPos());
			CdfState state = STATES.get(key);
			if (state != null) {
				state.killBonus = 0.0D;
				// Forge 在卸载事件之后同步保存，下一游戏刻再清理缓存。
				LEAVING.put(key, level.getGameTime());
			}
		}
	}

	@SubscribeEvent
	public static void onServerTick(TickEvent.ServerTickEvent event) {
		if (event.phase != TickEvent.Phase.END) {
			return;
		}
		MinecraftServer server = event.getServer();
		for (var iterator = LEAVING.entrySet().iterator(); iterator.hasNext();) {
			var entry = iterator.next();
			ServerLevel level = server.getLevel(entry.getKey().dimension());
			if (level != null && level.getGameTime() <= entry.getValue()) {
				continue;
			}
			if (level == null || !loaded(level, entry.getKey())) {
				STATES.remove(entry.getKey());
			}
			iterator.remove();
		}
		if (!ScalingState.enabled()) {
			endVisits();
			return;
		}
		ensureClock(server);
		effectiveNow = ++clock.time;
		clock.setDirty();
		Map<CdfKey, List<ServerPlayer>> groups = new HashMap<>();
		for (ServerLevel level : server.getAllLevels()) {
			for (ServerPlayer player : level.players()) {
				groups.computeIfAbsent(key(level, player.blockPosition()), ignored -> new ArrayList<>()).add(player);
			}
		}
		for (var entry : occupied.entrySet()) {
			if (!groups.containsKey(entry.getKey())) {
				endVisit(entry.getKey(), entry.getValue().get(0).serverLevel().getServer());
			}
		}
		for (var entry : groups.entrySet()) {
			ServerLevel level = entry.getValue().get(0).serverLevel();
			CdfState state = state(level, entry.getKey());
			if (!occupied.containsKey(entry.getKey()) && state.decay(effectiveNow)) {
				markDirty(level, entry.getKey());
			}
			if (server.getTickCount() % 20 == 0) {
				double target = maxPcf(entry.getValue());
				state.peakPcf = Math.max(state.peakPcf, target);
				double rate = doubleCfg(Config.CDF_RISE_RATE, 0.01D)
						+ Math.min(state.killBonus, doubleCfg(Config.CDF_KILL_RISE_MAX_BONUS, 0.05D))
						- (effectiveNow < state.deathPenaltyUntil
								? doubleCfg(Config.CDF_DEATH_RISE_PENALTY, 0.10D) : 0.0D);
				state.value = rise(state.value, target, rate);
				markDirty(level, entry.getKey());
			}
			state.lastUpdate = effectiveNow;
		}
		occupied = groups;
	}

	static double rise(double value, double target, double rate) {
		return clamp(value + Math.max(0.0D, target - value) * clamp(rate, 0.0D, 1.0D), 0.0D, 20.0D);
	}

	private static void ensureClock(MinecraftServer server) {
		if (clock == null) {
			long initial = server.overworld().getGameTime();
			clock = server.overworld().getDataStorage().computeIfAbsent(
					CdfClock::load, () -> new CdfClock(initial), CLOCK_ID);
			effectiveNow = clock.time;
		}
	}

	private static void endVisits() {
		for (var entry : occupied.entrySet()) {
			endVisit(entry.getKey(), entry.getValue().get(0).serverLevel().getServer());
		}
		occupied = Map.of();
	}

	private static void endVisit(CdfKey key, MinecraftServer server) {
		CdfState state = STATES.get(key);
		if (state != null) {
			state.killBonus = 0.0D;
			state.lastUpdate = effectiveNow;
			ServerLevel level = server.getLevel(key.dimension());
			if (level != null) {
				markDirty(level, key);
			}
		}
	}

	@SubscribeEvent
	public static void onLivingDeath(LivingDeathEvent event) {
		if (!ScalingState.enabled() || event.getEntity().level().isClientSide()) {
			return;
		}
		ensureClock(((ServerLevel) event.getEntity().level()).getServer());
		long now = effectiveNow;
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
		reset();
	}

	@SubscribeEvent
	public static void onServerStarted(ServerStartedEvent event) {
		ensureClock(event.getServer());
	}

	@SubscribeEvent
	public static void onServerStopped(ServerStoppedEvent event) {
		reset();
	}

	private static CdfState state(ServerLevel level, CdfKey key) {
		return STATES.computeIfAbsent(key, ignored -> legacyState(level, key));
	}

	private static CdfState legacyState(ServerLevel level, CdfKey key) {
		CdfState state = new CdfState();
		long now = effectiveNow;
		ChunkPos chunk = new ChunkPos(key.chunk());
		BlockPos center = new BlockPos(chunk.getMinBlockX() + 8, 64, chunk.getMinBlockZ() + 8);
		SpawnRegion region = SpawnRegion.detect(level, Vec3.atCenterOf(center), MobSpawnType.NATURAL);
		double max = maxPcf(level, key);
		state.value = Math.min(max, region.legacyCdfReferenceTl())
				* doubleCfg(Config.CDF_LEGACY_CHUNK_INIT_RATIO, 0.5D);
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
		var loaded = level.getChunkSource().getChunkNow(chunk.x, chunk.z);
		if (loaded != null) {
			loaded.setUnsaved(true);
		}
	}

	private static boolean loaded(ServerLevel level, CdfKey key) {
		ChunkPos chunk = new ChunkPos(key.chunk());
		return level.getChunkSource().getChunkNow(chunk.x, chunk.z) != null;
	}

	private static void reset() {
		STATES.clear();
		LEAVING.clear();
		occupied = Map.of();
		clock = null;
		effectiveNow = 0L;
	}

	private static final class CdfClock extends net.minecraft.world.level.saveddata.SavedData {
		long time;

		CdfClock(long time) {
			this.time = time;
		}

		static CdfClock load(CompoundTag tag) {
			return new CdfClock(tag.getLong("time"));
		}

		@Override
		public CompoundTag save(CompoundTag tag) {
			tag.putLong("time", time);
			return tag;
		}
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
		private volatile double value;
		private volatile long lastUpdate;
		private volatile double peakPcf;
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
