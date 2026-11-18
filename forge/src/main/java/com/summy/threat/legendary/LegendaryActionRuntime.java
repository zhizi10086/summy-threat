package com.summy.threat.legendary;

import com.summy.threat.SummyThreat;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 传奇动作公共生命周期与玩家中心测试入口。 */
public final class LegendaryActionRuntime {
	private static final int MARKER_INTERVAL_TICKS = 5;
	private static final int TEST_SAFETY_TIMEOUT_TICKS = 1200;
	private static final LegendaryTestTracker TEST_TRACKER = new LegendaryTestTracker();
	private static final LegendaryActionHandlerRegistry HANDLERS =
			new LegendaryActionHandlerRegistry();
	private static final List<TestSession> TEST_SESSIONS = new ArrayList<>();
	private static final Map<UUID, BossSession> BOSS_ACTIONS = new HashMap<>();
	private static final Map<UUID, Integer> COMBAT_TICKS = new HashMap<>();

	private LegendaryActionRuntime() {
	}

	public enum TestStartResult {
		STARTED,
		DUPLICATE,
		NO_ACTION_INSTANCE
	}

	public static TestStartResult startTest(ServerPlayer player, LivingEntity target,
			LegendaryActionId action) {
		if (player == null || target == null || action == null || !TEST_TRACKER.begin(
				player.getUUID(), action)) {
			return TestStartResult.DUPLICATE;
		}
		Vec3 center = player.position();
		LegendaryActionContext context = new LegendaryActionContext(
				player.serverLevel(), player, target, action, center, true);
		LegendaryActionInstance instance = diagnosticInstance(action);
		if (HANDLERS.handler(action).isPresent()) {
			try {
				instance = HANDLERS.handler(action).orElseThrow().start(context);
			} catch (RuntimeException exception) {
				TEST_TRACKER.finish(player.getUUID(), action);
				SummyThreat.LOGGER.error("[Summy Threat] 传奇动作 {} 启动失败",
						action.id(), exception);
				return TestStartResult.NO_ACTION_INSTANCE;
			}
		}
		if (instance == null) {
			TEST_TRACKER.finish(player.getUUID(), action);
			return TestStartResult.NO_ACTION_INSTANCE;
		}
		TEST_SESSIONS.add(new TestSession(player.getUUID(), context, instance));
		spawnMarker(player.serverLevel(), center, action);
		return TestStartResult.STARTED;
	}

	public static LegendaryActionHandlerRegistry handlers() {
		return HANDLERS;
	}

	@SubscribeEvent
	public static void onLivingTick(net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent event) {
		LivingEntity entity = event.getEntity();
		if (entity.level().isClientSide()
				|| MonsterScaler.kindOf(entity) != MonsterScaler.Kind.BOSS) {
			return;
		}
		LegendaryActionId selected = LegendaryActionState.ensure(entity);
		LivingEntity target = combatTarget(entity);
		if (selected == null || target == null) {
			COMBAT_TICKS.remove(entity.getUUID());
			return;
		}
		int ticks = COMBAT_TICKS.merge(entity.getUUID(), 1, Integer::sum);
		long now = entity.level().getGameTime();
		if (ticks < 200 || !LegendaryActionState.ready(entity, now)
				|| BOSS_ACTIONS.containsKey(entity.getUUID())
				|| selected == LegendaryActionId.LEGENDARY_RIPOSTE) {
			return;
		}
		startBossAction(entity, target, selected, now);
	}

	public static boolean startReactive(LivingEntity owner, LivingEntity target,
			LegendaryActionId expected, long now) {
		if (target == null || !canStartReactive(owner, expected, now)) {
			return false;
		}
		return startBossAction(owner, target, expected, now);
	}

	public static boolean canStartReactive(LivingEntity owner,
			LegendaryActionId expected, long now) {
		return owner != null && expected != null
				&& MonsterScaler.kindOf(owner) == MonsterScaler.Kind.BOSS
				&& LegendaryActionState.selection(owner) == expected
				&& COMBAT_TICKS.getOrDefault(owner.getUUID(), 0) >= 200
				&& LegendaryActionState.ready(owner, now)
				&& !BOSS_ACTIONS.containsKey(owner.getUUID());
	}

