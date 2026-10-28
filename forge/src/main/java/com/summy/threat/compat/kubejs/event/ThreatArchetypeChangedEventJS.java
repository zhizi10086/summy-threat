package com.summy.threat.compat.kubejs.event;

import com.summy.threat.api.event.ThreatArchetypeChangedEvent;
import dev.latvian.mods.kubejs.event.EventJS;
import net.minecraft.world.entity.LivingEntity;

/** KubeJS archetype.changed：只读。 */
public class ThreatArchetypeChangedEventJS extends EventJS {
	public final LivingEntity entity;
	public final String oldArchetype;
	public final String newArchetype;

	public ThreatArchetypeChangedEventJS(ThreatArchetypeChangedEvent event) {
		this.entity = event.getEntity();
		this.oldArchetype = event.getOldArchetype();
		this.newArchetype = event.getNewArchetype();
	}
}
