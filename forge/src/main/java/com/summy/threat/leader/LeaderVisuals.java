package com.summy.threat.leader;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.List;

/** 区域首领技能的高辨识粒子与阶段音效。 */
public final class LeaderVisuals {
	private static final float OUTLINE_SCALE = 1.3F;
	private static final float CORE_SCALE = 1.65F;
	private static final DustParticleOptions BLOCK_DUST =
			new DustParticleOptions(new Vector3f(0.48F, 0.38F, 0.27F), 1.2F);

	private LeaderVisuals() {
	}

	public static boolean supports(LeaderAbilityId id) {
		return id != null && switch (id.id()) {
			case "command_aura", "intimidation_aura", "bastion_aura",
					"suppression_aura", "bloodlust_aura", "tectonic_rupture",
					"armor_breaker", "hunt_ray", "gravity_pulse", "phase_lunge",
					"iron_guard", "collapse_field" -> true;
			default -> false;
		};
	}

	public static double auraRadius(LeaderAbilityId id) {
		if (id == null) {
			return 0.0D;
		}
		return switch (id.id()) {
			case "command_aura", "bastion_aura", "bloodlust_aura" -> 8.0D;
			case "intimidation_aura", "suppression_aura" -> 6.0D;
			default -> 0.0D;
		};
	}

	public static void legendaryResistance(LivingEntity entity) {
		if (!(entity.level() instanceof ServerLevel level)) {
			return;
		}
		bodyOrbit(level, entity, 0xFFE895, 0.9D, 12, entity.tickCount / 10.0D, 1.15F);
		level.sendParticles(ParticleTypes.END_ROD,
				entity.getX(), entity.getY() + entity.getBbHeight() * 0.65D, entity.getZ(),
				2, 0.35D, 0.35D, 0.35D, 0.01D);
	}

