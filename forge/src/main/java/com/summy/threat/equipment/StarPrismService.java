package com.summy.threat.equipment;

import com.summy.threat.Config;
import com.summy.threat.item.ModItems;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEquipmentChangeEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Optional;
import java.util.Random;

/** 首次主手揭示与待刷新执行。所有抽取均由服务端完成。 */
public final class StarPrismService {
	private StarPrismService() {
	}

	@SubscribeEvent
	public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
		if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide()
				|| !(event.player instanceof ServerPlayer player) || player.tickCount % 5 != 0) {
			return;
		}
		processMainHand(player);
	}

	@SubscribeEvent
	public static void onEquipmentChange(LivingEquipmentChangeEvent event) {
		if (event.getSlot().getType() == net.minecraft.world.entity.EquipmentSlot.Type.HAND
				&& event.getEntity() instanceof ServerPlayer player) {
			processMainHand(player);
		}
	}

	public static boolean processMainHand(ServerPlayer player) {
		if (!Config.SPEC.isLoaded() || !Config.EXIMUS_EQUIPMENT_ENABLED.get()
				|| !Config.STAR_PRISM_ENABLED.get()) {
			return false;
		}
		ItemStack stack = player.getItemInHand(InteractionHand.MAIN_HAND);
		if (!stack.is(ModItems.ABYSSAL_STAR_PRISM.get())) {
			return false;
		}
		Optional<StarPrismData> current = StarPrismData.read(stack);
		PrismProcessingMode mode = PrismProcessingMode.resolve(current);
		if (!mode.processes()) {
			return false;
		}
		Optional<StarPrismData> rolled = StarPrismRoller.roll(mode.rollMode(),
				StarPrismConfig.rollSettings(), StarPrismConfig.weightProvider(),
				new Random(player.getRandom().nextLong()));
		if (rolled.isEmpty()) {
			return false;
		}
		StarPrismData.write(stack, rolled.get());
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		player.inventoryMenu.broadcastChanges();
		player.serverLevel().sendParticles(ParticleTypes.END_ROD,
				player.getX(), player.getY() + 1.2D, player.getZ(),
				8, 0.22D, 0.3D, 0.22D, 0.015D);
		player.serverLevel().playSound(null, player.blockPosition(),
				SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.45F, 1.35F);
		return true;
	}
}
