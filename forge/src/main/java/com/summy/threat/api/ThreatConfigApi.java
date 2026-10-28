package com.summy.threat.api;

import com.summy.threat.Config;
import com.summy.threat.api.event.ThreatConfigChangedEvent;
import com.summy.threat.attribute.AttributeCaps;
import com.summy.threat.scaling.ScalingState;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.MinecraftForge;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** ST 全部 SERVER 配置的类型化读写接口。只允许服务端脚本调用写方法。 */
public final class ThreatConfigApi {
	private static final Map<String, Key> KEYS = new LinkedHashMap<>();
	private static final Map<String, List<String>> GROUPS = new LinkedHashMap<>();
	private static final ThreadLocal<Boolean> EVENT_WRITE = ThreadLocal.withInitial(() -> false);
	private static final ThreadLocal<Boolean> API_WRITE = ThreadLocal.withInitial(() -> false);
	private static String lastReloadFingerprint = "";
	private static long lastReloadAt;

	private record Key(String path, String group, ForgeConfigSpec.ConfigValue<?> value,
			ForgeConfigSpec.ValueSpec spec, String type) {
	}

	static {
		scan();
	}

	private ThreatConfigApi() {
	}

	public static List<String> groups() {
		return List.copyOf(GROUPS.keySet());
	}

	public static List<String> keys(String group) {
		List<String> keys = GROUPS.get(group);
		if (keys == null) {
			throw new IllegalArgumentException("未知配置分组：" + group);
		}
		return List.copyOf(keys);
	}

	public static String type(String path) {
		return require(path).type();
	}

	public static Object get(String path) {
		return jsValue(readValue(require(path).value()));
	}

	public static Object defaultValue(String path) {
		return jsValue(require(path).value().getDefault());
	}

	/** 当前全部配置值快照，供重载事件与调试展示。 */
	public static Map<String, Object> snapshot() {
		Map<String, Object> out = new LinkedHashMap<>();
		KEYS.forEach((path, key) -> out.put(path, jsValue(readValue(key.value()))));
		return out;
	}

	public static boolean isLoaded() {
		return Config.SPEC.isLoaded();
	}

	public static Object set(String path, Object value) {
		return setMany(Map.of(path, value)).get(path);
	}

	/** 先验证全部键，再统一写入；不会产生部分写入。返回 newValues。 */
	public static Map<String, Object> setMany(Map<String, ?> values) {
		if (!Config.SPEC.isLoaded()) {
			throw new IllegalStateException("SERVER 配置尚未加载；请在 ServerEvents.loaded 后写配置");
		}
		if (EVENT_WRITE.get()) {
			throw new IllegalStateException("禁止在 SummyThreatEvents.config.changed 内递归写配置");
		}
		Map<String, Parsed> parsed = new LinkedHashMap<>();
		for (Map.Entry<String, ?> entry : values.entrySet()) {
			Key key = require(entry.getKey());
			parsed.put(key.path(), new Parsed(key, parse(key, entry.getValue())));
		}
		if (parsed.isEmpty()) {
			return Map.of();
		}
		Map<String, Object> oldValues = new LinkedHashMap<>();
		Map<String, Object> newValues = new LinkedHashMap<>();
		for (Parsed change : parsed.values()) {
			oldValues.put(change.key().path(), jsValue(change.key().value().get()));
			newValues.put(change.key().path(), jsValue(change.value()));
		}
		writeAll(parsed.values(), oldValues, "api");
		return newValues;
	}

	public static Object reset(String path) {
		Key key = require(path);
		return set(path, key.value().getDefault());
	}

	public static void notifyReload(Map<String, ?> currentValues) {
		Map<String, Object> values = new LinkedHashMap<>();
		if (currentValues != null) {
			currentValues.forEach((key, value) -> values.put(key, jsValue(value)));
		}
		String fingerprint = values.toString();
		long now = System.nanoTime();
		if (fingerprint.equals(lastReloadFingerprint) && now - lastReloadAt < 2_000_000_000L) {
			return;
		}
		lastReloadFingerprint = fingerprint;
		lastReloadAt = now;
		MinecraftForge.EVENT_BUS.post(new ThreatConfigChangedEvent("reload", Map.of(), values));
	}

	public static boolean isApiWriteInProgress() {
		return API_WRITE.get();
	}