	private static boolean startBossAction(LivingEntity owner, LivingEntity target,
			LegendaryActionId action, long now) {
		LegendaryActionHandler handler = HANDLERS.handler(action).orElse(null);
		if (handler == null || !(owner.level() instanceof ServerLevel level)) {
			return false;
		}
		LegendaryActionContext context = new LegendaryActionContext(
				level, owner, target, action, owner.position(), false);
		LegendaryActionInstance instance;
		try {
			instance = handler.start(context);
		} catch (RuntimeException exception) {
			SummyThreat.LOGGER.error("[Summy Threat] Boss {} 启动传奇动作 {} 失败",
					owner.getUUID(), action.id(), exception);
			return false;
		}
		if (instance == null) {
			return false;
		}
		BOSS_ACTIONS.put(owner.getUUID(), new BossSession(owner.getUUID(), context, instance));
		LegendaryActionState.markStarted(owner, now);
		return true;
	}

	private static LivingEntity combatTarget(LivingEntity owner) {
		LivingEntity current = com.summy.threat.leader.LeaderTargeting.currentTarget(owner);
		if (current instanceof ServerPlayer player && player.isAlive()) {
			return player;
		}
		if (!(owner.level() instanceof ServerLevel level)) {
			return null;
		}
		return level.getPlayers(player -> player.isAlive()
						&& !player.isCreative()
						&& !player.isSpectator()
						&& player.distanceToSqr(owner) <= 32.0D * 32.0D)
				.stream()
				.min(Comparator.comparingDouble(player -> player.distanceToSqr(owner)))
				.orElse(null);
	}

