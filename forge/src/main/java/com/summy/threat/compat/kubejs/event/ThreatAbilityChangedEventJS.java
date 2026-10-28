package com.summy.threat.compat.kubejs.event;

import com.summy.threat.api.event.ThreatAbilityChangedEvent;
import dev.latvian.mods.kubejs.event.EventJS;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;

/** KubeJS ability.changed：只读。 */
public class ThreatAbilityChangedEventJS extends EventJS {
	public final LivingEntity entity;
	public final List<String> oldAbilities;
	public final List<String> newAbilities;

	public ThreatAbilityChangedEventJS(ThreatAbilityChangedEvent event) {
		this.entity = event.getEntity();
		this.oldAbilities = event.getOldAbilities();
		this.newAbilities = event.getNewAbilities();
	}
}
