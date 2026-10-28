package com.summy.threat.compat.kubejs;

import com.summy.threat.api.AbilityDefinition;
import com.summy.threat.api.ArchetypeDefinition;
import com.summy.threat.api.SummyThreatApi;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 服务端 KubeJS 全局 {@code SummyThreat}。 */
public final class SummyThreatBindings {
	private SummyThreatBindings() {
	}

	public static String tier() {
		return SummyThreatApi.tier();
	}

	public static int globalTl() {
		return SummyThreatApi.globalTl();
	}

	public static double rHp() {
		return SummyThreatApi.healthScale();
	}

	public static double rAtk() {
		return SummyThreatApi.attackScale();
	}

	public static boolean setTier(String tier) {
		return SummyThreatApi.setTier(tier);
	}

	public static int setGlobalTl(int tl) {
		return SummyThreatApi.setGlobalTl(tl);
	}

	public static boolean isScaled(LivingEntity entity) {
		return SummyThreatApi.isScaled(entity);
	}

	public static int tl(LivingEntity entity) {
		return SummyThreatApi.tl(entity);
	}

	public static String kind(LivingEntity entity) {
		return SummyThreatApi.kind(entity);
	}

	public static List<String> abilities(LivingEntity entity) {
		return SummyThreatApi.abilities(entity);
	}

	public static boolean hasAbility(LivingEntity entity, String id) {
		return SummyThreatApi.hasAbility(entity, id);
	}

	public static String archetype(LivingEntity entity) {
		return SummyThreatApi.archetype(entity);
	}

	public static LivingEntity scale(LivingEntity entity, int tl, String kind) {
		return SummyThreatApi.scale(entity, tl, kind) ? entity : null;
	}

	public static double pcf(ServerPlayer player) {
		return SummyThreatApi.pcf(player);
	}

	public static double setPcf(ServerPlayer player, double value) {
		return SummyThreatApi.setPcf(player, value);
	}

	public static double cdf(ServerLevel level, BlockPos pos) {
		return SummyThreatApi.cdf(level, pos);
	}

	public static double setCdf(ServerLevel level, BlockPos pos, double value) {
		return SummyThreatApi.setCdf(level, pos, value);
	}

	public static double playerShield(ServerPlayer player) {
		return SummyThreatApi.playerShield(player);
	}

	public static double setPlayerShield(ServerPlayer player, double value) {
		return SummyThreatApi.setPlayerShield(player, value);
	}

	public static double eximusShield(LivingEntity entity) {
		return SummyThreatApi.eximusShield(entity);
	}

	public static double eximusShieldMax(LivingEntity entity) {
		return SummyThreatApi.eximusShieldMax(entity);
	}

	public static void setAbilities(LivingEntity entity, List<String> ids) {
		SummyThreatApi.setAbilities(entity, ids);
	}

	public static boolean addAbility(LivingEntity entity, String id) {
		return SummyThreatApi.addAbility(entity, id);
	}

	public static boolean removeAbility(LivingEntity entity, String id) {
		return SummyThreatApi.removeAbility(entity, id);
	}

	public static void clearAbilities(LivingEntity entity) {
		SummyThreatApi.clearAbilities(entity);
	}

	public static String setArchetype(LivingEntity entity, String id) {
		return SummyThreatApi.setArchetype(entity, id);
	}

	public static LivingEntity spawn(Level level, String entityId, double x, double y, double z,
			int tl, String kind, double pcf, List<String> abilities, String archetype) {
		if (!(level instanceof ServerLevel serverLevel)) {
			throw new IllegalArgumentException("spawn 只能在服务端世界调用");
		}
		return SummyThreatApi.spawn(serverLevel, entityId, new Vec3(x, y, z), tl, kind,
				pcf, abilities == null ? List.of() : abilities, archetype);
	}

	public static boolean hurtWithArmorPenetration(LivingEntity target, DamageSource source,
			float amount, double percent) {
		return SummyThreatApi.hurtWithArmorPenetration(target, source, amount, percent);
	}

	public static void applyCorrosion(LivingEntity target) {
		SummyThreatApi.applyCorrosion(target);
	}

	public static void clearCorrosion(LivingEntity target) {
		SummyThreatApi.clearCorrosion(target);
	}

	public static AbilityDefinition registerAbility(Map<?, ?> options) {
		return SummyThreatApi.registerAbility(parseAbility(options)).definition();
	}

	public static AbilityDefinition registerAbility(String id, int unlockTl, String displayName,
			int color, boolean rollable, int weight, boolean eximusOnly) {
		return SummyThreatApi.registerAbility(new AbilityDefinition(id, unlockTl, displayName,
				color, rollable, weight, eximusOnly)).definition();
	}

	public static boolean unregisterAbility(String id) {
		return SummyThreatApi.unregisterAbility(id);
	}

	public static ArchetypeDefinition registerArchetype(Map<?, ?> options) {
		return SummyThreatApi.registerArchetype(parseArchetype(options)).definition();
	}

	public static ArchetypeDefinition registerArchetype(String id, String displayName, int color,
			boolean rollable, int weight) {
		return SummyThreatApi.registerArchetype(new ArchetypeDefinition(id, displayName,
				color, rollable, weight)).definition();
	}

	public static boolean unregisterArchetype(String id) {
		return SummyThreatApi.unregisterArchetype(id);
	}

	public static List<AbilityDefinition> customAbilities() {
		return SummyThreatApi.customAbilities();
	}

	public static List<ArchetypeDefinition> customArchetypes() {
		return SummyThreatApi.customArchetypes();
	}

	private static AbilityDefinition parseAbility(Map<?, ?> map) {
		String id = string(map, "id", "");
		return new AbilityDefinition(id, integer(map, "unlockTl", integer(map, "unlock_tl", 0)),
				string(map, "displayName", string(map, "display_name", id)),
				integer(map, "color", 0xFFFFFF), bool(map, "rollable", false),
				integer(map, "weight", 1), bool(map, "eximusOnly", bool(map, "eximus_only", false)));
	}

	private static ArchetypeDefinition parseArchetype(Map<?, ?> map) {
		String id = string(map, "id", "");
		return new ArchetypeDefinition(id,
				string(map, "displayName", string(map, "display_name", id)),
				integer(map, "color", 0xFFFFFF), bool(map, "rollable", false),
				integer(map, "weight", 1));
	}

	private static String string(Map<?, ?> map, String key, String fallback) {
		Object value = map.get(key);
		return value == null ? fallback : value.toString();
	}

	private static int integer(Map<?, ?> map, String key, int fallback) {
		Object value = map.get(key);
		if (value instanceof Number number) {
			return number.intValue();
		}
		if (value == null) {
			return fallback;
		}
		String text = value.toString().trim().toLowerCase(Locale.ROOT);
		return text.startsWith("0x") ? Integer.parseInt(text.substring(2), 16)
				: Integer.parseInt(text);
	}

	private static boolean bool(Map<?, ?> map, String key, boolean fallback) {
		Object value = map.get(key);
		if (value instanceof Boolean bool) {
			return bool;
		}
		return value == null ? fallback : Boolean.parseBoolean(value.toString());
	}
}
