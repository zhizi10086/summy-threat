package com.summy.threat.legendary;

import com.summy.threat.SummyThreat;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** 传奇动作客户端渲染注册。 */
@Mod.EventBusSubscriber(modid = SummyThreat.MOD_ID,
		bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class LegendaryClientEvents {
	private LegendaryClientEvents() {
	}

	@SubscribeEvent
	public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
		event.registerEntityRenderer(ModLegendaryEntities.JADE_LIGHT_BEAM.get(),
				JadeLightBeamRenderer::new);
		event.registerEntityRenderer(ModLegendaryEntities.THREAT_VISUAL.get(),
				ThreatVisualRenderer::new);
	}
}
