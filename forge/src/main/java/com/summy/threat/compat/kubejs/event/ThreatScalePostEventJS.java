package com.summy.threat.compat.kubejs.event;

import com.summy.threat.api.event.ThreatScalePostEvent;
import dev.latvian.mods.kubejs.event.EventJS;
import net.minecraft.world.entity.LivingEntity;

import java.util.Locale;

/** KubeJS scale.post：只读。 */
public class ThreatScalePostEventJS extends EventJS {
	public final LivingEntity entity;
	public final int tl;
	public final String kind;
	public final double healthMultiplier;
	public final double attackMultiplier;

	public ThreatScalePostEventJS(ThreatScalePostEvent event) {
		this.entity = event.getEntity();
		this.tl = event.getTl();
		this.kind = event.getKind().name().toLowerCase(Locale.ROOT);
		this.healthMultiplier = event.getHealthMultiplier();
		this.attackMultiplier = event.getAttackMultiplier();
	}
}
