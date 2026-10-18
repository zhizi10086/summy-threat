package com.summy.threat.pcf;

import com.summy.threat.scaling.MonsterScaler;
import com.summy.threat.scaling.ScalingState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

/**
 * 新刷怪物的 TL 选择：手动覆盖优先，否则由 PCF/CDF 与区域倍率共同计算。
 */
public final class TlSelector {
	private TlSelector() {
	}

	public static int select(ServerLevel level, LivingEntity entity, MonsterScaler.Kind kind) {
		int manual = ScalingState.globalTl();
		if (manual >= 0) {
			return manual;
		}
		if (!ScalingState.enabled()) {
			return 0;
		}
		double pcf = PcfManager.selectedPcf(level, entity.position(), kind);
		double cdf = CdfManager.current(level, entity.blockPosition());
		SpawnRegion region = SpawnRegion.forEntity(level, entity);
		double value = Math.min(pcf, cdf) * region.multiplier()
				+ (level.random.nextDouble() * 2.0D - 1.0D);
		int tl = clamp((int) Math.round(value), 0, 20);
		if (kind == MonsterScaler.Kind.LEADER) {
			tl = clamp(tl, 10, Math.min(20, region.leaderCap()));
		}
		if (kind == MonsterScaler.Kind.LEADER || kind == MonsterScaler.Kind.BOSS) {
			entity.getPersistentData().putDouble(MonsterScaler.TAG_PCF, pcf);
		}
		return tl;
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}
}
