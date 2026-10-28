package com.summy.threat.compat.kubejs;

import com.summy.threat.api.ThreatConfigApi;

import java.util.List;
import java.util.Map;

/** 服务端 KubeJS 全局 {@code SummyThreatConfig}。 */
public final class SummyThreatConfigBindings {
	private SummyThreatConfigBindings() {
	}

	public static List<String> groups() {
		return ThreatConfigApi.groups();
	}

	public static List<String> keys(String group) {
		return ThreatConfigApi.keys(group);
	}

	public static boolean isLoaded() {
		return ThreatConfigApi.isLoaded();
	}

	public static String type(String key) {
		return ThreatConfigApi.type(key);
	}

	public static Object get(String key) {
		return ThreatConfigApi.get(key);
	}

	public static Object defaultValue(String key) {
		return ThreatConfigApi.defaultValue(key);
	}

	public static Map<String, Object> set(String key, Object value) {
		return Map.of(key, ThreatConfigApi.set(key, value));
	}

	public static Map<String, Object> setMany(Map<String, ?> values) {
		return ThreatConfigApi.setMany(values);
	}

	public static Object reset(String key) {
		return ThreatConfigApi.reset(key);
	}

	public static Map<String, Object> snapshot() {
		return ThreatConfigApi.snapshot();
	}
}
