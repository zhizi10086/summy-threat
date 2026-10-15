package com.summy.threat.attribute;

import com.summy.threat.Config;
import com.summy.threat.SummyThreat;
import com.summy.threat.mixin.RangedAttributeAccessor;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraftforge.fml.ModList;

/**
 * 属性上限抬升。
 *
 * <p>只抬 {@code generic.armor} 与 {@code generic.max_health} 两个属性的 maxValue（min 不动）。
 * 改的是属性注册表里的单例对象，所以对玩家、原版怪与模组怪一视同仁。
 *
 * <p>必须走 Mixin accessor 而不是反射：生产环境字段名是 SRG（maxValue → f_22308_），
 * 按字面名反射在正式包里会失效。
 */
public final class AttributeCaps {
	/** 原版 generic.armor 的上限（Attributes.ARMOR 声明为 RangedAttribute(0, 0, 30)） */
	public static final double VANILLA_ARMOR_MAX = 30.0D;
	/** 原版 generic.max_health 的上限（Attributes.MAX_HEALTH 声明为 RangedAttribute(20, 0, 1024)） */
	public static final double VANILLA_MAX_HEALTH_MAX = 1024.0D;

	/** AttributeFix 已加载时只提示一次，避免每次配置重载都刷屏 */
	private static boolean warnedAboutAttributeFix;

	private AttributeCaps() {
	}

	/** 按当前配置应用上限；配置尚未加载时安全跳过（幂等，可重复调用） */
	public static void apply() {
		// AttributeFix 与本模组改的是同一个字段，谁后应用谁说了算：这里直接让位，避免两边互相覆盖
		if (ModList.get().isLoaded("attributefix")) {
			if (!warnedAboutAttributeFix) {
				warnedAboutAttributeFix = true;
				SummyThreat.LOGGER.warn("[Summy Threat] 检测到 AttributeFix：属性上限交由它掌管"
						+ "（它默认抬到 1000000），本模组的属性上限配置将被忽略。");
			}
			return;
		}
		if (!Config.SPEC.isLoaded()) {
			return;
		}
		boolean enabled = Config.ENABLE_ATTRIBUTE_CAP_OVERRIDE.get();
		double armorTarget = enabled
				? Math.max(VANILLA_ARMOR_MAX, Config.ARMOR_MAX_VALUE.get())
				: VANILLA_ARMOR_MAX;
		double healthTarget = enabled
				? Math.max(VANILLA_MAX_HEALTH_MAX, Config.MAX_HEALTH_MAX_VALUE.get())
				: VANILLA_MAX_HEALTH_MAX;
		setMaxValue(Attributes.ARMOR, "generic.armor", armorTarget);
		setMaxValue(Attributes.MAX_HEALTH, "generic.max_health", healthTarget);
	}

	/** 把某个 RangedAttribute 的上限改成 target；已经是目标值就什么都不做（幂等） */
	private static void setMaxValue(Attribute attribute, String name, double target) {
		if (!(attribute instanceof RangedAttribute ranged)) {
			return;
		}
		double current = ranged.getMaxValue();
		if (current == target) {
			return;
		}
		((RangedAttributeAccessor) (Object) ranged).summyThreat$setMaxValue(target);
		SummyThreat.LOGGER.info("[Summy Threat] 属性上限已调整：{} {} -> {}", name, current, target);
	}
}
