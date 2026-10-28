package com.summy.threat.api.event;

import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingEvent;

import java.util.List;

/** 实体 TL 能力列表实际变化。 */
public class ThreatAbilityChangedEvent extends LivingEvent {
	private final List<String> oldAbilities;
	private final List<String> newAbilities;

	public ThreatAbilityChangedEvent(LivingEntity entity, List<String> oldAbilities,
			List<String> newAbilities) {
		super(entity);
		this.oldAbilities = List.copyOf(oldAbilities);
		this.newAbilities = List.copyOf(newAbilities);
	}

	public List<String> getOldAbilities() {
		return oldAbilities;
	}

	public List<String> getNewAbilities() {
		return newAbilities;
	}
}
