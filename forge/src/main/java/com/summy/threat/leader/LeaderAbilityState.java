package com.summy.threat.leader;

import com.summy.threat.SummyThreat;
import com.summy.threat.scaling.MonsterScaler;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 区域首领能力标签的持久化、迁移和首次抽取。 */
public final class LeaderAbilityState {
	public static final String TAG_LEADER_ABILITIES = "summy_threat:leader_abilities";

	private LeaderAbilityState() {
	}

	public static List<LeaderAbilityId> abilities(LivingEntity entity) {
		String raw = entity.getPersistentData().getString(TAG_LEADER_ABILITIES);
		if (raw.isBlank()) {
			return List.of();
		}
		List<LeaderAbilityId> out = new ArrayList<>(2);
		for (String part : raw.split(",")) {
			LeaderAbilityId id = LeaderAbilityId.byId(part);
			if (id != null && !out.contains(id)) {
				out.add(id);
			}
		}
		return List.copyOf(out);
	}

	public static boolean has(LivingEntity entity, LeaderAbilityId id) {
		return abilities(entity).contains(id);
	}

	public static String csv(LivingEntity entity) {
		return entity.getPersistentData().getString(TAG_LEADER_ABILITIES);
	}

	public static void setAbilities(LivingEntity entity, Collection<LeaderAbilityId> values) {
		Map<String, LeaderAbilityId> unique = new LinkedHashMap<>();
		for (LeaderAbilityId id : values) {
			unique.putIfAbsent(id.id().toLowerCase(Locale.ROOT), id);
		}
		entity.getPersistentData().putString(TAG_LEADER_ABILITIES, String.join(",", unique.keySet()));
		LeaderAuraEffects.cleanupLeader(entity);
		LeaderActiveEffects.resetAbilities(entity);
	}

	public static void clear(LivingEntity entity) {
		entity.getPersistentData().putString(TAG_LEADER_ABILITIES, "");
		LeaderAuraEffects.cleanupLeader(entity);
		LeaderActiveEffects.resetAbilities(entity);
	}

	public static List<LeaderAbilityId> randomize(LivingEntity entity, int tl) {
		int effectiveTl = Math.max(10, tl);
		List<LeaderAbilityId> rolled = LeaderAbilityPool.roll(effectiveTl, entity.getRandom());
		setAbilities(entity, rolled);
		return rolled;
	}

	/**
	 * 首次生成或损坏标签时补抽。已有合法标签（包括调试清空）保持不变。
	 */
	public static void ensure(LivingEntity entity, int tl) {
		if (MonsterScaler.kindOf(entity) != MonsterScaler.Kind.LEADER) {
			entity.getPersistentData().remove(TAG_LEADER_ABILITIES);
			return;
		}
		String raw = entity.getPersistentData().getString(TAG_LEADER_ABILITIES);
		if (!entity.getPersistentData().contains(TAG_LEADER_ABILITIES)) {
			randomize(entity, tl);
			return;
		}
		if (raw.isBlank()) {
			return;
		}
		for (String part : raw.split(",")) {
			if (LeaderAbilityId.byId(part) == null) {
				SummyThreat.LOGGER.warn("[Summy Threat] 区域首领能力标签包含未知 ID {}；重新抽取组合",
						part);
				randomize(entity, tl);
				return;
			}
		}
	}
}
