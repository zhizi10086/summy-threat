package com.summy.threat.legendary;

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
			ThreatVisualEntity visual = ModLegendaryEntities.THREAT_VISUAL.get().create(level);
			if (visual != null) {
				Vec3 feet = feet(target);
				visual.initialize(ThreatVisualEntity.LEGENDARY, LegendaryActionId.JADE_LIGHT.id(),
						ThreatVisualEntity.BEAM, LegendaryActionId.JADE_LIGHT.color(), expiresAt);
				visual.setPos(feet.x, feet.y, feet.z);
				visual.update(ThreatVisualEntity.WARNING, 0.0F, (float) CONTACT_RADIUS, 0.0F,
						0.0F, ThreatVisualEntity.LOCKED, Vec3.ZERO);
				if (!level.addFreshEntity(visual)) {
					visual = null;
				}
			}
			beams.add(new Beam(target.getUUID(), visual, contactAnchor(target)));
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

	private static Vec3 feet(LivingEntity entity) {
		return new Vec3(entity.getX(), entity.getBoundingBox().minY, entity.getZ());
	}

	private static Vec3 contactAnchor(LivingEntity entity) {
		return new Vec3(entity.getX(),
				LegendaryActionMath.jadeContactAnchorY(entity.getY(), entity.getBbHeight()), entity.getZ());
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
				LivingEntity target = entity(level, beam.targetId);
				boolean invalid = !LegendaryActionRuntime.validPlayer(context.owner(), target)
						|| target.level() != level
						|| target.position().distanceTo(beam.contact) > 32.0D;
				if (invalid) {
					beam.discardVisual();
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
				Vec3 goal = contactAnchor(target);
				Vec3 delta = goal.subtract(beam.contact);
				double distance = delta.length();
				if (distance > 1.0E-6D) {
					Vec3 step = delta.scale(Math.min(distance, SPEED) / distance);
					beam.contact = beam.contact.add(step);
				}
				if (beam.hasVisual()) {
					Vec3 feet = feet(target);
					beam.visual.setPos(feet.x, feet.y, feet.z);
					beam.offset = new Vec3(beam.contact.x - feet.x,
							LegendaryActionMath.jadeVisualAnchorY(beam.contact.y, target.getBbHeight()) - feet.y,
							beam.contact.z - feet.z);
				}
			}
			if (age < WARNING_TICKS) {
				for (Beam beam : beams) {
					updateVisual(beam, ThreatVisualEntity.WARNING, age / (float) WARNING_TICKS);
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
				beam.impactTicks = Math.max(0, beam.impactTicks - 1);
				if (damageTick) {
					Player victim = chooseVictim(context, beam, hitThisCycle);
					if (victim != null && hitThisCycle.add(victim.getUUID())) {
						int previous = hitCounts.getOrDefault(victim.getUUID(), 0);
						int multiplier = LegendaryActionMath.jadeMultiplier(previous);
						if (LegendaryActionCombat.hit(context, victim,
								LegendaryActionCombat.DamageKind.INDIRECT_MAGIC, 12.0D * multiplier)) {
							hitCounts.put(victim.getUUID(), previous + 1);
							victim.setSecondsOnFire(2);
							beam.impactTicks = 5;
							beam.intensity = multiplier / 12.0F;
						}
					}
				}
				byte phase = beam.impactTicks > 0 ? ThreatVisualEntity.IMPACT
						: age >= WARNING_TICKS + ACTIVE_TICKS - 8 ? ThreatVisualEntity.FADING
						: ThreatVisualEntity.ACTIVE;
				updateVisual(beam, phase, (age - WARNING_TICKS) / (float) ACTIVE_TICKS);
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
							horizontalDistanceSqr(player.position(), beam.contact)))
					.orElse(null);
		}

		private static List<Player> contacts(ServerLevel level, Beam beam) {
			AABB volume = new AABB(
					beam.contact.x - CONTACT_RADIUS,
					LegendaryActionMath.jadeContactMinY(
							beam.contact.y, CONTACT_HEIGHT),
					beam.contact.z - CONTACT_RADIUS,
					beam.contact.x + CONTACT_RADIUS,
					LegendaryActionMath.jadeContactMaxY(beam.contact.y),
					beam.contact.z + CONTACT_RADIUS);
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

		private static void updateVisual(Beam beam, byte phase, float progress) {
			if (beam.hasVisual()) {
				beam.visual.update(phase, progress, (float) CONTACT_RADIUS, 0.0F,
						beam.impactTicks == 0 ? 0.0F : beam.intensity, ThreatVisualEntity.LOCKED, beam.offset);
			}
		}

		@Override
		public void stop(LegendaryActionContext context, StopReason reason) {
			for (Beam beam : beams) {
				beam.discardVisual();
			}
			beams.clear();
		}
	}

	private static LivingEntity entity(ServerLevel level, UUID id) {
		return level.getEntity(id) instanceof LivingEntity living ? living : null;
	}

	private static final class Beam {
		final UUID targetId;
		@javax.annotation.Nullable
		final ThreatVisualEntity visual;
		Vec3 contact;
		Vec3 offset = Vec3.ZERO;
		int impactTicks;
		float intensity;

		Beam(UUID targetId, @javax.annotation.Nullable ThreatVisualEntity visual, Vec3 contact) {
			this.targetId = targetId;
			this.visual = visual;
			this.contact = contact;
		}

		boolean hasVisual() {
			return visual != null && !visual.isRemoved();
		}

		void discardVisual() {
			if (hasVisual()) {
				visual.discard();
			}
		}
	}
}
