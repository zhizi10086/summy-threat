package com.summy.threat.compat.kubejs.event;

import com.summy.threat.api.event.ThreatScalePreEvent;
import dev.latvian.mods.kubejs.event.EventJS;
import net.minecraft.world.entity.LivingEntity;

import java.util.Locale;

/** KubeJS scale.pre：可修改 tl/kind 或设置 canceled=true。 */
public class ThreatScalePreEventJS extends EventJS {
	public final LivingEntity entity;
	public int tl;
	public String kind;
	public boolean canceled;

	public ThreatScalePreEventJS(ThreatScalePreEvent event) {
		this.entity = event.getEntity();
		this.tl = event.getTl();
		this.kind = event.getKind().name().toLowerCase(Locale.ROOT);
	}
}