	@SubscribeEvent
	public static void onLivingDeath(LivingDeathEvent event) {
		LivingEntity entity = event.getEntity();
		if (MonsterScaler.kindOf(entity) == MonsterScaler.Kind.BOSS) {
			LegendaryActionState.clear(entity);
			COMBAT_TICKS.remove(entity.getUUID());
			LegendaryRiposteService.clearOwner(entity.getUUID());
			BossSession session = BOSS_ACTIONS.remove(entity.getUUID());
			if (session != null) {
				finishBoss(session, LegendaryActionInstance.StopReason.OWNER_INVALID);
			}
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
		if (event.phase != TickEvent.Phase.END) {
			return;
		}
		MinecraftServer server = event.getServer();
		long now = server.overworld().getGameTime();
		tickTests(server, now);
		tickBossActions(now);
	}

	private static void tickTests(MinecraftServer server, long now) {
		Iterator<TestSession> iterator = TEST_SESSIONS.iterator();
		while (iterator.hasNext()) {
			TestSession session = iterator.next();
			ServerPlayer player = server.getPlayerList().getPlayer(session.playerId());
			if (player == null || !player.isAlive()) {
				finish(session, LegendaryActionInstance.StopReason.OWNER_INVALID);
				iterator.remove();
				continue;
			}
			boolean keepRunning;
			try {
				keepRunning = session.instance().tick(session.context(), session.age());
			} catch (RuntimeException exception) {
				SummyThreat.LOGGER.error("[Summy Threat] 传奇动作 {} tick 失败",
						session.action().id(), exception);
				keepRunning = false;
			}
			session.age++;
			if (!keepRunning || session.age >= TEST_SAFETY_TIMEOUT_TICKS) {
				finish(session, LegendaryActionInstance.StopReason.COMPLETED);
				iterator.remove();
				continue;
			}
			if (now % MARKER_INTERVAL_TICKS == 0) {
				spawnMarker(session.level(), session.center(), session.action());
			}
		}
	}

	private static void tickBossActions(long now) {
		Iterator<Map.Entry<UUID, BossSession>> iterator = BOSS_ACTIONS.entrySet().iterator();
		while (iterator.hasNext()) {
			BossSession session = iterator.next().getValue();
			if (!(session.level().getEntity(session.ownerId()) instanceof LivingEntity owner)
					|| !owner.isAlive()
					|| MonsterScaler.kindOf(owner) != MonsterScaler.Kind.BOSS) {
				finishBoss(session, LegendaryActionInstance.StopReason.OWNER_INVALID);
				iterator.remove();
				continue;
			}
			boolean keepRunning;
			try {
				keepRunning = session.instance().tick(session.context(), session.age());
			} catch (RuntimeException exception) {
				SummyThreat.LOGGER.error("[Summy Threat] 传奇动作 {} tick 失败",
						session.action().id(), exception);
				keepRunning = false;
			}
			session.age++;
			if (!keepRunning || session.age >= TEST_SAFETY_TIMEOUT_TICKS) {
				finishBoss(session, LegendaryActionInstance.StopReason.COMPLETED);
				iterator.remove();
			}
		}
	}

	@SubscribeEvent
	public static void onServerStopped(ServerStoppedEvent event) {
		TEST_TRACKER.clearAll();
		TEST_SESSIONS.clear();
		for (BossSession session : BOSS_ACTIONS.values()) {
			finishBoss(session, LegendaryActionInstance.StopReason.SERVER_STOPPED);
		}
		BOSS_ACTIONS.clear();
		COMBAT_TICKS.clear();
		LegendaryRiposteService.clearAll();
	}

	private static void clearPlayer(UUID playerId) {
		TEST_TRACKER.clear(playerId);
		Iterator<TestSession> iterator = TEST_SESSIONS.iterator();
		while (iterator.hasNext()) {
			TestSession session = iterator.next();
			if (session.playerId().equals(playerId)) {
				finish(session, LegendaryActionInstance.StopReason.CANCELLED);
				iterator.remove();
			}
		}
	}

	private static void finish(TestSession session,
			LegendaryActionInstance.StopReason reason) {
		TEST_TRACKER.finish(session.playerId(), session.action());
		try {
			session.instance().stop(session.context(), reason);
		} catch (RuntimeException exception) {
			SummyThreat.LOGGER.error("[Summy Threat] 传奇动作 {} 清理失败",
					session.action().id(), exception);
		}
	}

	private static void finishBoss(BossSession session,
			LegendaryActionInstance.StopReason reason) {
		try {
			session.instance().stop(session.context(), reason);
		} catch (RuntimeException exception) {
			SummyThreat.LOGGER.error("[Summy Threat] 传奇动作 {} 清理失败",
					session.action().id(), exception);
		}
	}

	private static LegendaryActionInstance diagnosticInstance(LegendaryActionId action) {
		return (context, age) -> age < 40;
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

	private static final class TestSession {
		private final UUID playerId;
		private final LegendaryActionContext context;
		private final LegendaryActionInstance instance;
		private int age;

		private TestSession(UUID playerId, LegendaryActionContext context,
				LegendaryActionInstance instance) {
			this.playerId = playerId;
			this.context = context;
			this.instance = instance;
		}

		private UUID playerId() {
			return playerId;
		}

		private LegendaryActionContext context() {
			return context;
		}

		private LegendaryActionInstance instance() {
			return instance;
		}

		private int age() {
			return age;
		}

		private ServerLevel level() {
			return context.level();
		}

		private Vec3 center() {
			return context.center();
		}

		private LegendaryActionId action() {
			return context.action();
		}
	}

	private static final class BossSession {
		private final UUID ownerId;
		private final LegendaryActionContext context;
		private final LegendaryActionInstance instance;
		private int age;

		private BossSession(UUID ownerId, LegendaryActionContext context,
				LegendaryActionInstance instance) {
			this.ownerId = ownerId;
			this.context = context;
			this.instance = instance;
		}

		private UUID ownerId() {
			return ownerId;
		}

		private LegendaryActionContext context() {
			return context;
		}

		private LegendaryActionInstance instance() {
			return instance;
		}

		private int age() {
			return age;
		}

		private ServerLevel level() {
			return context.level();
		}

		private LegendaryActionId action() {
			return context.action();
		}
	}
}
