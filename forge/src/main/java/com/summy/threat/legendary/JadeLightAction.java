package com.summy.threat.legendary;

import com.summy.threat.SummyThreat;
import com.summy.threat.legendary.JadeLightBeamEntity;
import com.summy.threat.legendary.LegendaryActionCombat;
import com.summy.threat.legendary.LegendaryActionContext;
import com.summy.threat.legendary.LegendaryActionHandler;
import com.summy.threat.legendary.LegendaryActionId;
import com.summy.threat.legendary.LegendaryActionInstance;
import com.summy.threat.legendary.LegendaryVisuals;
import com.summy.threat.legendary.ModLegendaryEntities;
import com.summy.threat.leader.LeaderTargeting;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** 翠玉之光：两道三维慢速追踪光柱。 */
final class JadeLightAction implements LegendaryActionHandler {
	private static final int WARNING_TICKS = 30;
	private static final int ACTIVE_TICKS = 160;
	private static final double SPEED = 0.13D;
	private static final double CONTACT_RADIUS = 0.4D;
	private static final double CONTACT_HEIGHT = 4.0D;

	@Override
	public LegendaryActionInstance start(LegendaryActionContext context) {
		Map<UUID, Player> selected = selectTargets(context);
		if (selected.isEmpty()) {
			return null;
		}
		ServerLevel level = context.level();
		long expiresAt = level.getGameTime() + WARNING_TICKS + ACTIVE_TICKS + 20L;
		List<Beam> beams = new ArrayList<>();
		for (Player target : selected.values()) {
			JadeLightBeamEntity entity = ModLegendaryEntities.JADE_LIGHT_BEAM.get().create(level);
			if (entity == null) {
				continue;
			}
			Vec3 start = head(target);
			entity.setPos(start.x, start.y, start.z);
			entity.setExpiresAt(expiresAt);
			level.addFreshEntity(entity);
			beams.add(new Beam(target.getUUID(), entity));
		}
		if (beams.isEmpty()) {
			return null;
		}
		LegendaryVisuals.sound(level, context.center(), SoundEvents.BEACON_ACTIVATE,
				0.7F, 1.7F);
		return new Instance(beams);
	}

	private static Map<UUID, Player> selectTargets(LegendaryActionContext context) {
		Map<UUID, Player> selected = new LinkedHashMap<>();
		if (context.target() instanceof Player target
				&& LegendaryActionRuntime.validPlayer(context.owner(), target)
				&& (context.test() || target != context.owner())
				&& target.distanceTo(context.owner()) <= 32.0D) {
			selected.put(target.getUUID(), target);
		}
		List<Player> nearby = LegendaryActionCombat.playerTargets(context,
				context.owner().position(), 32.0D);
		nearby.sort(java.util.Comparator.comparingDouble(
				target -> target.distanceToSqr(context.owner())));
		for (Player target : nearby) {
			if (selected.size() >= 2) {
				break;
			}
			selected.putIfAbsent(target.getUUID(), target);
		}
		return selected;
	}

	private static Vec3 head(LivingEntity entity) {
		return entity.position().add(0.0D, entity.getBbHeight() + 0.35D, 0.0D);
	}

	private static final class Instance implements LegendaryActionInstance {
		private final List<Beam> beams;
		private final Map<UUID, Integer> hitCounts = new HashMap<>();

		private Instance(List<Beam> beams) {
			this.beams = beams;
		}