	private static void writeAll(Collection<Parsed> changes, Map<String, Object> oldValues,
			String source) {
		API_WRITE.set(true);
		try {
			for (Parsed change : changes) {
				write(change.key().value(), change.value());
			}
			Config.SPEC.save();
			AttributeCaps.apply();
			ScalingState.refresh();
		} catch (Throwable throwable) {
			rollback(changes, oldValues);
			throw new IllegalStateException("配置写入失败，已回滚：" + throwable.getMessage(), throwable);
		} finally {
			API_WRITE.remove();
		}
		Map<String, Object> newValues = new LinkedHashMap<>();
		for (Parsed change : changes) {
			newValues.put(change.key().path(), jsValue(change.key().value().get()));
		}
		EVENT_WRITE.set(true);
		try {
			MinecraftForge.EVENT_BUS.post(new ThreatConfigChangedEvent(source, oldValues, newValues));
		} finally {
			EVENT_WRITE.remove();
		}
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private static void write(ForgeConfigSpec.ConfigValue value, Object parsed) {
		value.set(parsed);
	}

	private static void rollback(Collection<Parsed> changes, Map<String, Object> oldValues) {
		for (Parsed change : changes) {
			if (!oldValues.containsKey(change.key().path())) {
				continue;
			}
			Object old = oldValues.get(change.key().path());
			Object raw = parse(change.key(), old);
			write(change.key().value(), raw);
		}
	}

	private static Key require(String path) {
		Key key = KEYS.get(path == null ? "" : path.trim());
		if (key == null) {
			throw new IllegalArgumentException("未知配置键：" + path);
		}
		return key;
	}

	private static Object parse(Key key, Object input) {
		Object parsed = switch (key.type()) {
			case "boolean" -> parseBoolean(input);
			case "integer" -> parseInteger(input);
			case "double" -> parseDouble(input);
			case "string" -> Objects.toString(input, "");
			case "enum" -> parseEnum(key.value().getDefault(), input);
			case "list" -> parseList(input);
			default -> throw new IllegalArgumentException("不支持的配置类型：" + key.type());
		};
		if (!key.spec().test(parsed)) {
			throw new IllegalArgumentException(key.path() + " 不在声明范围或类型不匹配：" + input);
		}
		return parsed;
	}

	private static Boolean parseBoolean(Object value) {
		if (value instanceof Boolean bool) {
			return bool;
		}
		String text = Objects.toString(value, "").trim().toLowerCase(Locale.ROOT);
		if ("true".equals(text)) {
			return true;
		}
		if ("false".equals(text)) {
			return false;
		}
		throw new IllegalArgumentException("需要 boolean：" + value);
	}

	private static Integer parseInteger(Object value) {
		if (value instanceof Number number && number.doubleValue() == number.intValue()) {
			return number.intValue();
		}
		try {
			return Integer.parseInt(Objects.toString(value).trim());
		} catch (NumberFormatException exception) {
			throw new IllegalArgumentException("需要整数：" + value);
		}
	}

	private static Double parseDouble(Object value) {
		double parsed;
		if (value instanceof Number number) {
			parsed = number.doubleValue();
		} else {
			try {
				parsed = Double.parseDouble(Objects.toString(value).trim());
			} catch (NumberFormatException exception) {
				throw new IllegalArgumentException("需要数值：" + value);
			}
		}
		if (!Double.isFinite(parsed)) {
			throw new IllegalArgumentException("需要有限数值：" + value);
		}
		return parsed;
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private static Object parseEnum(Object defaultValue, Object input) {
		if (!(defaultValue instanceof Enum enumValue)) {
			throw new IllegalArgumentException("枚举默认值异常");
		}
		String expected = Objects.toString(input, "").trim();
		for (Object candidate : enumValue.getDeclaringClass().getEnumConstants()) {
			if (((Enum) candidate).name().equalsIgnoreCase(expected)) {
				return candidate;
			}
		}
		throw new IllegalArgumentException("未知枚举值：" + input);
	}

	private static List<String> parseList(Object input) {
		if (input instanceof Collection<?> collection) {
			List<String> out = new ArrayList<>();
			for (Object element : collection) {
				out.add(Objects.toString(element));
			}
			return out;
		}
		if (input != null && input.getClass().isArray()) {
			int length = java.lang.reflect.Array.getLength(input);
			List<String> out = new ArrayList<>(length);
			for (int i = 0; i < length; i++) {
				out.add(Objects.toString(java.lang.reflect.Array.get(input, i)));
			}
			return out;
		}
		throw new IllegalArgumentException("需要字符串列表：" + input);
	}

	private static Object jsValue(Object value) {
		if (value instanceof Enum<?> enumValue) {
			return enumValue.name().toLowerCase(Locale.ROOT);
		}
		if (value instanceof List<?> list) {
			return List.copyOf(list);
		}
		return value;
	}

	private static void scan() {
		try {
			for (Field field : Config.class.getFields()) {
				if (!Modifier.isStatic(field.getModifiers())) {
					continue;
				}
				Object value = field.get(null);
				if (value instanceof ForgeConfigSpec.ConfigValue<?> configValue) {
					add(configValue);
				} else if (value instanceof ForgeConfigSpec.ConfigValue<?>[] values) {
					for (ForgeConfigSpec.ConfigValue<?> configValue : values) {
						add(configValue);
					}
				}
			}
		} catch (ReflectiveOperationException exception) {
			throw new ExceptionInInitializerError(exception);
		}
	}

	private static void add(ForgeConfigSpec.ConfigValue<?> value) {
		String path = String.join(".", value.getPath());
		Object spec = Config.SPEC.getSpec().get(value.getPath());
		if (!(spec instanceof ForgeConfigSpec.ValueSpec valueSpec)) {
			throw new IllegalStateException("配置缺少 ValueSpec：" + path);
		}
		String group = value.getPath().get(0);
		Key key = new Key(path, group, value, valueSpec, typeOf(value.getDefault()));
		KEYS.put(path, key);
		GROUPS.computeIfAbsent(group, ignored -> new ArrayList<>()).add(path);
	}

	private static String typeOf(Object value) {
		if (value instanceof Boolean) {
			return "boolean";
		}
		if (value instanceof Integer || value instanceof Long) {
			return "integer";
		}
		if (value instanceof Number) {
			return "double";
		}
		if (value instanceof Enum<?>) {
			return "enum";
		}
		if (value instanceof List<?>) {
			return "list";
		}
		return "string";
	}

	private static Object readValue(ForgeConfigSpec.ConfigValue<?> value) {
		return Config.SPEC.isLoaded() ? value.get() : value.getDefault();
	}

	private record Parsed(Key key, Object value) {
	}
}
