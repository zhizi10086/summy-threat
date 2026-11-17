package com.summy.threat.legendary;

import com.summy.threat.SummyThreat;
import net.minecraft.world.entity.LivingEntity;

/** Boss 传奇动作的持久选择与冷却入口。 */
public final class LegendaryActionState {
	private LegendaryActionState() {
	}

	public static LegendaryActionId selection(LivingEntity entity) {
		return LegendaryActionStateData.selection(entity.getPersistentData());
	}

	public static LegendaryActionId ensure(LivingEntity entity) {
		LegendaryBossProfile profile = LegendaryBossProfileResolver.resolve(entity);
		LegendaryActionId before = selection(entity);
		String rawBefore = entity.getPersistentData().getString(LegendaryActionStateData.TAG_ACTION);
		LegendaryActionId selected = LegendaryActionStateData.ensureSelection(
				entity.getPersistentData(), profile, entity.getRandom());
		if (!rawBefore.isBlank() && before == null && profile.eligible()) {
			SummyThreat.LOGGER.warn("[Summy Threat] Boss 传奇动作标签包含未知 ID {}，已重新抽取：{}",
					rawBefore, entity.getUUID());
		}
		return selected;
	}

	public static boolean ready(LivingEntity entity, long gameTime) {
		return LegendaryActionStateData.ready(entity.getPersistentData(), gameTime);
	}

	public static void markStarted(LivingEntity entity, long gameTime) {
		LegendaryActionStateData.markStarted(entity.getPersistentData(), gameTime);
	}

	public static void clear(LivingEntity entity) {
		LegendaryActionStateData.clear(entity.getPersistentData());
	}
}
