package com.summy.threat.pcf;

import com.summy.threat.scaling.MonsterScaler;
import com.summy.threat.scaling.ScalingState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;

/**
 * 新刷怪物的 TL 选择：手动覆盖优先，否则由 PCF/CDF 与区域倍率共同计算。
 */
public final class TlSelector {
	private TlSelector() {
	}

	public static int select(ServerLevel level, LivingEntity entity, MonsterScaler.Kind kind) {
		return select(level, entity, kind, SpawnRegion.forEntity(level, entity));
	}

	public static int select(ServerLevel level, LivingEntity entity, MonsterScaler.Kind kind,
			MobSpawnType spawnType) {
		return select(level, entity, kind,
				SpawnRegion.detect(level, entity.position(), spawnType));
	}

	private static int select(ServerLevel level, LivingEntity entity, MonsterScaler.Kind kind,
			SpawnRegion region) {
		int manual = ScalingState.globalTl();
		if (manual >= 0) {
			return manual;
		}
		if (!ScalingState.enabled()) {
			return 0;
		}
		double pcf = PcfManager.selectedPcf(level, entity.position(), kind);
		double cdf = CdfManager.current(level, entity.blockPosition());
		double value = Math.min(pcf, cdf) * region.multiplier()
				+ (level.random.nextDouble() * 2.0D - 1.0D);
		int tl = clamp((int) Math.round(value), 0, 20);
		if (kind == MonsterScaler.Kind.LEADER || kind == MonsterScaler.Kind.BOSS) {
			entity.getPersistentData().putDouble(MonsterScaler.TAG_PCF, pcf);
		}
		return tl;
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}
}
