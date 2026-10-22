package com.summy.threat.combat;

import com.summy.threat.Config;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 内部临时削甲引擎。
 *
 * <p>每个来源独立保存层数，层百分比按加法汇总后一次性写入后台护甲 modifier。
 * 当前生产来源只有 TL 腐蚀；Boss / 领袖的破甲能力后续复用同一入口。
 */
public final class ArmorShred {
	private static final UUID ID_ARMOR_SHRED =
			UUID.fromString("6f5c1a2e-0009-4a0b-9d1e-000000000105");
	private static final String MODIFIER_NAME = "summy_threat:armor_shred";
	private static final String SOURCE_CORROSION = "corrosion";

	/** 目标 UUID → 来源 ID → 各层到期 tick 与应用时百分比。 */
	private static final Map<UUID, Map<String, List<Stack>>> SHREDS = new HashMap<>();

	private record Stack(long expiresAt, double percent) {
	}

	private ArmorShred() {
	}

	/** 应用一层腐蚀；数值读取 SERVER 配置。 */
	public static void applyCorrosion(LivingEntity victim) {
		if (!Config.SPEC.isLoaded()) {
			return;
		}
		apply(victim, SOURCE_CORROSION,
				Config.CORROSION_PER_STACK_PERCENT.get(),
				Config.CORROSION_MAX_STACKS.get(),
				Config.CORROSION_DURATION_SECONDS.get() * 20);
	}

	/** 清除腐蚀层；其它削甲来源不受影响。 */
	public static void clearCorrosion(LivingEntity victim) {
		clearSource(victim, SOURCE_CORROSION);
	}

	/** 每 tick 清理过期层并刷新属性 modifier。 */
	public static void tick(LivingEntity victim) {
		if (SHREDS.containsKey(victim.getUUID())) {
			refresh(victim, victim.level().getGameTime());
		}
	}

	/** 实体死亡或移除时清空全部临时削甲。 */
	public static void cleanup(LivingEntity victim) {
		SHREDS.remove(victim.getUUID());
		AttributeInstance armor = victim.getAttribute(Attributes.ARMOR);
		if (armor != null) {
			armor.removeModifier(ID_ARMOR_SHRED);
		}
	}

	/** 测试和后续内部来源使用：按来源叠加，百分比最终整体限制在 0~100。 */
	public static void apply(LivingEntity victim, String sourceId, double percentPerStack,
			int maxStacks, int durationTicks) {
		if (victim.level().isClientSide || percentPerStack <= 0.0D
				|| maxStacks <= 0 || durationTicks <= 0) {
			return;
		}
		long now = victim.level().getGameTime();
		Map<String, List<Stack>> sources =
				SHREDS.computeIfAbsent(victim.getUUID(), id -> new HashMap<>());
		List<Stack> stacks = sources.computeIfAbsent(sourceId, id -> new ArrayList<>());
		prune(stacks, now);
		long expiresAt = now + durationTicks;
		if (stacks.size() >= maxStacks) {
			int oldest = 0;
			for (int i = 1; i < stacks.size(); i++) {
				if (stacks.get(i).expiresAt() < stacks.get(oldest).expiresAt()) {
					oldest = i;
				}
			}
			stacks.set(oldest, new Stack(expiresAt, percentPerStack));
		} else {
			stacks.add(new Stack(expiresAt, percentPerStack));
		}
		while (stacks.size() > maxStacks) {
			removeOldest(stacks);
		}
		refresh(victim, now);
	}

	/** 当前所有临时削甲来源的合计百分比，0~1。 */
	public static double activePercent(LivingEntity victim) {
		Map<String, List<Stack>> sources = SHREDS.get(victim.getUUID());
		if (sources == null) {
			return 0.0D;
		}
		long now = victim.level().getGameTime();
		double total = 0.0D;
		for (List<Stack> stacks : sources.values()) {
			for (Stack stack : stacks) {
				if (stack.expiresAt() > now) {
					total += stack.percent();
				}
			}
		}
		return Math.max(0.0D, Math.min(1.0D, total / 100.0D));
	}

	private static void clearSource(LivingEntity victim, String sourceId) {
		Map<String, List<Stack>> sources = SHREDS.get(victim.getUUID());
		if (sources == null) {
			return;
		}
		sources.remove(sourceId);
		if (sources.isEmpty()) {
			cleanup(victim);
		} else {
			refresh(victim, victim.level().getGameTime());
		}
	}

	private static void refresh(LivingEntity victim, long now) {
		Map<String, List<Stack>> sources = SHREDS.get(victim.getUUID());
		AttributeInstance armor = victim.getAttribute(Attributes.ARMOR);
		if (sources == null) {
			if (armor != null) {
				armor.removeModifier(ID_ARMOR_SHRED);
			}
			return;
		}

		double totalPercent = 0.0D;
		Iterator<Map.Entry<String, List<Stack>>> sourceIterator = sources.entrySet().iterator();
		while (sourceIterator.hasNext()) {
			List<Stack> stacks = sourceIterator.next().getValue();
			prune(stacks, now);
			if (stacks.isEmpty()) {
				sourceIterator.remove();
				continue;
			}
			for (Stack stack : stacks) {
				totalPercent += stack.percent();
			}
		}
		if (armor != null) {
			armor.removeModifier(ID_ARMOR_SHRED);
			if (totalPercent > 0.0D) {
				double applied = Math.min(100.0D, totalPercent) / 100.0D;
				armor.addTransientModifier(new AttributeModifier(
						ID_ARMOR_SHRED, MODIFIER_NAME, -applied,
						AttributeModifier.Operation.MULTIPLY_TOTAL));
			}
		}
		if (sources.isEmpty()) {
			SHREDS.remove(victim.getUUID());
		}
	}

	private static void prune(List<Stack> stacks, long now) {
		stacks.removeIf(stack -> stack.expiresAt() <= now);
	}

	private static void removeOldest(List<Stack> stacks) {
		int oldest = 0;
		for (int i = 1; i < stacks.size(); i++) {
			if (stacks.get(i).expiresAt() < stacks.get(oldest).expiresAt()) {
				oldest = i;
			}
		}
		stacks.remove(oldest);
	}
}
