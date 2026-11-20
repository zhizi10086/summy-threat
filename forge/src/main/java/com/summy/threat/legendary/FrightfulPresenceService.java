package com.summy.threat.legendary;

import com.summy.threat.network.ModNetwork;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 巨龙威仪的持续复查、输出削弱和短时免疫。 */
public final class FrightfulPresenceService {
	private static final int DURATION_TICKS = 120;
	private static final int IMMUNITY_TICKS = 600;
	private static final Map<UUID, FearState> ACTIVE = new HashMap<>();
	private static final Map<UUID, Map<UUID, Long>> IMMUNITY = new HashMap<>();

	private FrightfulPresenceService() {
	}

	public static void apply(LivingEntity owner, List<Player> players, long now, boolean test) {
		for (Player player : players) {
			if (!(player instanceof ServerPlayer serverPlayer)) {
				continue;
			}
			Long immuneUntil = immunityMap(player.getUUID()).get(owner.getUUID());
			if (immuneUntil != null && immuneUntil > now) {
				continue;
			}
			FearState state = ACTIVE.get(player.getUUID());
			if (state != null && !state.ownerId.equals(owner.getUUID())) {
				end(serverPlayer, state, false, now);
				state = null;
			}
			if (state == null) {
				state = new FearState(owner.getUUID(), player.getUUID(),
						now + DURATION_TICKS,
						now, -1L, test);
				ACTIVE.put(player.getUUID(), state);
			} else {
				state.endAt = now + DURATION_TICKS;
			}
			applySlow(serverPlayer, state);
			ModNetwork.sendFrightfulPresence(serverPlayer, DURATION_TICKS, LegendaryVisuals.dragonColor(owner));
			if (serverPlayer.level() instanceof ServerLevel level) {
				level.sendParticles(LegendaryVisuals.dust(
								LegendaryVisuals.dragonColor(owner), 1.2F),
						player.getX(), player.getY() + player.getBbHeight() * 0.8D, player.getZ(),
						12, 0.8D, 0.6D, 0.8D, 0.01D);
			}
		}
	}

