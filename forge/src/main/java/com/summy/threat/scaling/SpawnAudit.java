package com.summy.threat.scaling;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 刷怪审计：直接记录 FinalizeSpawn 的生成类型、卓越者词条发布与 EntityJoin 的最终分类，
 * 不依赖粒子或外观判断卓越者是否刷新。
 */
public final class SpawnAudit {
	private static final int RECENT_LIMIT = 48;
	private static final Map<String, AtomicLong> SPAWN_TYPES = new HashMap<>();
	private static final AtomicLong NATURAL_CANDIDATES = new AtomicLong();
	private static final AtomicLong NATURAL_EXIMUS = new AtomicLong();
	private static final AtomicLong BOSS_EXCLUDED = new AtomicLong();
	private static final Deque<String> RECENT = new ArrayDeque<>();
	private static volatile boolean recording;

	private SpawnAudit() {
	}

	public static void setRecording(boolean value) {
		recording = value;
	}

	public static boolean recording() {
		return recording;
	}

	public static void reset() {
		SPAWN_TYPES.clear();
		NATURAL_CANDIDATES.set(0L);
		NATURAL_EXIMUS.set(0L);
		BOSS_EXCLUDED.set(0L);
		synchronized (RECENT) {
			RECENT.clear();
		}
	}

	public static void recordFinalize(Mob mob, MobSpawnType type, boolean naturalEligible,
			boolean eximusTagged, double chance, String reason) {
		SPAWN_TYPES.computeIfAbsent(type.name(), ignored -> new AtomicLong()).incrementAndGet();
		if (naturalEligible) {
			NATURAL_CANDIDATES.incrementAndGet();
			if (eximusTagged) {
				NATURAL_EXIMUS.incrementAndGet();
			}
		}
		if ("boss_excluded".equals(reason)) {
			BOSS_EXCLUDED.incrementAndGet();
		}
		if (!recording) {
			return;
		}
		ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
		add("%s | spawn=%s | natural=%s | eximus=%s | chance=%.4f | reason=%s"
				.formatted(id, type, naturalEligible, eximusTagged, chance, reason));
	}

	public static void recordJoin(LivingEntity entity, MonsterScaler.Kind kind, int tl,
			double pcf, double cdf, String region, String abilities, String archetype) {
		if (!recording) {
			return;
		}
		ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
		add("%s | join=%s | tl=%d | pcf=%.2f | cdf=%.2f | region=%s | abilities=%s | archetype=%s"
				.formatted(id, kind, tl, pcf, cdf, region,
						abilities == null || abilities.isEmpty() ? "-" : abilities,
						archetype == null || archetype.isEmpty() ? "-" : archetype));
	}

	public static String summary() {
		StringBuilder out = new StringBuilder();
		out.append("recording=").append(recording)
				.append(" natural=").append(NATURAL_CANDIDATES.get())
				.append(" naturalEximus=").append(NATURAL_EXIMUS.get())
				.append(" bossExcluded=").append(BOSS_EXCLUDED.get())
				.append(" types=");
		boolean first = true;
		for (Map.Entry<String, AtomicLong> entry : SPAWN_TYPES.entrySet()) {
			if (!first) {
				out.append(',');
			}
			first = false;
			out.append(entry.getKey()).append(':').append(entry.getValue().get());
		}
		synchronized (RECENT) {
			if (!RECENT.isEmpty()) {
				out.append(" recent=");
				out.append(String.join(" || ", RECENT));
			}
		}
		return out.toString();
	}

	private static void add(String line) {
		synchronized (RECENT) {
			RECENT.addLast(line);
			while (RECENT.size() > RECENT_LIMIT) {
				RECENT.removeFirst();
			}
		}
	}
}