		@Override
		public boolean tick(LegendaryActionContext context, int age) {
			ServerLevel level = context.level();
			beams.removeIf(beam -> {
				if (beam.entity.isRemoved() || !beam.entity.isAlive()) {
					return true;
				}
				LivingEntity target = entity(level, beam.targetId);
				boolean invalid = !LegendaryActionRuntime.validPlayer(context.owner(), target)
						|| target.level() != level
						|| target.position().distanceTo(beam.entity.position()) > 32.0D;
				if (invalid) {
					beam.entity.discard();
				}
				return invalid;
			});
			if (beams.isEmpty() || age >= WARNING_TICKS + ACTIVE_TICKS) {
				return false;
			}
			for (Beam beam : beams) {
				LivingEntity target = entity(level, beam.targetId);
				if (target == null) {
					continue;
				}
				Vec3 goal = head(target);
				Vec3 delta = goal.subtract(beam.entity.position());
				double distance = delta.length();
				if (distance > 1.0E-6D) {
					Vec3 step = delta.scale(Math.min(distance, SPEED) / distance);
					beam.entity.setPos(beam.entity.getX() + step.x,
							beam.entity.getY() + step.y,
							beam.entity.getZ() + step.z);
				}
			}
			if (age < WARNING_TICKS) {
				for (Beam beam : beams) {
					LegendaryVisuals.ring(level, beam.entity.position(), 0.7D,
							LegendaryActionId.JADE_LIGHT.color(), 12, 0.8F, 0.05D);
				}
				if (age % 5 == 0) {
					LegendaryVisuals.sound(level, context.center(),
							SoundEvents.NOTE_BLOCK_CHIME.value(), 0.45F,
							1.0F + age / (float) WARNING_TICKS);
				}
				return true;
			}
			boolean damageTick = (age - WARNING_TICKS) % 10 == 0;
			Set<UUID> hitThisCycle = new HashSet<>();
			for (Beam beam : beams) {
				drawBeam(level, beam.entity.position());
				if (!damageTick) {
					continue;
				}
				Player victim = chooseVictim(context, beam, hitThisCycle);
				if (victim == null || !hitThisCycle.add(victim.getUUID())) {
					continue;
				}
				int previous = hitCounts.getOrDefault(victim.getUUID(), 0);
				int multiplier = com.summy.threat.legendary.LegendaryActionMath
						.jadeMultiplier(previous);
				boolean landed = LegendaryActionCombat.hit(context, victim,
						LegendaryActionCombat.DamageKind.INDIRECT_MAGIC,
						12.0D * multiplier);
				if (landed) {
					hitCounts.put(victim.getUUID(), previous + 1);
					victim.setSecondsOnFire(2);
					LegendaryVisuals.burst(level, victim.getEyePosition(),
							LegendaryActionId.JADE_LIGHT.color(), 10, 0.4D, 1.1F);
				}
			}
			return true;
		}

		private static Player chooseVictim(LegendaryActionContext context, Beam beam,
				Set<UUID> alreadyHit) {
			List<Player> contacts = contacts(context.level(), beam);
			if (contacts.isEmpty()) {
				return null;
			}
			for (Player player : contacts) {
				if (player.getUUID().equals(beam.targetId)
						&& !alreadyHit.contains(player.getUUID())) {
					return player;
				}
			}
			return contacts.stream()
					.filter(player -> !alreadyHit.contains(player.getUUID()))
					.min(java.util.Comparator.comparingDouble(player ->
							horizontalDistanceSqr(player.position(), beam.entity.position())))
					.orElse(null);
		}

		private static List<Player> contacts(ServerLevel level, Beam beam) {
			AABB volume = new AABB(
					beam.entity.getX() - CONTACT_RADIUS,
					LegendaryActionMath.jadeContactMinY(
							beam.entity.getY(), CONTACT_HEIGHT),
					beam.entity.getZ() - CONTACT_RADIUS,
					beam.entity.getX() + CONTACT_RADIUS,
					LegendaryActionMath.jadeContactMaxY(beam.entity.getY()),
					beam.entity.getZ() + CONTACT_RADIUS);
			return level.getEntitiesOfClass(Player.class, volume.inflate(0.01D),
					player -> player.isAlive()
							&& !player.isCreative()
							&& !player.isSpectator()
							&& player.getBoundingBox().intersects(volume));
		}

		private static double horizontalDistanceSqr(Vec3 first, Vec3 second) {
			double dx = first.x - second.x;
			double dz = first.z - second.z;
			return dx * dx + dz * dz;
		}

		private static void drawBeam(ServerLevel level, Vec3 point) {
			LegendaryVisuals.vertical(level, point,
					LegendaryActionId.JADE_LIGHT.color(), CONTACT_HEIGHT, 0.3D, 1.0F);
			LegendaryVisuals.ring(level, point, 0.45D,
					LegendaryActionId.JADE_LIGHT.color(), 10, 0.9F, 0.05D);
			level.sendParticles(LegendaryVisuals.endRod(),
					point.x, point.y + 0.5D, point.z,
					1, 0.05D, 0.2D, 0.05D, 0.0D);
		}

		@Override
		public void stop(LegendaryActionContext context, StopReason reason) {
			for (Beam beam : beams) {
				beam.entity.discard();
			}
			beams.clear();
		}
	}

	private static LivingEntity entity(ServerLevel level, UUID id) {
		return level.getEntity(id) instanceof LivingEntity living ? living : null;
	}

	private record Beam(UUID targetId, JadeLightBeamEntity entity) {
	}
}
