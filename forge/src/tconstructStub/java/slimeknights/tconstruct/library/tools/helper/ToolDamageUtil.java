package slimeknights.tconstruct.library.tools.helper;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;

/** 仅供 Mixin AP 解析可选目标，不进入任何运行时 classpath。 */
abstract class ToolDamageUtil {
	private ToolDamageUtil() {
	}

	public static boolean directDamage(IToolStackView tool, int amount,
			LivingEntity entity, ItemStack stack) {
		return false;
	}

	public static boolean damage(IToolStackView tool, int amount,
			LivingEntity entity, ItemStack stack, ModifierId cause) {
		return directDamage(tool, amount, entity, stack);
	}
}
