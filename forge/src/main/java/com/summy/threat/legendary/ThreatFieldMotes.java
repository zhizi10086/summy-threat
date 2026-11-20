package com.summy.threat.legendary;

import net.minecraft.world.phys.Vec3;

/** 场内光点沿用技能的环带与扇区几何。 */
final class ThreatFieldMotes {
	static final int BUDGET = 24;
	static final double FULL_CIRCLE = 2.0D * Math.PI;

	private ThreatFieldMotes() {
	}

	record Band(double inner, double outer, double start, double span) {
		static Band ring(double inner, double outer) {
			return new Band(inner, outer, 0.0D, FULL_CIRCLE);
		}

		Vec3 sample(long seed, int slot, float time) {
			// 向内留出少量空间，让贴图本身也尽量避开安全区。
			double radial = 0.05D + 0.90D * drift(seed, slot * 4, time);
			double angular = 0.025D + 0.95D * drift(seed, slot * 4 + 1, time);
			double radius = Math.sqrt(inner * inner + radial * (outer * outer - inner * inner));
			double angle = start + span * angular;
			return new Vec3(Math.cos(angle) * radius, 0.0D, Math.sin(angle) * radius);
		}
	}

	static double unit(long seed, int index) {
		long value = seed + 0x9E3779B97F4A7C15L * (index + 1L);
		value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
		value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
		return ((value ^ (value >>> 31)) >>> 11) * 0x1.0p-53;
	}

	private static double drift(long seed, int index, float time) {
		double origin = unit(seed, index);
		double sway = 0.5D + 0.5D * Math.sin(origin * FULL_CIRCLE + time * 0.038D);
		return 0.92D * origin + 0.08D * sway;
	}
}
