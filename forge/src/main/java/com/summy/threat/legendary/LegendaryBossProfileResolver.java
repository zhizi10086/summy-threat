package com.summy.threat.legendary;

import com.summy.threat.scaling.MonsterScaler;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;

import java.lang.reflect.Method;

/** 把实体分类转换为传奇动作池所需快照，不硬引用冰火类。 */
public final class LegendaryBossProfileResolver {
	private LegendaryBossProfileResolver() {
	}

	public static LegendaryBossProfile resolve(LivingEntity entity) {
		boolean boss = MonsterScaler.kindOf(entity) == MonsterScaler.Kind.BOSS;
		if (!boss) {
			return LegendaryBossProfile.none();
		}
		String typeId = EntityType.getKey(entity.getType()).toString();
		boolean tamed = entity instanceof TamableAnimal animal && animal.isTame();
		boolean taggedDragon = entity.getType().is(dragonBossesTag());
		int stage = readDragonStage(entity);
		return fromFlags(typeId, true, stage, tamed, taggedDragon);
	}

	public static LegendaryBossProfile fromFlags(String typeId, boolean boss,
			int dragonStage, boolean tamed, boolean taggedDragon) {
		if (!boss || typeId == null) {
			return LegendaryBossProfile.none();
		}
		if ("minecraft:ender_dragon".equals(typeId)) {
			return LegendaryBossProfile.enderDragon();
		}
		if ("minecraft:wither".equals(typeId)) {
			return LegendaryBossProfile.wither();
		}
		if ("minecraft:warden".equals(typeId)) {
			return LegendaryBossProfile.warden();
		}
		if (DragonBossPolicy.isKnownIceFireDragon(typeId)) {
			return DragonBossPolicy.isEligibleDragonBoss(typeId, dragonStage, tamed, true)
					? LegendaryBossProfile.dragonBoss()
					: LegendaryBossProfile.none();
		}
		if (taggedDragon && !tamed && (dragonStage <= 0 || dragonStage >= 4)) {
			return LegendaryBossProfile.dragonBoss();
		}
		return LegendaryBossProfile.genericBoss();
	}

	private static int readDragonStage(LivingEntity entity) {
		try {
			Method method = entity.getClass().getMethod("getDragonStage");
			Object value = method.invoke(entity);
			if (value instanceof Integer stage) {
				return stage;
			}
		} catch (ReflectiveOperationException ignored) {
			// 非冰火龙或社区版接口不同；类型标签仍可提供 Boss 资格。
		}
		return -1;
	}

	private static TagKey<EntityType<?>> dragonBossesTag() {
		return TagKey.create(Registries.ENTITY_TYPE,
				new ResourceLocation("summy_threat", "dragon_bosses"));
	}
}
