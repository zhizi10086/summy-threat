package com.summy.threat.api.event;

import net.minecraftforge.eventbus.api.Event;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** 配置通过 API 写入或 Forge 重载成功后的通知。 */
public class ThreatConfigChangedEvent extends Event {
	private final String source;
	private final Map<String, Object> oldValues;
	private final Map<String, Object> newValues;

	public ThreatConfigChangedEvent(String source, Map<String, ?> oldValues, Map<String, ?> newValues) {
		this.source = source;
		this.oldValues = Collections.unmodifiableMap(new LinkedHashMap<>(oldValues));
		this.newValues = Collections.unmodifiableMap(new LinkedHashMap<>(newValues));
	}

	public String getSource() {
		return source;
	}

	public Map<String, Object> getOldValues() {
		return oldValues;
	}

	public Map<String, Object> getNewValues() {
		return newValues;
	}
}
