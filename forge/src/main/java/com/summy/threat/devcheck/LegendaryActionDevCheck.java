package com.summy.threat.devcheck;

import com.summy.threat.legendary.JadeLightBeamEntity;
import com.summy.threat.legendary.LegendaryActionId;
import com.summy.threat.legendary.LegendaryActionRuntime;
import com.summy.threat.legendary.LegendaryActionState;
import com.summy.threat.legendary.LegendaryActionStateData;
import com.summy.threat.scaling.MonsterScaler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.CameraType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.GameType;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

/** 逐项启动 12 个传奇动作并核对关键可观察结果。 */
final class LegendaryActionDevCheck {
	private static final boolean AUTO_ONLY =
			Boolean.getBoolean("summy_threat.legendaryautoonly");
	private static final List<LegendaryActionId> ACTIONS = List.of(
			LegendaryActionId.JADE_LIGHT,
			LegendaryActionId.MIRROR_STEP,
			LegendaryActionId.COLLAPSING_HORIZON,
			LegendaryActionId.LAIR_BREACH,
			LegendaryActionId.DOOM_CLOCK,
			LegendaryActionId.REVERSE_GRAVITY,
			LegendaryActionId.LEGENDARY_RIPOSTE,
			LegendaryActionId.FRIGHTFUL_PRESENCE,
			LegendaryActionId.DRAGON_LAIR_PULSE,
			LegendaryActionId.DRAGONFALL_EDICT,
			LegendaryActionId.DECAY_TIDE,
			LegendaryActionId.ECHO_SENTENCE);
	private static final int START_DELAY = 20;
	private static final int ACTION_INTERVAL = 220;
	private static final int AUTO_START = START_DELAY + ACTION_INTERVAL * ACTIONS.size();
	private static final double TEST_HEALTH = 10_000.0D;

	private static boolean initialized;
	private static boolean finished;
	private static int currentAction = -1;
	private static double baseX;
	private static double baseY;
	private static double baseZ;
	private static float healthBefore;
	private static GameType previousGameMode;
	private static CameraType previousCameraType;
	private static double previousMaxHealth;
	private static Zombie automaticBoss;
	private static long automaticBossReadyAt;

	private LegendaryActionDevCheck() {
	}

	static void tick(ServerPlayer player, int elapsed) {
		if (finished) {
			return;
		}
		if (elapsed < 0) {
			return;
		}
		if (!initialized) {
			initialize(player);
		}
		if (elapsed < START_DELAY) {
			return;
		}
		if (AUTO_ONLY) {
			checkAutomaticTrigger(player, elapsed - START_DELAY);
			return;
		}
		if (elapsed >= AUTO_START) {
			checkAutomaticTrigger(player, elapsed - AUTO_START);
			return;
		}
		int actionIndex = (elapsed - START_DELAY) / ACTION_INTERVAL;
		int phase = (elapsed - START_DELAY) % ACTION_INTERVAL;
		if (actionIndex != currentAction) {
			startAction(player, actionIndex);
		}
		scenarioMove(player, ACTIONS.get(actionIndex), phase);
		checkAtPhase(player, ACTIONS.get(actionIndex), phase);
	}

	private static void initialize(ServerPlayer player) {
		initialized = true;
		previousGameMode = player.gameMode.getGameModeForPlayer();
		player.setGameMode(GameType.SURVIVAL);
		AttributeInstance maxHealth = player.getAttribute(Attributes.MAX_HEALTH);
		previousMaxHealth = maxHealth == null ? player.getMaxHealth() : maxHealth.getBaseValue();
		if (maxHealth != null) {
			maxHealth.setBaseValue(TEST_HEALTH);
		}
		player.setHealth(player.getMaxHealth());
		Minecraft client = Minecraft.getInstance();
		client.execute(() -> {
			previousCameraType = client.options.getCameraType();
			client.options.hideGui = true;
			client.options.setCameraType(CameraType.THIRD_PERSON_BACK);
		});
		ThreatDevCheck.logDetailed("传奇动作专项：12 项顺序测试开始");
	}

