package com.summy.threat.equipment;

import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 武器最终承伤观察入口。
 *
 * <p>必须在卓越护盾、护甲、保护、抗性和吸收之后读取金额；只负责吸血与上下文清理，
 * 不改写主伤害，避免把护盾吸收量误算成真实生命伤害。</p>
 */
public final class WeaponFinalRuntime {
	private WeaponFinalRuntime() {
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void onLivingDamage(LivingDamageEvent event) {
		MeleeWeaponRuntime.onFinalDamage(event);
		RangedWeaponRuntime.onFinalDamage(event);
	}
}
