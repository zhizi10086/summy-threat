package com.summy.threat.legendary;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** 传奇动作共用的高辨识度粒子与音效。 */
public final class LegendaryVisuals {
	private LegendaryVisuals() {
	}

	public static DustParticleOptions dust(int color, float scale) {
		return new DustParticleOptions(new Vector3f(
				((color >> 16) & 0xFF) / 255.0F,
				((color >> 8) & 0xFF) / 255.0F,
				(color & 0xFF) / 255.0F), scale);
	}

	public static int dragonColor(LivingEntity owner) {
		String id = EntityType.getKey(owner.getType()).toString();
		return switch (id) {
			case "iceandfire:fire_dragon" -> 0xF07A32;
			case "iceandfire:ice_dragon" -> 0x65D8FF;
			case "iceandfire:lightning_dragon" -> 0xB58CFF;
			case "minecraft:ender_dragon" -> 0x8F5CFF;
			default -> LegendaryActionId.FRIGHTFUL_PRESENCE.color();
		};
	}

	public static void ring(ServerLevel level, Vec3 center, double radius, int color,
			int count, float scale, double yOffset) {
		ParticleOptions particle = dust(color, scale);
		for (int i = 0; i < count; i++) {
			double angle = Math.PI * 2.0D * i / count;
			double x = center.x + Math.cos(angle) * radius;
			double y = center.y + yOffset;
			double z = center.z + Math.sin(angle) * radius;
			level.sendParticles(particle,
					x, y, z,
					1, 0.0D, 0.0D, 0.0D, 0.0D);
			if (i % 6 == 0) {
				level.sendParticles(ParticleTypes.END_ROD, x, y, z,
						1, 0.0D, 0.0D, 0.0D, 0.0D);
			}
		}
	}

	public static void ringWithGap(ServerLevel level, Vec3 center, double radius,
			int color, int count, float scale, double yOffset,
			double gapCenter, double gapHalfAngle) {
		ParticleOptions particle = dust(color, scale);
		for (int i = 0; i < count; i++) {
			double angle = Math.PI * 2.0D * i / count;
			if (angularDistance(angle, gapCenter) <= gapHalfAngle) {
				continue;
			}
			double x = center.x + Math.cos(angle) * radius;
			double y = center.y + yOffset;
			double z = center.z + Math.sin(angle) * radius;
			level.sendParticles(particle,
					x, y, z,
					1, 0.0D, 0.0D, 0.0D, 0.0D);
			if (i % 6 == 0) {
				level.sendParticles(ParticleTypes.END_ROD, x, y, z,
						1, 0.0D, 0.0D, 0.0D, 0.0D);
			}
		}
	}

	public static double angularDistance(double first, double second) {
		double delta = Math.abs(Mth.wrapDegrees((float) Math.toDegrees(first - second)));
		return Math.toRadians(delta);
	}

	public static void line(ServerLevel level, Vec3 start, Vec3 end, int color,
			double spacing, float scale) {
		Vec3 delta = end.subtract(start);
		double length = delta.length();
		if (length < 1.0E-6D) {
			return;
		}
		Vec3 direction = delta.scale(1.0D / length);
		ParticleOptions particle = dust(color, scale);
		for (double distance = 0.0D; distance <= length; distance += spacing) {
			Vec3 point = start.add(direction.scale(distance));
			level.sendParticles(particle, point.x, point.y, point.z,
					1, 0.0D, 0.0D, 0.0D, 0.0D);
			if (Math.round(distance / spacing) % 6 == 0) {
				level.sendParticles(ParticleTypes.END_ROD,
						point.x, point.y, point.z,
						1, 0.0D, 0.0D, 0.0D, 0.0D);
			}
		}
	}

	public static void vertical(ServerLevel level, Vec3 base, int color, double height,
			double spacing, float scale) {
		ParticleOptions particle = dust(color, scale);
		for (double y = 0.0D; y <= height; y += spacing) {
			level.sendParticles(particle, base.x, base.y + y, base.z,
					1, 0.0D, 0.0D, 0.0D, 0.0D);
			if (Math.round(y / spacing) % 5 == 0) {
				level.sendParticles(ParticleTypes.END_ROD,
						base.x, base.y + y, base.z,
						1, 0.0D, 0.0D, 0.0D, 0.0D);
			}
		}
	}

	public static void burst(ServerLevel level, Vec3 center, int color, int count,
			double spread, float scale) {
		level.sendParticles(dust(color, scale), center.x, center.y, center.z,
				count, spread, spread * 0.35D, spread, 0.02D);
	}

	public static void sound(ServerLevel level, Vec3 position, SoundEvent sound,
			float volume, float pitch) {
		level.playSound(null, position.x, position.y, position.z,
				sound, SoundSource.HOSTILE, volume, pitch);
	}

	public static ParticleOptions endRod() {
		return ParticleTypes.END_ROD;
	}
}