	private static void startAction(ServerPlayer player, int actionIndex) {
		currentAction = actionIndex;
		ServerLevel level = player.serverLevel();
		baseX = 300.5D;
		baseY = 100.0D;
		baseZ = 0.5D;
		player.teleportTo(level, baseX, baseY, baseZ, 0.0F, 0.0F);
		player.setXRot(28.0F);
		player.setHealth(player.getMaxHealth());
		healthBefore = player.getHealth();
		LegendaryActionId action = ACTIONS.get(actionIndex);
		LegendaryActionRuntime.TestStartResult result =
				LegendaryActionRuntime.startTest(player, player, action);
		ThreatDevCheck.checkDetailed("传奇动作启动 " + action.id()
						+ "（" + (actionIndex + 1) + "/" + ACTIONS.size() + "）",
				result == LegendaryActionRuntime.TestStartResult.STARTED, "result=" + result);
		ThreatDevCheck.logDetailed("LEGENDARY_START action=" + action.id()
				+ " index=" + actionIndex);
	}

	private static void scenarioMove(ServerPlayer player, LegendaryActionId action, int phase) {
		ServerLevel level = player.serverLevel();
		if (action == LegendaryActionId.COLLAPSING_HORIZON && phase == 40) {
			player.teleportTo(level, baseX + 12.0D, baseY, baseZ, 0.0F, 0.0F);
		} else if (action == LegendaryActionId.LAIR_BREACH && phase == 30) {
			player.teleportTo(level, baseX + 6.0D, baseY, baseZ, 0.0F, 0.0F);
		} else if (action == LegendaryActionId.DOOM_CLOCK && phase == 20) {
			player.teleportTo(level, baseX + 10.0D, baseY, baseZ, 0.0F, 0.0F);
		} else if (action == LegendaryActionId.DRAGON_LAIR_PULSE && phase == 45) {
			player.teleportTo(level, baseX, baseY, baseZ + 6.0D, 0.0F, 0.0F);
		} else if (action == LegendaryActionId.DRAGONFALL_EDICT && phase == 20) {
			player.teleportTo(level, baseX, baseY + 11.0D, baseZ, 0.0F, 0.0F);
			player.setNoGravity(true);
		} else if (action == LegendaryActionId.DECAY_TIDE && phase == 40) {
			player.teleportTo(level, baseX + 6.0D, baseY, baseZ, 0.0F, 0.0F);
		} else if (action == LegendaryActionId.ECHO_SENTENCE && phase == 60) {
			player.hurt(player.damageSources().playerAttack(player), 1.0F);
		}
	}

