package com.summy.threat.legendary;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** 巢穴裂变：四个裂口顺时针依次引爆。 */
final class LairBreachAction implements LegendaryActionHandler {
	private static final int WARNING_TICKS = 35;
	static final int VENT_INTERVAL = 5;
	static final int DURATION_TICKS = WARNING_TICKS + 22;
	static final double VENT_OFFSET = 6.0D;
	static final double VENT_RADIUS = 3.2D;
	static final float VISUAL_CULL_RADIUS = 10.0F;

	static int ventIndex(int age) {
		if (age < WARNING_TICKS || age >= DURATION_TICKS) {
			return -1;
		}
		return Math.min(3, (age - WARNING_TICKS) / VENT_INTERVAL);
	}

	static int eruptedFlags(int age) {
		int flags = 0;
		for (int vent = 0; vent < 4; vent++) {
			if (age >= WARNING_TICKS + vent * VENT_INTERVAL) {
				flags |= 1 << vent;
			}
		}
		return flags;
	}

	static float ventGlow(int age, int vent) {
		int elapsed = age - burstTick(vent);
		return elapsed < 0 ? 0.0F : Math.max(0.0F, 1.0F - elapsed / 8.0F);
	}

	static int burstTick(int vent) {
		return WARNING_TICKS + vent * VENT_INTERVAL;
	}

	static Vec3 ventCenter(Vec3 center, int vent) {
		return switch (vent) {
			case 0 -> center.add(VENT_OFFSET, 0.0D, 0.0D);
			case 1 -> center.add(0.0D, 0.0D, VENT_OFFSET);
			case 2 -> center.add(-VENT_OFFSET, 0.0D, 0.0D);
			case 3 -> center.add(0.0D, 0.0D, -VENT_OFFSET);
			default -> throw new IllegalArgumentException("未知裂口序号：" + vent);
		};
	}

	@Override
	public LegendaryActionInstance start(LegendaryActionContext context) {
		LivingEntity target = context.target();
		if (target == null || !target.isAlive()) {
			return null;
		}
		Vec3 center = target.position();
		List<Vec3> vents = List.of(ventCenter(center, 0), ventCenter(center, 1),
				ventCenter(center, 2), ventCenter(center, 3));
		ThreatVisualEntity visual = ThreatVisualEntity.spawnGround(context.level(),
				LegendaryActionId.LAIR_BREACH, center, DURATION_TICKS + 20,
				VISUAL_CULL_RADIUS, 0.0F, 0.0F);
		return new Instance(center, vents, visual);
	}

	private static final class Instance implements LegendaryActionInstance {
		private final Vec3 center;
		private final List<Vec3> vents;
		private final List<Boolean> erupted = new ArrayList<>(List.of(false, false, false, false));
		@javax.annotation.Nullable
		private final ThreatVisualEntity visual;

		private Instance(Vec3 center, List<Vec3> vents,
				@javax.annotation.Nullable ThreatVisualEntity visual) {
			this.center = center;
			this.vents = vents;
			this.visual = visual;
		}

		@Override
		public boolean tick(LegendaryActionContext context, int age) {
			ServerLevel level = context.level();
			if (age < WARNING_TICKS) {
				if (visual != null && !visual.isRemoved()) {
					visual.update(ThreatVisualEntity.WARNING, age / (float) DURATION_TICKS,
							VISUAL_CULL_RADIUS, Math.min(3, age / 9),
							0.25F + 0.45F * (age % 9) / 8.0F, 0, Vec3.ZERO);
				}
				if (age % 8 == 0) {
					LegendaryVisuals.sound(level, center, SoundEvents.STONE_PLACE,
							0.55F, 0.65F + age * 0.01F);
				}
				return true;
			}
			int index = ventIndex(age);
			if (index >= 0 && age == burstTick(index)
					&& !erupted.get(index)) {
				erupted.set(index, true);
				erupt(context, vents.get(index));
			}
			if (visual != null && !visual.isRemoved()) {
				int vent = Math.min(3, (age - WARNING_TICKS) / VENT_INTERVAL);
				visual.update(ThreatVisualEntity.IMPACT,
						age / (float) DURATION_TICKS, VISUAL_CULL_RADIUS, vent,
						ventGlow(age, vent), eruptedFlags(age), Vec3.ZERO);
			}
			return age < DURATION_TICKS;
		}

		private void erupt(LegendaryActionContext context, Vec3 vent) {
			ServerLevel level = context.level();
			for (LivingEntity target : LegendaryActionCombat.cylinderTargets(
					context, vent, 3.2D, 4.0D, 4.0D)) {
				if (LegendaryActionCombat.hit(context, target,
						LegendaryActionCombat.DamageKind.EXPLOSION, 75.0D)) {
					LegendaryActionCombat.push(target, vent, 0.4D, 0.25D,
							"legendary_lair_breach:" + context.owner().getUUID() + ":"
									+ level.getGameTime(),
							level.getGameTime() + 20L);
				}
			}
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK,
							Blocks.COARSE_DIRT.defaultBlockState()),
					vent.x, vent.y + 0.1D, vent.z,
					35, 1.8D, 0.25D, 1.8D, 0.04D);
			LegendaryVisuals.sound(level, vent, SoundEvents.GENERIC_EXPLODE,
					0.75F, 1.2F);
		}

		@Override
		public void stop(LegendaryActionContext context, StopReason reason) {
			if (visual != null) {
				visual.finish(reason);
			}
		}
	}
}
