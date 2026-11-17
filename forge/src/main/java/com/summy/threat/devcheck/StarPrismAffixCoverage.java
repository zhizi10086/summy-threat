package com.summy.threat.devcheck;

import com.summy.threat.equipment.EquipmentCategory;
import com.summy.threat.equipment.StarPrismAffixes;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** DevCheck 专用的星渊词条执行账本，确保 34 项不会只靠注册表“看起来覆盖”。 */
final class StarPrismAffixCoverage {
	private static final Set<String> COVERED = new HashSet<>();

	private StarPrismAffixCoverage() {
	}

	static void reset() {
		COVERED.clear();
	}

	static void cover(String id) {
		COVERED.add(id);
	}

	static List<String> missing(EquipmentCategory category) {
		return StarPrismAffixes.byCategory(category).stream()
				.map(affix -> affix.id())
				.filter(id -> !COVERED.contains(id))
				.collect(Collectors.toList());
	}
}
