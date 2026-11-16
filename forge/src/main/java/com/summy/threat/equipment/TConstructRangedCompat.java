package com.summy.threat.equipment;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 匠魂弩不触发 ArrowLooseEvent。
 *
 * <p>右键或结束蓄力时为同 tick 的弩射击准备快照；真正投射物加入世界时，
 * 通用 EntityJoinLevelEvent 会在匠魂完成箭矢原生属性后读取该快照。</p>
 */
public final class TConstructRangedCompat {
	private TConstructRangedCompat() {
	}

	@SubscribeEvent
	public static void onRightClick(PlayerInteractEvent.RightClickItem event) {
		if (event.isCanceled() || event.getLevel().isClientSide()
				|| !(event.getEntity() instanceof ServerPlayer player)) {
			return;
		}
		seed(player, event.getItemStack());
	}

	@SubscribeEvent
	public static void onStopUsing(LivingEntityUseItemEvent.Stop event) {
		if (event.isCanceled() || event.getEntity().level().isClientSide()
				|| !(event.getEntity() instanceof ServerPlayer player)) {
			return;
		}
		seed(player, event.getItem());
	}

	private static void seed(ServerPlayer player, ItemStack stack) {
		if (!stack.is(EquipmentTypeResolver.TINKERS_CROSSBOW)
				|| EquipmentTypeResolver.category(stack) != EquipmentCategory.RANGED) {
			return;
		}
		RangedWeaponRuntime.beginExternalShot(player, stack,
				RangedShotSource.TINKERS_CROSSBOW);
	}
}