	private static void checkAtPhase(ServerPlayer player, LegendaryActionId action, int phase) {
		ServerLevel level = player.serverLevel();
		if (phase == capturePhase(action)) {
			capture(action);
		}
		if (action == LegendaryActionId.JADE_LIGHT && phase == 60) {
			ThreatDevCheck.checkDetailed("翠玉之光产生接触光柱",
					!level.getEntitiesOfClass(JadeLightBeamEntity.class,
							player.getBoundingBox().inflate(32.0D)).isEmpty(), "no beam");
			checkHealthLoss(player, "翠玉之光接触并命中玩家");
		} else if (action == LegendaryActionId.MIRROR_STEP && phase == 50) {
			ThreatDevCheck.checkDetailed("镜界折跃移动施法者",
					player.position().distanceToSqr(baseX, baseY, baseZ) > 1.0D,
					"pos=" + player.position());
		} else if (action == LegendaryActionId.COLLAPSING_HORIZON && phase == 115) {
			checkHealthLoss(player, "天穹收束命中安全圈外目标");
		} else if (action == LegendaryActionId.LAIR_BREACH && phase == 110) {
			checkHealthLoss(player, "巢穴裂变按顺序命中裂口");
		} else if (action == LegendaryActionId.DOOM_CLOCK && phase == 115) {
			checkHealthLoss(player, "终末轮盘首扇区按目标方向引爆");
		} else if (action == LegendaryActionId.REVERSE_GRAVITY && phase == 65) {
			ThreatDevCheck.checkDetailed("逆重力场产生净上升",
					player.getY() > baseY + 0.5D, "y=" + player.getY());
		} else if (action == LegendaryActionId.LEGENDARY_RIPOSTE && phase == 25) {
			checkHealthLoss(player, "传奇反击完成固定中心冲击");
		} else if (action == LegendaryActionId.FRIGHTFUL_PRESENCE && phase == 55) {
			ThreatDevCheck.checkDetailed("巨龙威仪降低移动速度",
					player.getAttributeValue(Attributes.MOVEMENT_SPEED) < 0.1D,
					"speed=" + player.getAttributeValue(Attributes.MOVEMENT_SPEED));
		} else if (action == LegendaryActionId.DRAGON_LAIR_PULSE && phase == 115) {
			checkHealthLoss(player, "龙巢脉冲缺口外波次命中");
		} else if (action == LegendaryActionId.DRAGONFALL_EDICT && phase == 75) {
			checkHealthLoss(player, "龙威坠界压制高空目标");
		} else if (action == LegendaryActionId.DECAY_TIDE && phase == 75) {
			checkHealthLoss(player, "凋亡潮汐波次命中低空目标");
		} else if (action == LegendaryActionId.ECHO_SENTENCE && phase == 90) {
			checkHealthLoss(player, "回响判罪生成并结算延迟声波");
		}
		if (phase == ACTION_INTERVAL - 1) {
			if (action == LegendaryActionId.DRAGONFALL_EDICT) {
				player.setNoGravity(false);
			}
			if (action == LegendaryActionId.JADE_LIGHT) {
				ThreatDevCheck.checkDetailed("翠玉之光结束后清理专用实体",
						level.getEntitiesOfClass(JadeLightBeamEntity.class,
								player.getBoundingBox().inflate(64.0D)).isEmpty(),
						"beam remains");
			}
			ThreatDevCheck.logDetailed("LEGENDARY_DONE action=" + action.id());
		}
	}

	private static int capturePhase(LegendaryActionId action) {
		return switch (action) {
			case JADE_LIGHT -> 50;
			case MIRROR_STEP -> 41;
			case COLLAPSING_HORIZON -> 60;
			case LAIR_BREACH -> 48;
			case DOOM_CLOCK -> 50;
			case REVERSE_GRAVITY -> 55;
			case LEGENDARY_RIPOSTE -> 21;
			case FRIGHTFUL_PRESENCE -> 37;
			case DRAGON_LAIR_PULSE -> 55;
			case DRAGONFALL_EDICT -> 55;
			case DECAY_TIDE -> 52;
			case ECHO_SENTENCE -> 68;
		};
	}

	private static void checkAutomaticTrigger(ServerPlayer player, int phase) {
		ServerLevel level = player.serverLevel();
		if (automaticBoss == null) {
			baseX = player.getX();
			baseY = player.getY();
			baseZ = player.getZ();
			automaticBoss = EntityType.ZOMBIE.create(level);
			if (automaticBoss == null) {
				ThreatDevCheck.checkDetailed("传奇动作自动触发：创建测试 Boss",
						false, "create 返回 null");
				finish(player);
				automaticBoss = null;
				return;
			}
			automaticBoss.moveTo(baseX + 8.0D, baseY, baseZ);
			automaticBoss.setNoAi(true);
			automaticBoss.setPersistenceRequired();
			automaticBoss.setTarget(player);
			LegendaryActionStateData.setSelection(
					automaticBoss.getPersistentData(), LegendaryActionId.JADE_LIGHT);
			MonsterScaler.scaleNow(automaticBoss, 20, MonsterScaler.Kind.BOSS, false);
			level.addFreshEntity(automaticBoss);
			ThreatDevCheck.logDetailed("LEGENDARY_AUTO_START");
		}
		player.teleportTo(level, baseX, baseY, baseZ, player.getYRot(), player.getXRot());
		if (phase == 150) {
			logAutomaticBossState(player, phase);
			ThreatDevCheck.checkDetailed("传奇动作前 200 tick 不触发",
					LegendaryActionState.ready(automaticBoss,
							level.getGameTime()), "ready=false");
		} else if (phase == 210) {
			logAutomaticBossState(player, phase);
			automaticBossReadyAt = automaticBoss.getPersistentData()
					.getLong(LegendaryActionStateData.TAG_NEXT_READY);
			long now = level.getGameTime();
			ThreatDevCheck.checkDetailed("传奇动作连续战斗 200 tick 后自动触发",
					!LegendaryActionState.ready(automaticBoss, now),
					"ready=true nextReady=" + automaticBossReadyAt + " now=" + now);
			ThreatDevCheck.checkDetailed("传奇动作自动触发进入 600 tick 冷却",
					automaticBossReadyAt >= now + 500L,
					"nextReady=" + automaticBossReadyAt + " now=" + now);
			automaticBoss.discard();
			automaticBoss = null;
			ThreatDevCheck.logDetailed("LEGENDARY_AUTO_DONE");
			finish(player);
		}
	}

