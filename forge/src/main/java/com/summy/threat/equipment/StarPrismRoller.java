package com.summy.threat.equipment;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

/** 三系原子抽取；只有三类都成功时才返回完整结果。 */
public final class StarPrismRoller {
	@FunctionalInterface
	public interface WeightProvider {
		double weight(StarPrismAffix affix);
	}

	private StarPrismRoller() {
	}

	public static Optional<StarPrismData> roll(PrismRollMode mode,
			PrismRollSettings settings, WeightProvider weights, Random random) {
		String armor = choose(EquipmentCategory.ARMOR, mode, settings, weights, random);
		if (armor == null) {
			return Optional.empty();
		}
		String melee = choose(EquipmentCategory.MELEE, mode, settings, weights, random);
		if (melee == null) {
			return Optional.empty();
		}
		String ranged = choose(EquipmentCategory.RANGED, mode, settings, weights, random);
		if (ranged == null) {
			return Optional.empty();
		}
		return Optional.of(new StarPrismData(StarPrismData.SCHEMA, true, null,
				armor, melee, ranged));
	}

	private static String choose(EquipmentCategory category, PrismRollMode mode,
			PrismRollSettings settings, WeightProvider weights, Random random) {
		List<StarPrismTier> tiers = new ArrayList<>();
		List<Double> tierWeights = new ArrayList<>();
		for (StarPrismTier tier : StarPrismTier.values()) {
			double tierWeight = settings.weight(mode, tier);
			if (tierWeight > 0.0D && !candidates(category, tier, weights).isEmpty()) {
				tiers.add(tier);
				tierWeights.add(tierWeight);
			}
		}
		int tierIndex = weightedIndex(tierWeights, random);
		if (tierIndex < 0) {
			return null;
		}
		List<StarPrismAffix> candidates = candidates(category, tiers.get(tierIndex), weights);
		List<Double> affixWeights = candidates.stream().map(weights::weight).toList();
		int affixIndex = weightedIndex(affixWeights, random);
		return affixIndex < 0 ? null : candidates.get(affixIndex).id();
	}

	private static List<StarPrismAffix> candidates(EquipmentCategory category,
			StarPrismTier tier, WeightProvider weights) {
		List<StarPrismAffix> out = new ArrayList<>();
		for (StarPrismAffix affix : StarPrismAffixes.byCategory(category)) {
			if (affix.tier() == tier && weights.weight(affix) > 0.0D) {
				out.add(affix);
			}
		}
		return out;
	}

	private static int weightedIndex(List<Double> weights, Random random) {
		double total = weights.stream().mapToDouble(Double::doubleValue).sum();
		if (!(total > 0.0D) || !Double.isFinite(total)) {
			return -1;
		}
		double point = random.nextDouble() * total;
		double cursor = 0.0D;
		for (int i = 0; i < weights.size(); i++) {
			cursor += weights.get(i);
			if (point < cursor) {
				return i;
			}
		}
		return weights.size() - 1;
	}
}