	@SubscribeEvent
	public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
		if (event.phase != TickEvent.Phase.END
				|| !(event.player instanceof ServerPlayer player)) {
			return;
		}
		FearState state = ACTIVE.get(player.getUUID());
		if (state == null) {
			return;
		}
		long now = player.level().getGameTime();
		LivingEntity owner = player.level().getEntitiesOfClass(LivingEntity.class,
						player.getBoundingBox().inflate(64.0D),
						entity -> entity.getUUID().equals(state.ownerId))
				.stream().findFirst().orElse(null);
		if (owner == null || !owner.isAlive() || !LegendaryActionRuntime.validPlayer(owner, player)) {
			end(player, state, true, now);
			return;
		}
		if (now >= state.endAt) {
			end(player, state, true, now);
			return;
		}
		if (owner.distanceTo(player) > 20.0D) {
			removeSlow(player, state);
		} else {
			applySlow(player, state);
		}
		if (LegendaryActionCombat.hasLineOfSight(owner, player)) {
			state.lastVisibleAt = now;
		} else if (now - state.lastVisibleAt >= 20L) {
			end(player, state, true, now);
			return;
		}
		if (owner.distanceTo(player) > 28.0D) {
			if (state.lastFarAt < 0L) {
				state.lastFarAt = now;
			} else if (now - state.lastFarAt >= 20L) {
				end(player, state, true, now);
				return;
			}
		} else {
			state.lastFarAt = -1L;
		}
		if (now % 10L == 0L && player.level() instanceof ServerLevel level) {
			level.sendParticles(LegendaryVisuals.dust(
							LegendaryVisuals.dragonColor(owner), 1.0F),
					player.getX(), player.getY() + player.getBbHeight() * 0.85D, player.getZ(),
					6, 0.55D, 0.25D, 0.55D, 0.01D);
		}
	}

	public static float modifyPreAbsorptionDamage(LivingEntity dragon,
			DamageSource source, float amount) {
		if (!(source.getEntity() instanceof ServerPlayer player)) {
			return amount;
		}
		FearState state = ACTIVE.get(player.getUUID());
		if (state != null && state.ownerId.equals(dragon.getUUID())
				&& (state.test || com.summy.threat.scaling.ScalingState.enabled())) {
			return amount * 0.8F;
		}
		return amount;
	}

	@SubscribeEvent(priority = EventPriority.MONITOR)
	public static void onDeath(LivingDeathEvent event) {
		if (!event.isCanceled() && event.getEntity() instanceof ServerPlayer player) {
			clearPlayer(player);
		}
	}

	@SubscribeEvent
	public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
		if (event.getEntity() instanceof ServerPlayer player) {
			clearPlayer(player);
		}
	}

	@SubscribeEvent
	public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
		if (event.getEntity() instanceof ServerPlayer player) {
			clearPlayer(player);
		}
	}

	private static void clearPlayer(ServerPlayer player) {
		FearState state = ACTIVE.get(player.getUUID());
		if (state != null) {
			end(player, state, false, player.level().getGameTime());
		}
	}

	@SubscribeEvent
	public static void onServerStopped(ServerStoppedEvent event) {
		for (UUID playerId : new ArrayList<>(ACTIVE.keySet())) {
			ServerPlayer player = event.getServer().getPlayerList().getPlayer(playerId);
			if (player != null) {
				FearState state = ACTIVE.get(playerId);
				if (state != null) {
					removeSlow(player, state);
				}
			}
		}
		ACTIVE.clear();
		IMMUNITY.clear();
	}

	static void clearFormal(net.minecraft.server.MinecraftServer server) {
		for (var entry : List.copyOf(ACTIVE.entrySet())) {
			if (!entry.getValue().test) {
				clear(server, entry.getKey(), entry.getValue());
			}
		}
	}

	static void clearOwner(UUID ownerId, ServerLevel level) {
		for (var entry : List.copyOf(ACTIVE.entrySet())) {
			if (entry.getValue().ownerId.equals(ownerId)) {
				clear(level.getServer(), entry.getKey(), entry.getValue());
			}
		}
	}

	private static void clear(net.minecraft.server.MinecraftServer server, UUID playerId, FearState state) {
		ServerPlayer player = server.getPlayerList().getPlayer(playerId);
		if (player != null) {
			end(player, state, false, player.level().getGameTime());
		} else {
			ACTIVE.remove(playerId, state);
		}
	}

	private static void end(ServerPlayer player, FearState state, boolean grantImmunity,
			long now) {
		ACTIVE.remove(player.getUUID());
		removeSlow(player, state);
		if (player.connection != null && player.connection.isAcceptingMessages()) {
			ModNetwork.sendFrightfulPresence(player, 0, 0);
		}
		if (grantImmunity) {
			immunityMap(player.getUUID()).put(state.ownerId, now + IMMUNITY_TICKS);
		}
	}

	private static void applySlow(ServerPlayer player, FearState state) {
		AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed == null || speed.getModifier(state.modifierId) != null) {
			return;
		}
		speed.addTransientModifier(new AttributeModifier(state.modifierId,
				"summy_threat:frightful_presence", -0.15D,
				AttributeModifier.Operation.MULTIPLY_TOTAL));
	}

	private static void removeSlow(ServerPlayer player, FearState state) {
		AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed != null) {
			speed.removeModifier(state.modifierId);
		}
	}

	private static Map<UUID, Long> immunityMap(UUID playerId) {
		return IMMUNITY.computeIfAbsent(playerId, ignored -> new HashMap<>());
	}

	private static UUID modifierId(UUID playerId, UUID ownerId) {
		return UUID.nameUUIDFromBytes(("summy_threat:frightful_presence:"
				+ playerId + ":" + ownerId).getBytes(StandardCharsets.UTF_8));
	}

	private static final class FearState {
		private final UUID ownerId;
		private final UUID modifierId;
		private final boolean test;
		private long endAt;
		private long lastVisibleAt;
		private long lastFarAt;

		private FearState(UUID ownerId, UUID playerId, long endAt, long lastVisibleAt,
				long lastFarAt, boolean test) {
			this.ownerId = ownerId;
			this.test = test;
			this.modifierId = modifierId(playerId, ownerId);
			this.endAt = endAt;
			this.lastVisibleAt = lastVisibleAt;
			this.lastFarAt = lastFarAt;
		}
	}
}
