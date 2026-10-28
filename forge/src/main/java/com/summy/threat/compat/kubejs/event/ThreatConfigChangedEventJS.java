package com.summy.threat.compat.kubejs.event;

import com.summy.threat.api.event.ThreatConfigChangedEvent;
import dev.latvian.mods.kubejs.event.EventJS;

import java.util.Map;

/** KubeJS config.changed：只读；事件内再次写配置会被拒绝。 */
public class ThreatConfigChangedEventJS extends EventJS {
	public final String source;
	public final Map<String, Object> oldValues;
	public final Map<String, Object> newValues;

	public ThreatConfigChangedEventJS(ThreatConfigChangedEvent event) {
		this.source = event.getSource();
		this.oldValues = event.getOldValues();
		this.newValues = event.getNewValues();
	}
}
