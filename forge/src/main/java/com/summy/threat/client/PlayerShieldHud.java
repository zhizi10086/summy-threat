package com.summy.threat.client;

import com.summy.threat.SummyThreat;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 玩家卓越护盾的左侧竖向青色 HUD。
 *
 * <p>原版 {@code BossBarColor} 没有青色枚举，因此这里用 {@link GuiGraphics#fill} 直接绘制
 * 真正的青色条；位置在屏幕左侧垂直居中，避开居中的 Jade 提示框。
 */
@Mod.EventBusSubscriber(modid = SummyThreat.MOD_ID, value = Dist.CLIENT,
		bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PlayerShieldHud {
	private static final int BAR_WIDTH = 8;
	private static final int BAR_HEIGHT = 120;
	private static final int MARGIN = 4;
	private static final int TRACK = 0xFF0B3B44;
	private static final int BORDER = 0xFF00B8C8;
	private static final int FILL = 0xFF00E5FF;
	private static final int TEXT = 0xFF7FF7FF;
	private static final double MAX = 100.0D;

	private PlayerShieldHud() {
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void onRender(RenderGuiEvent.Post event) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null || minecraft.options.hideGui) {
			return;
		}
		double value = ClientShieldState.get();
		if (value <= 0.0D) {
			return;
		}
		GuiGraphics graphics = event.getGuiGraphics();
		int screenHeight = event.getWindow().getGuiScaledHeight();
		int x = MARGIN;
		int y = Math.max(4, (screenHeight - BAR_HEIGHT) / 2);
		// 1px 青色外框 + 深色轨道；填充从下往上增长。
		graphics.fill(x - 1, y - 1, x + BAR_WIDTH + 1, y + BAR_HEIGHT + 1, BORDER);
		graphics.fill(x, y, x + BAR_WIDTH, y + BAR_HEIGHT, TRACK);
		int filled = (int) Math.round(BAR_HEIGHT * Math.min(1.0D, value / MAX));
		graphics.fill(x, y + BAR_HEIGHT - filled, x + BAR_WIDTH, y + BAR_HEIGHT, FILL);
		// 底部只显示当前值/上限，避免遮挡画面。
		Component text = Component.translatable("summy_threat.hud.player_shield",
				"%.0f".formatted(value), "%.0f".formatted(MAX)).withStyle(ChatFormatting.AQUA);
		graphics.drawString(minecraft.font, text, x, y + BAR_HEIGHT + 3, TEXT, true);
	}

	@SubscribeEvent
	public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
		ClientShieldState.clear();
	}
}
