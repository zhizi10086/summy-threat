package com.summy.threat.devcheck;

import com.summy.threat.SummyThreat;
import com.summy.threat.scaling.MonsterScaler;
import com.summy.threat.eximus.EximusShield;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Dev-only：其余 7 个原型的核心行为。
 *
 * <p>只通过 {@code -Parchetypecheck} 启用；覆盖烈焰/剧毒/震荡伤害，冰霜 25% 盾，
 * 守护 10%/54% 转移，吸血/磁力云团伤害。追踪/锁定与粒子观感由人工确认。
 */
@Mod.EventBusSubscriber(modid = SummyThreat.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE,
		value = Dist.CLIENT)
public final class ArchetypeRuleDevCheck {
	private static final boolean ENABLED = Boolean.getBoolean("summy_threat.archetypecheck");
	private static final List<String> FAILURES = new ArrayList<>();
	private static int tick;
	private static int checks;
	private static boolean finished;
	private static ServerPlayer player;
	private static Zombie owner;
	private static Zombie ally;
	private static long flameCooldownSpan = -1L;
	private static long shockCooldownSpan = -1L;
	private static long cloudCooldownSpan = -1L;

	private ArchetypeRuleDevCheck() {
	}

	@SubscribeEvent
	public static void onServerTick(TickEvent.ServerTickEvent event) {
		if (!ENABLED || finished || event.phase != TickEvent.Phase.END
				|| event.getServer().getPlayerList().getPlayers().isEmpty()) {
			return;
		}
		player = event.getServer().getPlayerList().getPlayers().get(0);
		tick++;
		try {
			observeCooldowns();
			switch (tick) {
				case 20 -> prepare();
				case 40 -> spawnFlame();
				case 100 -> checkFlame();
				case 120 -> spawnPoison();
				case 200 -> checkPoison();
				case 220 -> spawnShock();
				case 340 -> checkShock();
				case 360 -> spawnFrost();
				case 440 -> checkFrost();
				case 460 -> spawnGuardian();
				case 520 -> checkGuardian();
				case 540 -> spawnVampire();
				case 680 -> checkVampire();
				case 700 -> spawnMagnet();
				case 780 -> checkMagnet();
				case 800 -> finish("完成");
				default -> {
				}
			}
		} catch (Throwable throwable) {
			FAILURES.add("步骤 " + tick + " 异常：" + throwable);
			log("步骤 " + tick + " 异常：" + throwable);
			throwable.printStackTrace();
			finish("异常停止");
		}
	}