	private static void logAutomaticBossState(ServerPlayer player, int phase) {
		ServerLevel level = player.serverLevel();
		ThreatDevCheck.logDetailed("LEGENDARY_AUTO_DIAG phase=" + phase
				+ " kind=" + MonsterScaler.kindOf(automaticBoss)
				+ " alive=" + automaticBoss.isAlive()
				+ " removed=" + automaticBoss.isRemoved()
				+ " target=" + automaticBoss.getTarget()
				+ " selection=" + LegendaryActionState.selection(automaticBoss)
				+ " combatTicks=" + automaticCombatTicks(automaticBoss.getUUID())
				+ " running=" + automaticRunningActions(automaticBoss.getUUID())
				+ " players=" + level.getPlayers(value -> value.isAlive()
						&& !value.isCreative() && !value.isSpectator()).size()
				+ " distance=" + automaticBoss.distanceTo(player));
	}

	@SuppressWarnings("unchecked")
	private static int automaticCombatTicks(java.util.UUID ownerId) {
		try {
			Field field = LegendaryActionRuntime.class.getDeclaredField("COMBAT_TICKS");
			field.setAccessible(true);
			Map<java.util.UUID, Integer> ticks =
					(Map<java.util.UUID, Integer>) field.get(null);
			return ticks.getOrDefault(ownerId, 0);
		} catch (ReflectiveOperationException exception) {
			return -1;
		}
	}

	@SuppressWarnings("unchecked")
	private static int automaticRunningActions(java.util.UUID ownerId) {
		try {
			Field field = LegendaryActionRuntime.class.getDeclaredField("BOSS_ACTIONS");
			field.setAccessible(true);
			Map<java.util.UUID, ?> actions = (Map<java.util.UUID, ?>) field.get(null);
			return actions.containsKey(ownerId) ? 1 : 0;
		} catch (ReflectiveOperationException exception) {
			return -1;
		}
	}

	private static void checkHealthLoss(ServerPlayer player, String label) {
		ThreatDevCheck.checkDetailed(label, player.getHealth() < healthBefore,
				"before=" + healthBefore + " now=" + player.getHealth());
	}

	private static void capture(LegendaryActionId action) {
		Minecraft client = Minecraft.getInstance();
		client.execute(() -> Screenshot.grab(client.gameDirectory,
				"st_legendary_" + action.id(), client.getMainRenderTarget(),
				message -> ThreatDevCheck.logDetailed(
						"传奇动作截图 " + action.id() + "：" + message)));
	}

	private static void finish(ServerPlayer player) {
		if (finished) {
			return;
		}
		finished = true;
		player.setNoGravity(false);
		player.teleportTo(player.serverLevel(), baseX, baseY, baseZ, 0.0F, 0.0F);
		AttributeInstance maxHealth = player.getAttribute(Attributes.MAX_HEALTH);
		if (maxHealth != null) {
			maxHealth.setBaseValue(previousMaxHealth);
		}
		if (player.getHealth() > player.getMaxHealth()) {
			player.setHealth(player.getMaxHealth());
		}
		GameType restore = previousGameMode == null ? GameType.SURVIVAL : previousGameMode;
		player.setGameMode(restore);
		Minecraft client = Minecraft.getInstance();
		client.execute(() -> {
			client.options.hideGui = false;
			client.options.setCameraType(previousCameraType == null
					? CameraType.FIRST_PERSON : previousCameraType);
		});
		ThreatDevCheck.logDetailed("传奇动作专项：12 项顺序测试结束");
	}
}
