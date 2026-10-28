package com.summy.threat.compat.kubejs;

import com.summy.threat.api.AbilityDefinition;
import com.summy.threat.api.ArchetypeDefinition;
import com.summy.threat.ability.AbilityId;
import com.summy.threat.eximus.EximusArchetype;

import java.util.Arrays;
import java.util.List;

/** 客户端只读 KubeJS 全局 {@code SummyThreat}。 */
public final class SummyThreatClientBindings {
	private SummyThreatClientBindings() {
	}

	public static String abilityName(String id) {
		AbilityId ability = AbilityId.byId(id);
		return ability == null ? id : ability.displayName();
	}

	public static int abilityColor(String id) {
		AbilityId ability = AbilityId.byId(id);
		return ability == null ? 0x777777 : ability.color();
	}

	public static String archetypeName(String id) {
		EximusArchetype archetype = EximusArchetype.byId(id);
		return archetype == null ? id : archetype.displayName();
	}

	public static int archetypeColor(String id) {
		EximusArchetype archetype = EximusArchetype.byId(id);
		return archetype == null ? 0x777777 : archetype.color();
	}

	public static List<AbilityDefinition> abilities() {
		return Arrays.stream(AbilityId.values())
				.map(id -> new AbilityDefinition(id.id(), id.unlockTl(), id.displayName(),
						id.color(), id.rollable(), id.weight(), id.eximusOnly()))
				.toList();
	}

	public static List<ArchetypeDefinition> archetypes() {
		return Arrays.stream(EximusArchetype.values())
				.map(id -> new ArchetypeDefinition(id.id(), id.displayName(), id.color(),
						id.rollable(), id.weight()))
				.toList();
	}
}
