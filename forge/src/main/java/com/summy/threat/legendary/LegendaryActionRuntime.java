package com.summy.threat.legendary;

import com.summy.threat.scaling.MonsterScaler;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/** 传奇动作公共生命周期与玩家中心测试入口。 */
public final class LegendaryActionRuntime {
	private static final int TEST_DURATION_TICKS = 40;
	private static final int MARKER_INTERVAL_TICKS = 5;
	private static final LegendaryTestTracker TEST_TRACKER = new LegendaryTestTracker();
	private static final LegendaryActionHandlerRegistry HANDLERS =
			new LegendaryActionHandlerRegistry();
	private static final List<TestSession> TEST_SESSIONS = new ArrayList<>();

	private LegendaryActionRuntime() {
	}

	public static boolean startTest(ServerPlayer player, LivingEntity target,
			LegendaryActionId action) {
		if (player == null || target == null || action == null || !TEST_TRACKER.begin(
				player.getUUID(), action)) {
			return false;
		}
		Vec3 center = player.position();
		TEST_SESSIONS.add(new TestSession(player.getUUID(), target.getUUID(),
				player.serverLevel(), action, center,
				player.serverLevel().getGameTime() + TEST_DURATION_TICKS));
		HANDLERS.handler(action).ifPresent(handler -> handler.start(
				new LegendaryActionContext(player.serverLevel(), player, target,
						action, center, true)));
		spawnMarker(player.serverLevel(), center, action);
		return true;
	}

	public static LegendaryActionHandlerRegistry handlers() {
		return HANDLERS;
	}

	@SubscribeEvent
	public static void onLivingTick(net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent event) {
		LivingEntity entity = event.getEntity();
		if (entity.level().isClientSide() || entity.tickCount % 20 != 0
				|| MonsterScaler.kindOf(entity) != MonsterScaler.Kind.BOSS) {
			return;
		}
		LegendaryActionState.ensure(entity);
	}

	@SubscribeEvent
	public static void onLivingDeath(LivingDeathEvent event) {
		LivingEntity entity = event.getEntity();
		if (MonsterScaler.kindOf(entity) == MonsterScaler.Kind.BOSS) {
			LegendaryActionState.clear(entity);
		}
		if (entity instanceof ServerPlayer player) {
			clearPlayer(player.getUUID());
		}
	}

	@SubscribeEvent
	public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
		if (event.getEntity() instanceof ServerPlayer player) {
			clearPlayer(player.getUUID());
		}
	}

	@SubscribeEvent
	public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
		if (event.getEntity() instanceof ServerPlayer player) {
			clearPlayer(player.getUUID());
		}
	}

	@SubscribeEvent
	public static void onServerTick(TickEvent.ServerTickEvent event) {
		if (event.phase != TickEvent.Phase.END || TEST_SESSIONS.isEmpty()) {
			return;
		}
		MinecraftServer server = event.getServer();
		long now = server.overworld().getGameTime();
		Iterator<TestSession> iterator = TEST_SESSIONS.iterator();
		while (iterator.hasNext()) {
			TestSession session = iterator.next();
			ServerPlayer player = server.getPlayerList().getPlayer(session.playerId());
			if (player == null || !player.isAlive() || now >= session.expiresAt()) {
				TEST_TRACKER.finish(session.playerId(), session.action());
				iterator.remove();
				continue;
			}
			if (now % MARKER_INTERVAL_TICKS == 0) {
				spawnMarker(session.level(), session.center(), session.action());
			}
		}
	}

	@SubscribeEvent
	public static void onServerStopped(ServerStoppedEvent event) {
		TEST_TRACKER.clearAll();
		TEST_SESSIONS.clear();
	}

	private static void clearPlayer(UUID playerId) {
		TEST_TRACKER.clear(playerId);
		TEST_SESSIONS.removeIf(session -> session.playerId().equals(playerId));
	}

	private static void spawnMarker(ServerLevel level, Vec3 center, LegendaryActionId action) {
		int color = action.color();
		DustParticleOptions particle = new DustParticleOptions(new Vector3f(
				((color >> 16) & 0xFF) / 255.0F,
				((color >> 8) & 0xFF) / 255.0F,
				(color & 0xFF) / 255.0F), 1.0F);
		for (int i = 0; i < 24; i++) {
			double angle = Math.PI * 2.0D * i / 24.0D;
			level.sendParticles(particle,
					center.x + Math.cos(angle) * 1.5D, center.y + 0.25D,
					center.z + Math.sin(angle) * 1.5D, 1,
					0.0D, 0.0D, 0.0D, 0.0D);
		}
		for (int i = 1; i <= 6; i++) {
			level.sendParticles(particle, center.x, center.y + i * 0.6D, center.z,
					1, 0.02D, 0.02D, 0.02D, 0.0D);
		}
	}

	private record TestSession(UUID playerId, UUID targetId, ServerLevel level,
			LegendaryActionId action, Vec3 center, long expiresAt) {
	}
}
