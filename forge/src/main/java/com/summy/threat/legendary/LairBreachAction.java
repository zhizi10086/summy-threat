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

/** 巢穴裂变：四个裂口顺时针依次喷发。 */
final class LairBreachAction implements LegendaryActionHandler {
	private static final int WARNING_TICKS = 35;

	@Override
	public LegendaryActionInstance start(LegendaryActionContext context) {
		LivingEntity target = context.target();
		if (target == null || !target.isAlive()) {
			return null;
		}
		Vec3 center = target.position();
		List<Vec3> vents = List.of(
				center.add(6.0D, 0.0D, 0.0D),
				center.add(0.0D, 0.0D, 6.0D),
				center.add(-6.0D, 0.0D, 0.0D),
				center.add(0.0D, 0.0D, -6.0D));
		return new Instance(center, vents);
	}

	private static final class Instance implements LegendaryActionInstance {
		private final Vec3 center;
		private final List<Vec3> vents;
		private final List<Boolean> erupted = new ArrayList<>(List.of(false, false, false, false));

		private Instance(Vec3 center, List<Vec3> vents) {
			this.center = center;
			this.vents = vents;
		}

		@Override
		public boolean tick(LegendaryActionContext context, int age) {
			ServerLevel level = context.level();
			if (age < WARNING_TICKS) {
				for (Vec3 vent : vents) {
					LegendaryVisuals.ring(level, vent, 3.2D,
							LegendaryActionId.LAIR_BREACH.color(), 28, 1.1F, 0.05D);
				}
				if (age % 8 == 0) {
					LegendaryVisuals.sound(level, center, SoundEvents.STONE_PLACE,
							0.55F, 0.65F + age * 0.01F);
				}
				return true;
			}
			int index = (age - WARNING_TICKS) / 5;
			if (index >= 0 && index < vents.size() && (age - WARNING_TICKS) % 5 == 0
					&& !erupted.get(index)) {
				erupted.set(index, true);
				erupt(context, vents.get(index));
			}
			for (int i = 0; i < vents.size(); i++) {
				if (!erupted.get(i)) {
					LegendaryVisuals.ring(level, vents.get(i), 3.2D,
							LegendaryActionId.LAIR_BREACH.color(), 28, 1.1F, 0.05D);
				}
			}
			return age < WARNING_TICKS + 22;
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
			LegendaryVisuals.ring(level, vent, 3.2D,
					LegendaryActionId.LAIR_BREACH.color(), 36, 1.6F, 0.1D);
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK,
							Blocks.COARSE_DIRT.defaultBlockState()),
					vent.x, vent.y + 0.1D, vent.z,
					35, 1.8D, 0.25D, 1.8D, 0.04D);
			LegendaryVisuals.sound(level, vent, SoundEvents.GENERIC_EXPLODE,
					0.75F, 1.2F);
		}
	}
}
