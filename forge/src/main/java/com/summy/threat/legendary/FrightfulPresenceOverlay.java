package com.summy.threat.legendary;

import com.mojang.blaze3d.systems.RenderSystem;
import com.summy.threat.SummyThreat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/** 畏怖只在收到服务端状态的本地玩家屏幕上绘制。 */
public final class FrightfulPresenceOverlay {
	private static final ResourceLocation VIGNETTE = new ResourceLocation(
			SummyThreat.MOD_ID, "textures/effect/threat_vignette.png");
	private static ClientLevel level;
	private static long startedAt;
	private static long endsAt;
	private static int color;

	static final IGuiOverlay OVERLAY = (gui, graphics, partialTick, width, height) -> {
		Minecraft client = Minecraft.getInstance();
		if (level == null || level != client.level || level.getGameTime() >= endsAt
				|| client.player == null || !client.player.isAlive()) {
			level = null;
			return;
		}
		if (client.options.hideGui) {
			return;
		}
		float age = level.getGameTime() - startedAt + partialTick;
		float remaining = endsAt - level.getGameTime() - partialTick;
		float alpha = 0.53F * Mth.clamp(Math.min(age / 6.0F, remaining / 10.0F), 0.0F, 1.0F);
		// 压低元素色亮度，让暗角保留色相和中心视野。
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		graphics.setColor(((color >> 16) & 255) / 1020.0F,
				((color >> 8) & 255) / 1020.0F, (color & 255) / 1020.0F, alpha);
		graphics.blit(VIGNETTE, 0, 0, width, height, 0, 0, 256, 256, 256, 256);
		graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
		RenderSystem.disableBlend();
	};

	private FrightfulPresenceOverlay() {
	}

	public static void apply(int duration, int tint) {
		ClientLevel current = Minecraft.getInstance().level;
		if (current == null || duration <= 0) {
			level = null;
			return;
		}
		long now = current.getGameTime();
		if (level != current || now >= endsAt || color != tint) {
			startedAt = now;
		}
		level = current;
		endsAt = now + duration;
		color = tint;
	}
}
