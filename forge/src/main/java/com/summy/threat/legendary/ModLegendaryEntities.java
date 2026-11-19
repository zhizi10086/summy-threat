package com.summy.threat.legendary;

import com.summy.threat.SummyThreat;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** 传奇动作专用视觉实体注册。 */
public final class ModLegendaryEntities {
	public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
			DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, SummyThreat.MOD_ID);

	public static final RegistryObject<EntityType<JadeLightBeamEntity>> JADE_LIGHT_BEAM =
			ENTITY_TYPES.register("jade_light_beam", () -> EntityType.Builder
					.<JadeLightBeamEntity>of(JadeLightBeamEntity::new, MobCategory.MISC)
					.sized(0.1F, 0.1F)
					.noSave()
					.clientTrackingRange(16)
					.updateInterval(1)
					.build(SummyThreat.MOD_ID + ":jade_light_beam"));

	public static final RegistryObject<EntityType<ThreatVisualEntity>> THREAT_VISUAL =
			ENTITY_TYPES.register("threat_visual", () -> EntityType.Builder
					.<ThreatVisualEntity>of(ThreatVisualEntity::new, MobCategory.MISC)
					.sized(0.1F, 0.1F)
					.noSave()
					.noSummon()
					.clientTrackingRange(16)
					.updateInterval(1)
					.build(SummyThreat.MOD_ID + ":threat_visual"));

	private ModLegendaryEntities() {
	}
}
