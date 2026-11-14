package com.summy.threat.api;

import com.summy.threat.ability.AbilityEffects;
import com.summy.threat.ability.AbilityId;
import com.summy.threat.armor.PlayerEximusShield;
import com.summy.threat.combat.ArmorPenetration;
import com.summy.threat.combat.ArmorShred;
import com.summy.threat.eximus.EximusArchetype;
import com.summy.threat.eximus.EximusShield;
import com.summy.threat.pcf.CdfManager;
import com.summy.threat.pcf.PcfManager;
import com.summy.threat.scaling.MonsterScaler;
import com.summy.threat.scaling.ScalingState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 供 KubeJS/Java 调用的稳定 ST 操作入口。 */
public final class SummyThreatApi {
	private SummyThreatApi() {
	}

	public static String tier() {
		return ScalingState.tier().name().toLowerCase(Locale.ROOT);
	}

	public static int globalTl() {
		return ScalingState.globalTl();
	}

	public static double healthScale() {
		return ScalingState.rHp();
	}

	public static double attackScale() {
		return ScalingState.rAtk();
	}

	public static boolean setTier(String mode) {
		com.summy.threat.Config.TierMode value;
		try {
			value = com.summy.threat.Config.TierMode.valueOf(mode.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException exception) {
			throw new IllegalArgumentException("未知 tier：" + mode);
		}
		ThreatConfigApi.set("environment_tier.mode", value.name().toLowerCase(Locale.ROOT));
		return true;
	}

	public static int setGlobalTl(int tl) {
		if (tl < -2 || tl > 20) {
			throw new IllegalArgumentException("globalTl 必须在 -2~20");
		}
		ThreatConfigApi.set("scaling_debug.global_tl", tl);
		ScalingState.setGlobalTl(tl);
		return tl;
	}

	public static boolean isScaled(LivingEntity entity) {
		return entity.getPersistentData().getBoolean(MonsterScaler.TAG_SCALED);
	}

	public static int tl(LivingEntity entity) {
		return entity.getPersistentData().getInt(MonsterScaler.TAG_TL);
	}

	public static String kind(LivingEntity entity) {
		return MonsterScaler.kindOf(entity).name().toLowerCase(Locale.ROOT);
	}

	public static List<String> abilities(LivingEntity entity) {
		return split(entity.getPersistentData().getString(MonsterScaler.TAG_ABILITIES));
	}

	public static boolean hasAbility(LivingEntity entity, String id) {
		AbilityId ability = AbilityId.byId(id);
		if (ability == null) {
			return false;
		}
		return abilities(entity).contains(ability.id());
	}

	public static String archetype(LivingEntity entity) {
		return entity.getPersistentData().getString(MonsterScaler.TAG_ARCHETYPE);
	}

	public static boolean scale(LivingEntity entity, int tl, String kind) {
		MonsterScaler.Kind value = kind == null || kind.isBlank()
				? MonsterScaler.kindOf(entity) : parseKind(kind);
		return MonsterScaler.scaleNow(entity, tl, value);
	}

	public static double pcf(ServerPlayer player) {
		return PcfManager.value(player);
	}

	public static double setPcf(ServerPlayer player, double value) {
		if (value < 0.0D || value > 20.0D) {
			throw new IllegalArgumentException("pcf 必须在 0~20");
		}
		PcfManager.setValue(player, value);
		return value;
	}

	public static double cdf(ServerLevel level, BlockPos pos) {
		return CdfManager.current(level, pos);
	}

	public static double setCdf(ServerLevel level, BlockPos pos, double value) {
		if (value < 0.0D || value > 20.0D) {
			throw new IllegalArgumentException("cdf 必须在 0~20");
		}
		CdfManager.setCurrent(level, pos, value);
		return value;
	}

	public static double playerShield(ServerPlayer player) {
		return PlayerEximusShield.current(player);
	}

	public static double setPlayerShield(ServerPlayer player, double value) {
		PlayerEximusShield.set(player, value);
		return PlayerEximusShield.current(player);
	}

	public static double eximusShield(LivingEntity entity) {
		return EximusShield.current(entity);
	}

	public static double eximusShieldMax(LivingEntity entity) {
		return EximusShield.max(entity);
	}

	public static void setAbilities(LivingEntity entity, List<String> ids) {
		if (!isScaled(entity)) {
			throw new IllegalStateException("实体尚未数值化");
		}
		rejectLeaderAbilityWrite(entity);
		List<String> normalized = new ArrayList<>();
		for (String raw : ids) {
			AbilityId id = AbilityId.byId(raw);
			if (id == null) {
				throw new IllegalArgumentException("未知能力 ID：" + raw);
			}
			if (!normalized.contains(id.id())) {
				normalized.add(id.id());
			}
		}
		AbilityEffects.setAbilities(entity, tl(entity), String.join(",", normalized));
	}

	public static boolean addAbility(LivingEntity entity, String id) {
		rejectLeaderAbilityWrite(entity);
		AbilityId ability = AbilityId.byId(id);
		if (ability == null) {
			throw new IllegalArgumentException("未知能力 ID：" + id);
		}
		List<String> current = abilities(entity);
		if (current.contains(ability.id())) {
			return false;
		}
		current.add(ability.id());
		setAbilities(entity, current);
		return true;
	}

	public static boolean removeAbility(LivingEntity entity, String id) {
		rejectLeaderAbilityWrite(entity);
		String normalized = id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
		List<String> current = abilities(entity);
		boolean removed = current.remove(normalized);
		if (removed) {
			AbilityEffects.setAbilities(entity, tl(entity), String.join(",", current));
		}
		return removed;
	}

	public static void clearAbilities(LivingEntity entity) {
		rejectLeaderAbilityWrite(entity);
		AbilityEffects.setAbilities(entity, tl(entity), "");
	}

	public static String setArchetype(LivingEntity entity, String id) {
		String normalized = id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
		if (!normalized.isEmpty() && EximusArchetype.byId(normalized) == null) {
			throw new IllegalArgumentException("未知原型 ID：" + id);
		}
		MonsterScaler.setArchetype(entity, normalized);
		return normalized;
	}

	public static LivingEntity spawn(ServerLevel level, String entityId, Vec3 pos, int tl,
			String kind, double pcf, List<String> abilities, String archetype) {
		EntityType<?> type = EntityType.byString(entityId).orElse(null);
		if (type == null) {
			throw new IllegalArgumentException("未知实体 ID：" + entityId);
		}
		Entity created = type.create(level);
		if (!(created instanceof LivingEntity living) || !MonsterScaler.isScalable(living)) {
			throw new IllegalArgumentException("实体不在 ST 接管范围：" + entityId);
		}
		living.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
		if (living instanceof Mob mob) {
			mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()),
					MobSpawnType.COMMAND, null, null);
			mob.setPersistenceRequired();
		}
		MonsterScaler.Kind parsedKind = parseKind(kind == null ? "normal" : kind);
		if (parsedKind == MonsterScaler.Kind.EXIMUS) {
			living.getPersistentData().putBoolean(MonsterScaler.TAG_EXIMUS, true);
		} else if (parsedKind == MonsterScaler.Kind.LEADER) {
			living.getPersistentData().putBoolean(MonsterScaler.TAG_LEADER, true);
			living.getPersistentData().putDouble(MonsterScaler.TAG_PCF, pcf);
		}
		if (archetype != null && !archetype.isBlank()) {
			String normalized = archetype.trim().toLowerCase(Locale.ROOT);
			if (EximusArchetype.byId(normalized) == null) {
				throw new IllegalArgumentException("未知原型 ID：" + archetype);
			}
			living.getPersistentData().putString(MonsterScaler.TAG_ARCHETYPE, normalized);
		}
		if (abilities != null && !abilities.isEmpty()) {
			if (parsedKind == MonsterScaler.Kind.LEADER) {
				throw new IllegalArgumentException(
						"区域首领能力使用独立标签，不能通过 TL 能力参数写入");
			}
			List<String> normalized = new ArrayList<>();
			for (String raw : abilities) {
				AbilityId id = AbilityId.byId(raw);
				if (id == null) {
					throw new IllegalArgumentException("未知能力 ID：" + raw);
				}
				normalized.add(id.id());
			}
			living.getPersistentData().putString(MonsterScaler.TAG_ABILITIES,
					String.join(",", normalized));
		}
		if (!MonsterScaler.scaleNow(living, tl, parsedKind)) {
			created.discard();
			return null;
		}
		level.addFreshEntity(living);
		return living;
	}

	public static boolean hurtWithArmorPenetration(LivingEntity target, DamageSource source,
			float amount, double percent) {
		return ArmorPenetration.hurt(target, source, amount, percent);
	}

	public static void applyCorrosion(LivingEntity target) {
		ArmorShred.applyCorrosion(target);
	}

	public static void clearCorrosion(LivingEntity target) {
		ArmorShred.clearCorrosion(target);
	}

	public static AbilityId registerAbility(AbilityDefinition definition) {
		return AbilityId.registerCustom(definition);
	}

	public static boolean unregisterAbility(String id) {
		return AbilityId.unregisterCustom(id);
	}

	public static List<AbilityDefinition> customAbilities() {
		return AbilityId.customDefinitions();
	}

	public static EximusArchetype registerArchetype(ArchetypeDefinition definition) {
		return EximusArchetype.registerCustom(definition);
	}

	public static boolean unregisterArchetype(String id) {
		return EximusArchetype.unregisterCustom(id);
	}

	public static List<ArchetypeDefinition> customArchetypes() {
		return EximusArchetype.customDefinitions();
	}

	public static void clearCustomContent() {
		AbilityId.clearCustom();
		EximusArchetype.clearCustom();
	}

	public static void replaceClientDefinitions(List<AbilityDefinition> abilities,
			List<ArchetypeDefinition> archetypes) {
		AbilityId.replaceClientDefinitions(abilities);
		EximusArchetype.replaceClientDefinitions(archetypes);
	}

	public static MonsterScaler.Kind parseKind(String raw) {
		try {
			MonsterScaler.Kind kind = MonsterScaler.Kind.valueOf(
					raw.trim().toUpperCase(Locale.ROOT));
			if (kind == MonsterScaler.Kind.BOSS) {
				return kind;
			}
			return kind;
		} catch (IllegalArgumentException exception) {
			throw new IllegalArgumentException("kind 只支持 normal / eximus / leader / boss");
		}
	}

	private static List<String> split(String csv) {
		List<String> out = new ArrayList<>();
		if (csv == null || csv.isBlank()) {
			return out;
		}
		for (String value : csv.split(",")) {
			String normalized = value.trim().toLowerCase(Locale.ROOT);
			if (!normalized.isEmpty()) {
				out.add(normalized);
			}
		}
		return out;
	}

	private static void rejectLeaderAbilityWrite(LivingEntity entity) {
		if (MonsterScaler.kindOf(entity) == MonsterScaler.Kind.LEADER) {
			throw new IllegalStateException("区域首领能力使用独立标签，不能写入普通 TL 能力");
		}
	}
}
