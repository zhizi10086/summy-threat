package com.summy.threat.legendary;

import com.summy.threat.SummyThreat;
import com.summy.threat.scaling.MonsterScaler;
import com.summy.threat.scaling.ScalingState;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
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
		if (needsTestMarker(action)) {
			spawnMarker(player.serverLevel(), center, action);
		}
		return TestStartResult.STARTED;
	}

	public static LegendaryActionHandlerRegistry handlers() {
		return HANDLERS;
	}

	@SubscribeEvent
	public static void onLivingTick(net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent event) {
		LivingEntity entity = event.getEntity();
		if (!eligibleBoss(entity)) {
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
		if (!validPlayer(owner, target) || !canStartReactive(owner, expected, now)) {
			return false;
		}
		return startBossAction(owner, target, expected, now);
	}

	public static boolean canStartReactive(LivingEntity owner,
			LegendaryActionId expected, long now) {
		return expected != null && eligibleBoss(owner)
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
		if (validPlayer(owner, current)) {
			return current;
		}
		if (!(owner.level() instanceof ServerLevel level)) {
			return null;
		}
		return level.getPlayers(player -> validPlayer(owner, player)
						&& player.distanceToSqr(owner) <= 32.0D * 32.0D)
				.stream()
				.min(Comparator.comparingDouble(player -> player.distanceToSqr(owner)))
				.orElse(null);
	}

	private static boolean eligibleBoss(LivingEntity owner) {
		return owner != null && owner.level() instanceof ServerLevel && owner.isAlive()
				&& ScalingState.enabled()
				&& owner.getPersistentData().getBoolean(MonsterScaler.TAG_SCALED)
				&& MonsterScaler.kindOf(owner) == MonsterScaler.Kind.BOSS;
	}

	static boolean validPlayer(LivingEntity owner, LivingEntity target) {
		return owner != null && target instanceof ServerPlayer player && player.isAlive()
				&& player.level() == owner.level() && !player.isCreative() && !player.isSpectator();
	}

	// 伤害事件可在动作 tick 内同步回调，先移除会话，待 tick 返回时清理。
	@SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.MONITOR)
	public static void onLivingDeath(LivingDeathEvent event) {
		LivingEntity entity = event.getEntity();
		if (entity.level().isClientSide()) {
			return;
		}
		if (MonsterScaler.kindOf(entity) == MonsterScaler.Kind.BOSS) {
			LegendaryActionState.clear(entity);
		}
		removeOwner(entity);
	}

	@SubscribeEvent
	public static void onEntityLeave(EntityLeaveLevelEvent event) {
		if (!event.getLevel().isClientSide() && event.getEntity() instanceof LivingEntity living) {
			removeOwner(living);
		}
	}

	private static void removeOwner(LivingEntity owner) {
		UUID id = owner.getUUID();
		COMBAT_TICKS.remove(id);
		LegendaryRiposteService.clearOwner(id);
		FrightfulPresenceService.clearOwner(id, (ServerLevel) owner.level());
		BossSession boss = BOSS_ACTIONS.get(id);
		if (boss != null && boss.context.owner() == owner) {
			cancel(boss, LegendaryActionInstance.StopReason.OWNER_INVALID);
		}
		if (owner instanceof ServerPlayer player) {
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
		long now = event.getServer().overworld().getGameTime();
		for (TestSession session : List.copyOf(TEST_SESSIONS)) {
			if (session.reason != null) {
				continue;
			}
			if (!session.context.owner().isAlive() || session.context.owner().level() != session.level()) {
				cancel(session, LegendaryActionInstance.StopReason.OWNER_INVALID);
			} else if (tick(session) && needsTestMarker(session.action())
					&& now % MARKER_INTERVAL_TICKS == 0) {
				spawnMarker(session.level(), session.context.center(), session.action());
			}
		}
		if (!ScalingState.enabled()) {
			return;
		}
		for (BossSession session : List.copyOf(BOSS_ACTIONS.values())) {
			if (session.reason != null) {
				continue;
			}
			LivingEntity owner = session.context.owner();
			if (!eligibleBoss(owner) || session.level().getEntity(session.ownerId()) != owner) {
				removeOwner(owner);
			} else {
				tick(session);
			}
		}
	}

	public static void pauseBossActions(MinecraftServer server) {
		for (BossSession session : List.copyOf(BOSS_ACTIONS.values())) {
			cancel(session, LegendaryActionInstance.StopReason.CANCELLED);
		}
		COMBAT_TICKS.clear();
		LegendaryRiposteService.clearAll();
		FrightfulPresenceService.clearFormal(server);
	}

	private static boolean tick(Session session) {
		session.ticking = true;
		boolean keepRunning = false;
		try {
			keepRunning = session.instance.tick(session.context, session.age++);
		} catch (RuntimeException exception) {
			SummyThreat.LOGGER.error("[Summy Threat] 传奇动作 {} tick 失败",
					session.action().id(), exception);
		} finally {
			session.ticking = false;
			if (session.reason != null) {
				stop(session);
			}
		}
		if (!keepRunning || session.age >= TEST_SAFETY_TIMEOUT_TICKS) {
			cancel(session, LegendaryActionInstance.StopReason.COMPLETED);
		}
		return session.reason == null;
	}

	@SubscribeEvent
	public static void onServerStopping(ServerStoppingEvent event) {
		pauseBossActions(event.getServer());
		for (TestSession session : List.copyOf(TEST_SESSIONS)) {
			cancel(session, LegendaryActionInstance.StopReason.SERVER_STOPPED);
		}
		TEST_TRACKER.clearAll();
	}

	private static void clearPlayer(UUID playerId) {
		for (TestSession session : List.copyOf(TEST_SESSIONS)) {
			if (session.playerId().equals(playerId)) {
				cancel(session, LegendaryActionInstance.StopReason.CANCELLED);
			}
		}
	}

	private static void cancel(Session session, LegendaryActionInstance.StopReason reason) {
		if (session.reason != null) {
			return;
		}
		session.reason = reason;
		session.context.cancel();
		if (session instanceof TestSession test) {
			TEST_SESSIONS.remove(test);
			TEST_TRACKER.finish(test.playerId(), test.action());
		} else {
			BOSS_ACTIONS.remove(session.context.owner().getUUID(), session);
		}
		if (!session.ticking) {
			stop(session);
		}
	}

	private static void stop(Session session) {
		try {
			session.instance.stop(session.context, session.reason);
		} catch (RuntimeException exception) {
			SummyThreat.LOGGER.error("[Summy Threat] 传奇动作 {} 清理失败",
					session.action().id(), exception);
		}
	}

	private static LegendaryActionInstance diagnosticInstance(LegendaryActionId action) {
		return (context, age) -> age < 40;
	}

	private static boolean needsTestMarker(LegendaryActionId action) {
		return switch (action) {
			case JADE_LIGHT, MIRROR_STEP, COLLAPSING_HORIZON, LAIR_BREACH,
					DOOM_CLOCK, REVERSE_GRAVITY, LEGENDARY_RIPOSTE,
					DRAGON_LAIR_PULSE, DECAY_TIDE -> false;
			default -> true;
		};
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

	private static class Session {
		final LegendaryActionContext context;
		final LegendaryActionInstance instance;
		int age;
		boolean ticking;
		LegendaryActionInstance.StopReason reason;

		Session(LegendaryActionContext context, LegendaryActionInstance instance) {
			this.context = context;
			this.instance = instance;
		}

		ServerLevel level() {
			return context.level();
		}

		LegendaryActionId action() {
			return context.action();
		}
	}

	private static final class TestSession extends Session {
		private final UUID playerId;

		TestSession(UUID playerId, LegendaryActionContext context, LegendaryActionInstance instance) {
			super(context, instance);
			this.playerId = playerId;
		}

		UUID playerId() {
			return playerId;
		}
	}

	private static final class BossSession extends Session {
		private final UUID ownerId;

		BossSession(UUID ownerId, LegendaryActionContext context, LegendaryActionInstance instance) {
			super(context, instance);
			this.ownerId = ownerId;
		}

		UUID ownerId() {
			return ownerId;
		}
	}
}
