package com.summy.threat.legendary;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;

import java.util.List;

/** 传奇动作选择与冷却的纯 NBT 规则。 */
public final class LegendaryActionStateData {
	public static final String TAG_ACTION = "summy_threat:legendary_action";
	public static final String TAG_NEXT_READY = "summy_threat:legendary_action_next_ready";
	public static final int COOLDOWN_TICKS = 600;

	private LegendaryActionStateData() {
	}

	public static LegendaryActionId selection(CompoundTag data) {
		return LegendaryActionId.byId(data.getString(TAG_ACTION));
	}

	public static void setSelection(CompoundTag data, LegendaryActionId action) {
		if (action == null) {
			data.remove(TAG_ACTION);
		} else {
			data.putString(TAG_ACTION, action.id());
		}
	}

	public static LegendaryActionId ensureSelection(CompoundTag data,
			LegendaryBossProfile profile, RandomSource random) {
		if (profile == null || !profile.eligible()) {
			clear(data);
			return null;
		}
		List<LegendaryActionId> eligible = LegendaryActionPool.eligible(profile);
		LegendaryActionId current = selection(data);
		if (current != null && eligible.contains(current)) {
			return current;
		}
		LegendaryActionId rolled = LegendaryActionPool.roll(profile, random);
		setSelection(data, rolled);
		return rolled;
	}

	public static boolean ready(CompoundTag data, long gameTime) {
		return gameTime >= data.getLong(TAG_NEXT_READY);
	}

	public static void markStarted(CompoundTag data, long gameTime) {
		data.putLong(TAG_NEXT_READY, gameTime + COOLDOWN_TICKS);
	}

	public static void clear(CompoundTag data) {
		data.remove(TAG_ACTION);
		data.remove(TAG_NEXT_READY);
	}
}
