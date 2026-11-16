package com.summy.threat.equipment;

import com.summy.threat.Config;
import com.summy.threat.SummyThreat;
import com.summy.threat.api.event.ThreatConfigChangedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** B15 跨字段配置校验；非法组合让对应能力安全失效。 */
public final class EquipmentConfigValidation {
	private static boolean lastMendingValid = true;
	private static boolean lastDurabilityValid = true;

	private EquipmentConfigValidation() {
	}

	public static boolean emergencyMendingValid() {
		if (!Config.SPEC.isLoaded()) {
			return true;
		}
		return Config.EMERGENCY_MENDING_HEAL_MIN.get()
				<= Config.EMERGENCY_MENDING_HEAL_MAX.get();
	}

	public static boolean durabilityCapValid() {
		if (!Config.SPEC.isLoaded()) {
			return true;
		}
		return Config.ARMOR_DURABILITY_CAP_MIN.get()
				<= Config.ARMOR_DURABILITY_CAP_MAX.get();
	}

	@SubscribeEvent
	public static void onConfigChanged(ThreatConfigChangedEvent event) {
		boolean mending = emergencyMendingValid();
		boolean durability = durabilityCapValid();
		if (!mending && lastMendingValid) {
			SummyThreat.LOGGER.error(
					"[Summy Threat] 应急生息配置非法：治疗下限大于上限，能力已安全失效");
		}
		if (!durability && lastDurabilityValid) {
			SummyThreat.LOGGER.error(
					"[Summy Threat] II 阶护甲耐久保护配置非法：最小值大于最大值，保护已安全失效");
		}
		lastMendingValid = mending;
		lastDurabilityValid = durability;
	}
}