	private static void prepare() {
		ServerLevel level = player.serverLevel();
		for (int x = 295; x <= 305; x++) {
			for (int z = -5; z <= 5; z++) {
				level.setBlockAndUpdate(new BlockPos(x, 99, z), Blocks.STONE.defaultBlockState());
			}
		}
		player.setGameMode(GameType.SURVIVAL);
		clearPlayer(player);
		player.teleportTo(level, 300.0D, 100.0D, 0.0D, 0.0F, 0.0F);
		player.removeAllEffects();
		player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0D);
		player.setHealth(1000.0F);
		run("gamerule naturalRegeneration false");
		run("gamerule doMobSpawning false");
		run("kill @e[type=!minecraft:player]");
		run("summythreat tier full");
		run("summythreat tl off");
		log("世界准备完成");
	}

	private static void spawnFlame() {
		beginCase();
		flameCooldownSpan = -1L;
		owner = spawnEximus("flame", 306.0D);
		owner.setTarget(player);
		log("烈焰已生成");
	}

	private static void checkFlame() {
		checkPlayerDamaged("烈焰火圈命中玩家", 0.1F);
		checkCooldown("烈焰冷却 180 tick", flameCooldownSpan, 180L);
		endCase();
	}

	private static void spawnPoison() {
		beginCase();
		owner = spawnEximus("poison", 302.0D);
		owner.setTarget(player);
		log("剧毒已生成");
	}

	private static void checkPoison() {
		checkPlayerDamaged("剧毒光环命中玩家", 0.1F);
		endCase();
	}

	private static void spawnShock() {
		beginCase();
		shockCooldownSpan = -1L;
		owner = spawnEximus("shock", 305.0D);
		owner.setTarget(player);
		log("震荡已生成");
	}

	private static void checkShock() {
		checkPlayerDamaged("震荡冲击柱命中玩家", 0.1F);
		checkCooldown("震荡冷却 160 tick", shockCooldownSpan, 160L);
		endCase();
	}

	private static void spawnFrost() {
		beginCase();
		owner = spawnEximus("frost", 302.0D);
		ally = spawnAlly(301.0D);
		log("冰霜与友军已生成");
	}

	private static void checkFrost() {
		double shield = ally.getPersistentData().getDouble("summy_threat:frost_shield");
		check("冰霜：友军获得 25% 最大生命护盾", shield >= 200.0D,
				"shield=%.1f max=%.1f".formatted(shield, ally.getMaxHealth()));
		float healthBefore = ally.getHealth();
		double shieldBefore = ally.getPersistentData().getDouble("summy_threat:frost_shield");
		// 不用玩家来源，避免 FULL 环境把 SR/SE 的玩家增伤乘进固定测试金额。
		ally.hurt(owner.damageSources().mobAttack(owner), 100.0F);
		double shieldAfter = ally.getPersistentData().getDouble("summy_threat:frost_shield");
		check("冰霜：100 物理伤害先被 50% 减伤，再由冰盾吸收",
				Math.abs(ally.getHealth() - healthBefore) <= 5.1F
						&& Math.abs(shieldAfter - (shieldBefore - 50.0D)) < 1.0D,
				"health %.1f→%.1f shield %.1f→%.1f".formatted(
						healthBefore, ally.getHealth(), shieldBefore, shieldAfter));
		check("冰霜：已有冰盾不会刷新", shieldAfter < shieldBefore,
				"shield %.1f→%.1f".formatted(shieldBefore, shieldAfter));
		AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
		boolean slowed = speed != null && speed.getModifiers().stream()
				.anyMatch(modifier -> "summy_threat:frost_slow_speed".equals(modifier.getName()));
		check("冰霜：3 格减速光环命中玩家", slowed, "movement modifiers=" + (speed == null ? "-" : speed.getModifiers()));
		endCase();
	}

	private static void spawnGuardian() {
		beginCase();
		owner = spawnEximus("guardian", 304.0D);
		ally = spawnAlly(301.0D);
		log("守护与友军已生成");
	}

	private static void checkGuardian() {
		float allyBefore = ally.getHealth();
		double guardianShieldBefore = EximusShield.current(owner);
		ally.hurt(ally.damageSources().generic(), 100.0F);
		float allyLoss = allyBefore - ally.getHealth();
		double guardianShieldLoss = guardianShieldBefore - EximusShield.current(owner);
		check("守护：友军实际承伤约 10", Math.abs(allyLoss - 10.0F) < 2.0F,
				"ally loss=%.1f".formatted(allyLoss));
		// FULL 环境有约 10% 的外部通用减伤：54 × 0.9 = 48.6。
		check("守护：守护者实际承伤约 54（FULL 环境含外部减伤）",
				Math.abs(guardianShieldLoss - 54.0D) < 7.0D,
				"guardian shield loss=%.1f（设计 54）".formatted(guardianShieldLoss));
		endCase();
	}

	private static void spawnVampire() {
		beginCase();
		cloudCooldownSpan = -1L;
		owner = spawnEximus("vampire", 304.0D);
		owner.setHealth(1000.0F);
		owner.setTarget(player);
		log("吸血已生成");
	}

	private static void checkVampire() {
		checkPlayerDamaged("吸血云团命中玩家", 0.1F);
		check("吸血：主人回复生命", owner.getHealth() > 1000.0F,
				"owner health=%.1f".formatted(owner.getHealth()));
		Object clouds = mapValue("CLOUDS", owner.getUUID());
		List<?> list = clouds instanceof List<?> raw ? raw : List.of();
		boolean windowOk = !list.isEmpty();
		for (Object cloud : list) {
			windowOk &= ((long) field(cloud, "end") - (long) field(cloud, "armUntil")) == 200L;
		}
		check("云团：预警 1.5 秒后生效 10 秒（200 tick）", windowOk,
				"clouds=" + list.size());
		checkCooldown("云团生成间隔 160 tick", cloudCooldownSpan, 160L);
		screenshot("st_archetype_vampire_cloud");
		endCase();
	}

	private static void spawnMagnet() {
		beginCase();
		owner = spawnEximus("magnet", 304.0D);
		owner.setTarget(player);
		log("磁力已生成");
	}

	private static void checkMagnet() {
		checkPlayerDamaged("磁力云团命中玩家", 0.1F);
		screenshot("st_archetype_magnet_cloud");
		endCase();
	}

	private static void beginCase() {
		run("kill @e[type=!minecraft:player]");
		player.teleportTo(player.serverLevel(), 300.0D, 100.0D, 0.0D, 0.0F, 0.0F);
		player.setHealth(1000.0F);
		player.removeAllEffects();
		run("summythreat tl off");
	}

	private static void endCase() {
		run("kill @e[type=!minecraft:player]");
	}

	private static Zombie spawnEximus(String archetype, double x) {
		ServerLevel level = player.serverLevel();
		Zombie eximus = EntityType.ZOMBIE.create(level);
		if (eximus == null) {
			throw new IllegalStateException("无法生成 " + archetype);
		}
		eximus.moveTo(x, 100.0D, 0.0D, 0.0F, 0.0F);
		eximus.getPersistentData().putBoolean(MonsterScaler.TAG_EXIMUS, true);
		eximus.getPersistentData().putString(MonsterScaler.TAG_ARCHETYPE, archetype);
		level.addFreshEntity(eximus);
		MonsterScaler.scaleNow(eximus, 20, MonsterScaler.Kind.EXIMUS);
		eximus.setNoAi(true);
		eximus.setPersistenceRequired();
		eximus.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.0D);
		eximus.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(0.0D);
		return eximus;
	}

	private static Zombie spawnAlly(double x) {
		ServerLevel level = player.serverLevel();
		Zombie zombie = EntityType.ZOMBIE.create(level);
		if (zombie == null) {
			throw new IllegalStateException("无法生成友军");
		}
		zombie.moveTo(x, 100.0D, 0.0D, 0.0F, 0.0F);
		zombie.setNoAi(true);
		zombie.setPersistenceRequired();
		zombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0D);
		zombie.getAttribute(Attributes.ARMOR).setBaseValue(0.0D);
		zombie.setHealth(1000.0F);
		level.addFreshEntity(zombie);
		return zombie;
	}

	private static void checkPlayerDamaged(String label, float minDamage) {
		float damage = 1000.0F - player.getHealth();
		check(label, damage > minDamage, "player loss=%.1f".formatted(damage));
	}

	private static void check(String label, boolean ok, String detail) {
		checks++;
		if (!ok) {
			FAILURES.add(label);
		}
		log((ok ? "OK  " : "FAIL") + " " + label + "｜" + detail);
	}

	private static void checkCooldown(String label, long actual, long expected) {
		check(label, actual == expected, "actual=" + actual + " expected=" + expected);
	}

	/** 在状态首次出现时记录“开始 → ready”的真实跨度，避免固定 tick 推断受服务器相位影响。 */
	private static void observeCooldowns() {
		if (owner == null) {
			return;
		}
		if (flameCooldownSpan < 0L) {
			Object cast = mapValue("FLAME", owner.getUUID());
			Object ready = mapValue("FLAME_READY", owner.getUUID());
			if (cast != null && ready instanceof Long readyAt) {
				flameCooldownSpan = readyAt - (long) field(cast, "start");
			}
		}
		if (shockCooldownSpan < 0L) {
			Object cast = mapValue("SHOCK", owner.getUUID());
			Object ready = mapValue("SHOCK_READY", owner.getUUID());
			if (cast != null && ready instanceof Long readyAt) {
				shockCooldownSpan = readyAt - (long) field(cast, "start");
			}
		}
		if (cloudCooldownSpan < 0L) {
			Object rawClouds = mapValue("CLOUDS", owner.getUUID());
			Object ready = mapValue("NEXT_CLOUD", owner.getUUID());
			if (rawClouds instanceof List<?> clouds && !clouds.isEmpty() && ready instanceof Long readyAt) {
				long armUntil = (long) field(clouds.get(0), "armUntil");
				long spawnedAt = armUntil - 30L;
				cloudCooldownSpan = readyAt - spawnedAt;
			}
		}
	}

	private static Object mapValue(String fieldName, Object key) {
		try {
			Field field = com.summy.threat.eximus.EximusArchetypeEffects.class.getDeclaredField(fieldName);
			field.setAccessible(true);
			Object raw = field.get(null);
			return raw instanceof Map<?, ?> map ? map.get(key) : null;
		} catch (ReflectiveOperationException exception) {
			throw new IllegalStateException("读取 " + fieldName + " 失败", exception);
		}
	}

	private static Object field(Object target, String name) {
		try {
			Field field = target.getClass().getDeclaredField(name);
			field.setAccessible(true);
			return field.get(target);
		} catch (ReflectiveOperationException exception) {
			throw new IllegalStateException("读取字段 " + name + " 失败", exception);
		}
	}

	private static void clearPlayer(ServerPlayer player) {
		player.getInventory().clearContent();
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			player.setItemSlot(slot, ItemStack.EMPTY);
		}
		CuriosApi.getCuriosInventory(player).ifPresent(handler -> {
			for (ICurioStacksHandler stacks : handler.getCurios().values()) {
				clear(stacks.getStacks());
				clear(stacks.getCosmeticStacks());
			}
		});
	}

	private static void clear(IDynamicStackHandler handler) {
		for (int i = 0; i < handler.getSlots(); i++) {
			handler.setStackInSlot(i, ItemStack.EMPTY);
		}
	}

	private static void run(String command) {
		player.server.getCommands().performPrefixedCommand(player.createCommandSourceStack(), command);
	}

	private static void finish(String reason) {
		if (finished) {
			return;
		}
		finished = true;
		log("===== " + reason + "：检查 " + checks + " 项，失败 " + FAILURES.size() + " 项 =====");
		for (String failure : FAILURES) {
			log("FAILED｜" + failure);
		}
		Minecraft.getInstance().execute(() -> Minecraft.getInstance().stop());
	}

	private static void log(String message) {
		SummyThreat.LOGGER.info("[ARCHCHECK] " + message);
	}

	private static void screenshot(String name) {
		Minecraft.getInstance().execute(() -> Screenshot.grab(
				Minecraft.getInstance().gameDirectory, name,
				Minecraft.getInstance().getMainRenderTarget(),
				message -> log("截图 " + name + "：" + message)));
	}
}
