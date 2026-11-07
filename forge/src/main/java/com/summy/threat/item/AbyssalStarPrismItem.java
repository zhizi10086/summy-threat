package com.summy.threat.item;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;

/** 星渊棱晶：仅在客户端为掉落的物品实体提供星核脉冲光效。 */
public final class AbyssalStarPrismItem extends Item {
	private static final DustParticleOptions STAR_DUST =
			new DustParticleOptions(new Vector3f(0.478F, 0.784F, 0.957F), 0.75F);
	private static final DustParticleOptions PALE_DUST =
			new DustParticleOptions(new Vector3f(0.824F, 0.922F, 1.0F), 0.55F);

	public AbyssalStarPrismItem(Properties properties) {
		super(properties);
	}

	@Override
	public boolean onEntityItemUpdate(ItemStack stack, ItemEntity entity) {
		if (entity.level().isClientSide()) {
			tickDroppedGlow(entity);
		}
		return false;
	}

	private static void tickDroppedGlow(ItemEntity entity) {
		long tick = entity.level().getGameTime() + entity.getId();
		double x = entity.getX();
		double y = entity.getY() + 0.35D;
		double z = entity.getZ();

		// 两枚反向环绕光点，每 2 tick 更新一次即可保持低开销。
		if (tick % 2L == 0L) {
			double phase = tick * 0.18D;
			for (int i = 0; i < 2; i++) {
				double angle = phase + i * Math.PI;
				entity.level().addParticle(STAR_DUST,
						x + Math.cos(angle) * 0.42D,
						y + 0.16D + Math.sin(phase * 2.0D) * 0.05D,
						z + Math.sin(angle) * 0.42D,
						0.0D, 0.008D, 0.0D);
			}
		}

		int pulse = (int) Math.floorMod(tick, 40L);
		if (pulse < 12 && tick % 3L == 0L) {
			double radius = 0.76D - pulse * 0.035D;
			double angle = pulse * 0.52D + entity.getId() * 0.37D;
			entity.level().addParticle(PALE_DUST,
					x + Math.cos(angle) * radius, y + 0.08D,
					z + Math.sin(angle) * radius, 0.0D, 0.01D, 0.0D);
		} else if (pulse == 12 || pulse == 13) {
			for (int i = 0; i < 4; i++) {
				double angle = Math.PI * 0.5D * i;
				entity.level().addParticle(PALE_DUST,
						x + Math.cos(angle) * 0.24D, y + 0.12D,
						z + Math.sin(angle) * 0.24D, 0.0D, 0.02D, 0.0D);
			}
			entity.level().addParticle(ParticleTypes.END_ROD, x, y + 0.18D, z,
					0.0D, 0.02D, 0.0D);
		} else if (pulse > 19 && pulse < 36 && tick % 4L == 0L) {
			double radius = 0.34D + (pulse - 20) * 0.025D;
			double angle = pulse * 0.33D + entity.getId() * 0.21D;
			entity.level().addParticle(STAR_DUST,
					x + Math.cos(angle) * radius, y + 0.10D,
					z + Math.sin(angle) * radius, 0.0D, 0.012D, 0.0D);
		}

		if (tick % 10L == 0L) {
			entity.level().addParticle(ParticleTypes.END_ROD,
					x + (entity.getId() % 3 - 1) * 0.08D,
					y + 0.48D, z,
					0.0D, 0.03D, 0.0D);
		}
	}
}
