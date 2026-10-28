package com.summy.threat.api.event;

import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingEvent;

/** 卓越者原型实际变化。 */
public class ThreatArchetypeChangedEvent extends LivingEvent {
	private final String oldArchetype;
	private final String newArchetype;

	public ThreatArchetypeChangedEvent(LivingEntity entity, String oldArchetype, String newArchetype) {
		super(entity);
		this.oldArchetype = oldArchetype == null ? "" : oldArchetype;
		this.newArchetype = newArchetype == null ? "" : newArchetype;
	}

	public String getOldArchetype() {
		return oldArchetype;
	}

	public String getNewArchetype() {
		return newArchetype;
	}
}