	public static void auraLeader(LivingEntity leader, LeaderAbilityId id) {
		if (!(leader.level() instanceof ServerLevel level) || !id.aura()) {
			return;
		}
		double radius = auraRadius(id);
		int count = radius >= 8.0D ? 40 : 32;
		groundRing(level, leader.position(), radius, id.color(), count, OUTLINE_SCALE, 0.08D);
		bodyOrbit(level, leader, id.color(), 0.75D, 8, leader.tickCount / 10.0D, 1.15F);
		switch (id.id()) {
			case "command_aura", "bastion_aura" -> level.sendParticles(ParticleTypes.END_ROD,
					leader.getX(), leader.getY() + 0.25D, leader.getZ(),
					4, 0.8D, 0.25D, 0.8D, 0.005D);
			case "intimidation_aura" -> level.sendParticles(ParticleTypes.SMOKE,
					leader.getX(), leader.getY() + 0.35D, leader.getZ(),
					6, 1.0D, 0.45D, 1.0D, 0.01D);
			case "suppression_aura" -> level.sendParticles(ParticleTypes.SMOKE,
					leader.getX(), leader.getY() + 0.12D, leader.getZ(),
					6, 1.1D, 0.15D, 1.1D, 0.005D);
			case "bloodlust_aura" -> level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
					leader.getX(), leader.getY() + 0.5D, leader.getZ(),
					6, 0.8D, 0.55D, 0.8D, 0.01D);
			default -> {
			}
		}
	}

	public static void auraTargets(LivingEntity leader, LeaderAbilityId id,
			List<LivingEntity> targets) {
		if (!(leader.level() instanceof ServerLevel level) || targets.isEmpty()) {
			return;
		}
		DustParticleOptions dust = dust(id.color(), 1.05F);
		for (LivingEntity target : targets) {
			groundRing(level, target.position(), 0.48D, id.color(), 8, 1.0F, 0.06D);
			level.sendParticles(dust, target.getX(), target.getY() + target.getBbHeight() * 0.7D,
					target.getZ(), 3, 0.25D, 0.2D, 0.25D, 0.005D);
		}
	}

	public static void tectonicWarning(ServerLevel level, Vec3 point, long elapsed) {
		double progress = Math.min(1.0D, elapsed / 20.0D);
		groundRing(level, point, 4.0D, LeaderAbilityId.TECTONIC_RUPTURE.color(),
				36, OUTLINE_SCALE, 0.07D);
		groundRing(level, point, 3.7D - progress * 1.1D,
				LeaderAbilityId.TECTONIC_RUPTURE.color(), 18, 1.05F, 0.12D);
		radialLines(level, point, 0.8D, 3.8D, 4, 5,
				LeaderAbilityId.TECTONIC_RUPTURE.color(), 1.15F);
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK,
						Blocks.COARSE_DIRT.defaultBlockState()),
				point.x, point.y + 0.08D, point.z, 4, 2.2D, 0.05D, 2.2D, 0.02D);
		if (elapsed >= 15L) {
			groundRing(level, point, 4.0D, 0xFFE5A3, 16, 1.0F, 0.18D,
					ParticleTypes.END_ROD);
		}
	}

	public static void tectonicImpact(ServerLevel level, Vec3 point) {
		int color = LeaderAbilityId.TECTONIC_RUPTURE.color();
		level.sendParticles(dust(color, CORE_SCALE),
				point.x, point.y + 0.2D, point.z, 72, 2.3D, 0.35D, 2.3D, 0.04D);
		groundRing(level, point, 4.0D, color, 48, OUTLINE_SCALE, 0.06D);
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK,
						Blocks.COARSE_DIRT.defaultBlockState()),
				point.x, point.y + 0.15D, point.z, 16, 2.0D, 0.25D, 2.0D, 0.04D);
		level.sendParticles(ParticleTypes.CRIT,
				point.x, point.y + 0.25D, point.z, 12, 1.4D, 0.3D, 1.4D, 0.04D);
	}

	public static void armorWarning(ServerLevel level, LivingEntity target, long elapsed) {
		Vec3 point = target.position();
		int color = LeaderAbilityId.ARMOR_BREAKER.color();
		groundRing(level, point, 1.2D, color, 20, OUTLINE_SCALE, 0.06D);
		verticalBeam(level, point, 1.4D, color, 3, 0.2D);
		if (elapsed >= 12L) {
			groundRing(level, point, 1.55D, 0xFFD5A8, 12, 1.0F, 0.16D,
					ParticleTypes.END_ROD);
		}
	}

	public static void armorImpact(ServerLevel level, LivingEntity target) {
		int color = LeaderAbilityId.ARMOR_BREAKER.color();
		Vec3 point = target.position();
		level.sendParticles(ParticleTypes.CRIT,
				point.x, point.y + target.getBbHeight() * 0.55D, point.z,
				40, 0.9D, 0.55D, 0.9D, 0.08D);
		level.sendParticles(dust(color, CORE_SCALE),
				point.x, point.y + 0.3D, point.z, 24, 1.2D, 0.45D, 1.2D, 0.03D);
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK,
						Blocks.DEEPSLATE.defaultBlockState()),
				point.x, point.y + 0.15D, point.z, 16, 0.9D, 0.25D, 0.9D, 0.04D);
	}

	public static void rayCharge(ServerLevel level, LivingEntity owner, Vec3 origin,
			Vec3 direction, long elapsed) {
		int color = LeaderAbilityId.HUNT_RAY.color();
		Vec3 forward = horizontalOrFallback(direction).scale(0.9D);
		double spin = elapsed * 0.55D;
		DustParticleOptions dust = dust(color, CORE_SCALE);
		for (int i = 0; i < 8; i++) {
			double angle = spin + Math.PI * 2.0D * i / 8.0D;
			Vec3 point = origin.add(Math.cos(angle) * 0.55D, Math.sin(angle) * 0.45D, 0.0D)
					.add(forward);
			level.sendParticles(dust, point.x, point.y, point.z,
					1, 0.02D, 0.02D, 0.02D, 0.0D);
		}
		if (elapsed >= 8L) {
			Vec3 end = rayEndpoint(level, owner, origin, direction, 20.0D);
			line(level, origin, end, color, 1.0D, 0.9F, false);
			level.sendParticles(ParticleTypes.END_ROD, end.x, end.y, end.z,
					3, 0.2D, 0.2D, 0.2D, 0.01D);
		}
	}

	public static void rayImpact(ServerLevel level, Vec3 origin, Vec3 end) {
		int color = LeaderAbilityId.HUNT_RAY.color();
		Vec3 direction = end.subtract(origin);
		if (direction.lengthSqr() < 1.0E-5D) {
			return;
		}
		Vec3 normal = direction.normalize();
		Vec3 side = perpendicular(normal).scale(0.28D);
		double length = direction.length();
		DustParticleOptions edge = dust(color, OUTLINE_SCALE);
		for (double distance = 0.0D; distance <= length; distance += 0.5D) {
			Vec3 point = origin.add(normal.scale(distance));
			level.sendParticles(ParticleTypes.END_ROD,
					point.x, point.y, point.z, 1, 0.03D, 0.03D, 0.03D, 0.0D);
			level.sendParticles(edge, point.x + side.x, point.y + side.y, point.z + side.z,
					1, 0.02D, 0.02D, 0.02D, 0.0D);
			level.sendParticles(edge, point.x - side.x, point.y - side.y, point.z - side.z,
					1, 0.02D, 0.02D, 0.02D, 0.0D);
		}
		level.sendParticles(ParticleTypes.CRIT, end.x, end.y, end.z,
				18, 0.35D, 0.35D, 0.35D, 0.04D);
	}

	public static void gravityWarning(ServerLevel level, Vec3 center, long elapsed) {
		double progress = Math.min(1.0D, elapsed / 20.0D);
		double radius = 8.0D - 6.5D * progress;
		int count = Math.max(24, (int) Math.ceil(radius * 4.5D));
		groundRing(level, center, radius, LeaderAbilityId.GRAVITY_PULSE.color(),
				count, OUTLINE_SCALE, 0.08D);
		level.sendParticles(ParticleTypes.DRAGON_BREATH,
				center.x, center.y + 0.5D, center.z,
				8, Math.max(0.3D, radius * 0.35D), 0.4D,
				Math.max(0.3D, radius * 0.35D), 0.01D);
		if (elapsed >= 15L) {
			groundRing(level, center, radius, 0xE8CCFF, 16, 1.0F, 0.18D,
					ParticleTypes.END_ROD);
		}
	}

	public static void gravityImpact(ServerLevel level, Vec3 center) {
		int color = LeaderAbilityId.GRAVITY_PULSE.color();
		groundRing(level, center, 8.0D, color, 64, OUTLINE_SCALE, 0.07D);
		level.sendParticles(dust(color, CORE_SCALE),
				center.x, center.y + 0.25D, center.z,
				80, 4.2D, 0.45D, 4.2D, 0.06D);
		level.sendParticles(ParticleTypes.END_ROD,
				center.x, center.y + 0.35D, center.z,
				24, 3.2D, 0.3D, 3.2D, 0.03D);
		level.sendParticles(ParticleTypes.DRAGON_BREATH,
				center.x, center.y + 0.5D, center.z,
				20, 2.5D, 0.5D, 2.5D, 0.03D);
	}

	public static void gravityPull(LivingEntity owner, LivingEntity target) {
		if (!(owner.level() instanceof ServerLevel level) || owner.tickCount % 2 != 0) {
			return;
		}
		Vec3 start = target.position().add(0.0D, target.getBbHeight() * 0.55D, 0.0D);
		Vec3 end = owner.position().add(0.0D, owner.getBbHeight() * 0.55D, 0.0D);
		line(level, start, end, LeaderAbilityId.GRAVITY_PULSE.color(),
				1.5D, 0.95F, false);
		level.sendParticles(ParticleTypes.END_ROD, target.getX(),
				target.getY() + target.getBbHeight() * 0.55D, target.getZ(),
				2, 0.2D, 0.2D, 0.2D, 0.01D);
	}

	public static void phaseWarning(ServerLevel level, Vec3 start, Vec3 end,
			Vec3 direction, long elapsed) {
		Vec3 side = perpendicular(direction).scale(0.75D);
		line(level, start.add(side), end.add(side), LeaderAbilityId.PHASE_LUNGE.color(),
				1.0D, 1.1F, false);
		line(level, start.subtract(side), end.subtract(side),
				LeaderAbilityId.PHASE_LUNGE.color(), 1.0D, 1.1F, false);
		groundRing(level, end, 0.75D, LeaderAbilityId.PHASE_LUNGE.color(),
				20, OUTLINE_SCALE, 0.08D);
		if (elapsed >= 12L) {
			groundRing(level, end, 1.0D, 0xFFF0A0, 12, 1.0F, 0.16D,
					ParticleTypes.END_ROD);
		}
	}

	public static void phaseImpact(ServerLevel level, Vec3 start, Vec3 end, Vec3 direction) {
		int color = LeaderAbilityId.PHASE_LUNGE.color();
		Vec3 side = perpendicular(direction).scale(0.75D);
		line(level, start, end, 0xFFF1A6, 0.75D, 1.0F, true);
		line(level, start.add(side), end.add(side), color, 0.9D, 1.2F, false);
		line(level, start.subtract(side), end.subtract(side), color, 0.9D, 1.2F, false);
		double length = end.subtract(start).length();
		for (double fraction : new double[] {0.3D, 0.6D, 0.9D}) {
			Vec3 point = start.add(end.subtract(start).scale(fraction));
			level.sendParticles(ParticleTypes.END_ROD,
					point.x, point.y + 0.65D, point.z,
					5, 0.2D, 0.65D, 0.2D, 0.01D);
		}
		level.sendParticles(dust(color, CORE_SCALE), start.x, start.y + 0.3D, start.z,
				(int) Math.max(24.0D, length * 5.0D), 1.0D, 0.3D, 1.0D, 0.03D);
	}

	public static void ironGuard(LivingEntity owner, int hits) {
		if (!(owner.level() instanceof ServerLevel level)) {
			return;
		}
		int color = LeaderAbilityId.IRON_GUARD.color();
		double[] heights = {0.35D, owner.getBbHeight() * 0.55D, owner.getBbHeight() * 0.9D};
		for (double height : heights) {
			ringAround(level, owner.position().add(0.0D, height, 0.0D),
					0.72D, color, 12, 1.15F);
		}
		double phase = owner.tickCount * 0.18D;
		for (int i = 0; i < 2; i++) {
			double angle = phase + Math.PI * i;
			level.sendParticles(ParticleTypes.END_ROD,
					owner.getX() + Math.cos(angle) * 0.78D,
					owner.getY() + owner.getBbHeight() * 0.6D,
					owner.getZ() + Math.sin(angle) * 0.78D,
					1, 0.02D, 0.02D, 0.02D, 0.0D);
		}
		if (hits > 0) {
			level.sendParticles(ParticleTypes.CRIT,
					owner.getX(), owner.getY() + owner.getBbHeight() * 0.55D, owner.getZ(),
					hits * 4, 0.7D, 0.5D, 0.7D, 0.02D);
		}
	}

	public static void ironHit(LivingEntity owner, int hits) {
		if (!(owner.level() instanceof ServerLevel level)) {
			return;
		}
		groundRing(level, owner.position(), 0.95D, LeaderAbilityId.IRON_GUARD.color(),
				16, OUTLINE_SCALE, 0.09D);
		level.sendParticles(ParticleTypes.CRIT,
				owner.getX(), owner.getY() + owner.getBbHeight() * 0.55D, owner.getZ(),
				16, 0.75D, 0.45D, 0.75D, 0.03D);
		play(level, owner.position(), net.minecraft.sounds.SoundEvents.BELL_RESONATE,
				0.5F, 1.15F + Math.min(2, hits) * 0.08F);
	}

	public static void ironCounter(LivingEntity owner) {
		if (!(owner.level() instanceof ServerLevel level)) {
			return;
		}
		int color = LeaderAbilityId.IRON_GUARD.color();
		groundRing(level, owner.position(), 5.0D, color, 48, OUTLINE_SCALE, 0.08D);
		level.sendParticles(dust(color, CORE_SCALE),
				owner.getX(), owner.getY() + 0.35D, owner.getZ(),
				64, 2.8D, 0.45D, 2.8D, 0.05D);
		level.sendParticles(ParticleTypes.END_ROD,
				owner.getX(), owner.getY() + 0.35D, owner.getZ(),
				20, 2.2D, 0.35D, 2.2D, 0.03D);
	}

	public static void collapseWarning(ServerLevel level, Vec3 point, long elapsed) {
		double progress = Math.min(1.0D, elapsed / 20.0D);
		int color = LeaderAbilityId.COLLAPSE_FIELD.color();
		groundRing(level, point, 5.0D, color, 36, OUTLINE_SCALE, 0.07D);
		groundRing(level, point, 1.0D + 3.8D * progress, color,
				18, 1.0F, 0.12D);
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK,
						Blocks.COARSE_DIRT.defaultBlockState()),
				point.x, point.y + 0.08D, point.z, 4, 2.8D, 0.05D, 2.8D, 0.02D);
		if (elapsed >= 15L) {
			groundRing(level, point, 5.0D, 0xD9B18C, 16, 1.0F, 0.18D,
					ParticleTypes.END_ROD);
		}
	}

	public static void collapseBorder(ServerLevel level, Vec3 point) {
		int color = LeaderAbilityId.COLLAPSE_FIELD.color();
		groundRing(level, point, 5.0D, color, 40, OUTLINE_SCALE, 0.07D);
		level.sendParticles(ParticleTypes.SMOKE,
				point.x, point.y + 0.25D, point.z,
				8, 3.3D, 0.25D, 3.3D, 0.015D);
		verticalBeam(level, point, 1.6D, color, 4, 0.3D);
	}

	public static void collapsePulse(ServerLevel level, Vec3 point) {
		int color = LeaderAbilityId.COLLAPSE_FIELD.color();
		level.sendParticles(BLOCK_DUST,
				point.x, point.y + 0.15D, point.z, 24, 3.6D, 0.2D, 3.6D, 0.025D);
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK,
						Blocks.COARSE_DIRT.defaultBlockState()),
				point.x, point.y + 0.1D, point.z, 8, 3.0D, 0.15D, 3.0D, 0.025D);
		level.sendParticles(ParticleTypes.CRIT,
				point.x, point.y + 0.3D, point.z, 32, 1.4D, 0.25D, 1.4D, 0.03D);
	}

	public static void hitFlash(LivingEntity target, int color) {
		if (!(target.level() instanceof ServerLevel level)) {
			return;
		}
		level.sendParticles(dust(color, CORE_SCALE),
				target.getX(), target.getY() + target.getBbHeight() * 0.55D, target.getZ(),
				14, 0.55D, 0.4D, 0.55D, 0.03D);
	}

	public static void play(ServerLevel level, Vec3 point, SoundEvent sound,
			float volume, float pitch) {
		level.playSound(null, point.x, point.y, point.z, sound, SoundSource.HOSTILE, volume, pitch);
	}

	private static void groundRing(ServerLevel level, Vec3 center, double radius,
			int color, int count, float scale, double yOffset) {
		groundRing(level, center, radius, color, count, scale, yOffset, null);
	}

	private static void groundRing(ServerLevel level, Vec3 center, double radius,
			int color, int count, float scale, double yOffset, ParticleOptions particle) {
		DustParticleOptions dust = particle == null ? dust(color, scale) : null;
		for (int i = 0; i < count; i++) {
			double angle = Math.PI * 2.0D * i / count;
			double x = center.x + Math.cos(angle) * radius;
			double z = center.z + Math.sin(angle) * radius;
			if (particle == null) {
				level.sendParticles(dust, x, center.y + yOffset, z,
						1, 0.02D, 0.0D, 0.02D, 0.0D);
			} else {
				level.sendParticles(particle, x, center.y + yOffset, z,
						1, 0.02D, 0.0D, 0.02D, 0.0D);
			}
		}
	}

	private static void ringAround(ServerLevel level, Vec3 center, double radius,
			int color, int count, float scale) {
		DustParticleOptions dust = dust(color, scale);
		for (int i = 0; i < count; i++) {
			double angle = Math.PI * 2.0D * i / count;
			level.sendParticles(dust,
					center.x + Math.cos(angle) * radius,
					center.y,
					center.z + Math.sin(angle) * radius,
					1, 0.02D, 0.02D, 0.02D, 0.0D);
		}
	}

	private static void bodyOrbit(ServerLevel level, LivingEntity entity, int color,
			double radius, int count, double phase, float scale) {
		DustParticleOptions dust = dust(color, scale);
		for (int i = 0; i < count; i++) {
			double angle = phase + Math.PI * 2.0D * i / count;
			level.sendParticles(dust,
					entity.getX() + Math.cos(angle) * radius,
					entity.getY() + entity.getBbHeight() * 0.5D
							+ Math.sin(angle * 2.0D) * entity.getBbHeight() * 0.22D,
					entity.getZ() + Math.sin(angle) * radius,
					1, 0.02D, 0.02D, 0.02D, 0.0D);
		}
	}

	private static void radialLines(ServerLevel level, Vec3 center, double inner,
			double outer, int lines, int points, int color, float scale) {
		DustParticleOptions dust = dust(color, scale);
		for (int line = 0; line < lines; line++) {
			double angle = Math.PI * 2.0D * line / lines + Math.PI / 4.0D;
			for (int i = 0; i < points; i++) {
				double distance = inner + (outer - inner) * i / Math.max(1, points - 1);
				level.sendParticles(dust,
						center.x + Math.cos(angle) * distance,
						center.y + 0.08D,
						center.z + Math.sin(angle) * distance,
						1, 0.03D, 0.0D, 0.03D, 0.0D);
			}
		}
	}

	private static void verticalBeam(ServerLevel level, Vec3 point, double height,
			int color, int count, double spread) {
		DustParticleOptions dust = dust(color, 1.15F);
		for (int i = 0; i < count; i++) {
			double y = height * i / Math.max(1, count - 1);
			level.sendParticles(dust, point.x, point.y + 0.2D + y, point.z,
					1, spread, 0.03D, spread, 0.0D);
		}
	}

	private static void line(ServerLevel level, Vec3 start, Vec3 end, int color,
			double step, float scale, boolean endRodCore) {
		Vec3 delta = end.subtract(start);
		double length = delta.length();
		if (length <= 1.0E-5D) {
			return;
		}
		Vec3 direction = delta.scale(1.0D / length);
		DustParticleOptions dust = dust(color, scale);
		for (double distance = 0.0D; distance <= length; distance += step) {
			Vec3 point = start.add(direction.scale(distance));
			if (endRodCore) {
				level.sendParticles(ParticleTypes.END_ROD,
						point.x, point.y, point.z, 1, 0.02D, 0.02D, 0.02D, 0.0D);
			} else {
				level.sendParticles(dust, point.x, point.y, point.z,
						1, 0.03D, 0.03D, 0.03D, 0.0D);
			}
		}
	}

	private static Vec3 rayEndpoint(ServerLevel level, LivingEntity owner,
			Vec3 origin, Vec3 direction, double maxLength) {
		Vec3 end = origin.add(direction.scale(maxLength));
		BlockHitResult hit = level.clip(new ClipContext(origin, end,
				ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
		return hit.getType() == HitResult.Type.MISS ? end : hit.getLocation();
	}

	private static Vec3 perpendicular(Vec3 direction) {
		Vec3 normal = direction.normalize();
		Vec3 side = normal.cross(new Vec3(0.0D, 1.0D, 0.0D));
		if (side.lengthSqr() < 1.0E-4D) {
			side = normal.cross(new Vec3(1.0D, 0.0D, 0.0D));
		}
		return side.normalize();
	}

	private static Vec3 horizontalOrFallback(Vec3 direction) {
		Vec3 horizontal = new Vec3(direction.x, 0.0D, direction.z);
		if (horizontal.lengthSqr() > 1.0E-4D) {
			return horizontal.normalize();
		}
		return new Vec3(0.0D, 0.0D, 1.0D);
	}

	private static DustParticleOptions dust(int color, float scale) {
		return new DustParticleOptions(rgb(color), scale);
	}

	private static Vector3f rgb(int color) {
		return new Vector3f(((color >> 16) & 0xFF) / 255.0F,
				((color >> 8) & 0xFF) / 255.0F, (color & 0xFF) / 255.0F);
	}
}
