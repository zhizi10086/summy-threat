package com.summy.threat.devcheck;

import com.summy.threat.SummyThreat;
import com.summy.threat.ability.AbilityEffects;
import com.summy.threat.ability.AbilityId;
import com.summy.threat.armor.EchoArmorEffects;
import com.summy.threat.armor.PlayerEximusShield;
import com.summy.threat.eximus.EximusArchetype;
import com.summy.threat.eximus.EximusShield;
import com.summy.threat.eximus.EximusSpawner;
import com.summy.threat.item.ModItems;
import com.summy.threat.pcf.CdfManager;
import com.summy.threat.pcf.PcfManager;
import com.summy.threat.pcf.SpawnRegion;
import com.summy.threat.scaling.MonsterScaler;
import com.summy.threat.scaling.SpawnAudit;
import com.summy.threat.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.RandomSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Summy Threat 的真实客户端自检。
 *
 * <p>只通过 {@code gradlew runClient -Pdevcheck --args="--quickPlaySingleplayer DevCheckWorld"}
 * 启用；正式 jar 会排除本包。这里做“代码/数值关系 + 简单视觉”的可判定断言：
 * PCF 装备分、TL 选择、回响套被动、玩家护盾 HUD 截图、Jade 目标截图。
 * 复杂技能视觉（8 原型粒子、前摇、云团颜色）仍由 visual_checklist.md 人工确认。
 */
@Mod.EventBusSubscriber(modid = SummyThreat.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ThreatDevCheck {
	private static final boolean ENABLED = Boolean.getBoolean("summy_threat.devcheck");
	// 主菜单阶段要留给玩家创建/选择 DevCheckWorld 的时间；30 秒会在世界创建前
	// 就把客户端关掉，因此这里放宽到 10 分钟，总运行上限放宽到 20 分钟。
	private static final int IDLE_STOP_TICKS = 12000;
	private static final int TOTAL_STOP_TICKS = 24000;
	private static final int CLIENT_STOP_TICK = 1520;
	private static final List<String> FAILURES = new ArrayList<>();
	private static int serverTick;
	private static int clientTick;
	private static int idleTicks;
	private static int totalClientTicks;
	private static boolean finished;
	private static double baseSpeed;
	private static int checks;
	private static Zombie chestEximus;
	private static final List<String> MAPPING = new ArrayList<>();

	private ThreatDevCheck() {
	}

	@SubscribeEvent
	public static void onServerTick(TickEvent.ServerTickEvent event) {
		if (!ENABLED || event.phase != TickEvent.Phase.END) {
			return;
		}
		if (event.getServer().getPlayerList().getPlayers().isEmpty()) {
			serverTick = 0;
			return;
		}
		ServerPlayer player = event.getServer().getPlayerList().getPlayers().get(0);
		serverTick++;
		try {
			switch (serverTick) {
				case 20 -> prepareWorld(player);
				case 40 -> prepareEquipment(player);
				case 120 -> checkPcfEquipment(player);
				case 140 -> checkNightVision(player);
				case 160 -> checkBootsWithoutShield(player);
				case 180 -> checkBootsWithShield(player);
				case 200 -> checkLeggings(player);
				// 破盾当击的溢出伤害会被清零并进入 0.5 秒真无敌；
				// 必须分两次：220 破盾，250（30 tick 后）再击杀。
				case 220 -> prepareChestKill(player);
				case 250 -> finishChestKill(player);
				case 260 -> checkAutoTl(player);
				case 270 -> checkPcfCdfMapping(player);
				case 280 -> checkRandomness(player);
				case 285 -> checkRegionMultipliers(player);
				case 290 -> ReliquaryCompatDevCheck.run(player);
				case 350 -> prepareNaturalSpawn(player, 1.0D, "100%");
				case 750 -> checkNatural100(player);
				case 770 -> prepareNaturalSpawn(player, 0.05D, "5%");
				case 1170 -> checkNatural5(player);
				case 1190 -> prepareSpawner(player);
				case 1290 -> checkSpawner(player);
				case 1310 -> checkManualTl(player);
				case 1330 -> prepareJadeTarget(player);
				case 1350 -> prepareHud(player);
				case 1450 -> cleanup(player);
				default -> {
				}
			}
		} catch (Throwable throwable) {
			FAILURES.add("步骤 " + serverTick + " 异常：" + throwable);
			log("步骤 " + serverTick + " 异常：" + throwable);
			throwable.printStackTrace();
		}
	}

	@SubscribeEvent
	public static void onClientTick(TickEvent.ClientTickEvent event) {
		if (!ENABLED || event.phase != TickEvent.Phase.END) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		totalClientTicks++;
		if (totalClientTicks > TOTAL_STOP_TICKS) {
			finish(client, "硬停止：客户端总 tick 超限");
			return;
		}
		if (client.player == null || client.level == null) {
			idleTicks++;
			if (idleTicks == IDLE_STOP_TICKS) {
				log("硬停止：连续 " + IDLE_STOP_TICKS + " tick 没有玩家/关卡；请确认 DevCheckWorld 已创建");
			}
			if (idleTicks > IDLE_STOP_TICKS) {
				finish(client, "硬停止：等待世界超时");
			}
			return;
		}
		idleTicks = 0;
		clientTick++;
		if (clientTick == 1370) {
			screenshot(client, "st_devcheck_jade");
		} else if (clientTick == 1390) {
			screenshot(client, "st_devcheck_hud");
		} else if (clientTick == 1430) {
			screenshot(client, "st_devcheck_scene");
		} else if (clientTick >= CLIENT_STOP_TICK) {
			finish(client, "客户端自检结束");
		}
	}

	private static void prepareWorld(ServerPlayer player) {
		ServerLevel level = player.serverLevel();
		// 自然刷怪需要 24 格外的黑暗平台；51x51 足够让玩家站在 y=130 时
		// 下方平台（y=99）持续刷出怪物。
		for (int x = -25; x <= 25; x++) {
			for (int z = -25; z <= 25; z++) {
				level.setBlockAndUpdate(new BlockPos(300 + x, 99, z), Blocks.STONE.defaultBlockState());
			}
		}
		player.teleportTo(level, 300.0D, 100.0D, 0.0D, player.getYRot(), player.getXRot());
		run(player, "gamerule doMobSpawning false");
		run(player, "gamerule keepInventory true");
		run(player, "gamerule doDaylightCycle false");
		run(player, "difficulty normal");
		run(player, "summythreat tier full");
		run(player, "summythreat tl auto");
		log("世界准备完成（x=300，normal，关闭自然刷怪）");
	}

	private static void prepareEquipment(ServerPlayer player) {
		player.getInventory().clearContent();
		// 必须在穿靴子前记录基础移速，否则会把靴子的 +20% 当成基准。
		// 玩家进入世界时可能还穿着上次存档的回响靴子，getValue() 会带上 +20%。
		// 用 baseValue() 取真正的裸移速 0.1，后续比值才是 1.2 / 1.4。
		baseSpeed = player.getAttribute(Attributes.MOVEMENT_SPEED).getBaseValue();
		player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ModItems.ECHO_HELMET.get()));
		player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(ModItems.ECHO_CHESTPLATE.get()));
		player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(ModItems.ECHO_LEGGINGS.get()));
		player.setItemSlot(EquipmentSlot.FEET, new ItemStack(ModItems.ECHO_BOOTS.get()));
		player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.NETHERITE_SWORD));
		PlayerEximusShield.set(player, 0.0D);
		PcfManager.setValue(player, 0.0D);
	}

	private static void checkPcfEquipment(ServerPlayer player) {
		PcfManager.Snapshot snapshot = PcfManager.snapshot(player);
		check("PCF 装备分约 18.4", Math.abs(snapshot.equipment() - 18.4D) < 0.3D,
				"equipment=" + snapshot.equipment());
		log("PCF 快照：value=%.2f calc=%.2f equipment=%.2f boss=%.2f exploration=%.2f resource=%.2f"
				.formatted(snapshot.value(), snapshot.calc(), snapshot.equipment(), snapshot.boss(),
						snapshot.exploration(), snapshot.resource()));
		PcfManager.setValue(player, 10.0D);
		CdfManager.setCurrent(player.serverLevel(), player.blockPosition(), 5.0D);
	}

	private static void checkNightVision(ServerPlayer player) {
		check("回响头盔提供夜视", player.hasEffect(MobEffects.NIGHT_VISION),
				"night_vision=" + player.getEffect(MobEffects.NIGHT_VISION));
		MobEffectInstance vision = player.getEffect(MobEffects.NIGHT_VISION);
		if (vision != null) {
			check("夜视剩余 >200 tick（防闪烁）", vision.getDuration() > 200,
					"duration=" + vision.getDuration());
		}
		boolean added = player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 200, 0));
		check("失明被头盔拒绝", !added && player.getEffect(MobEffects.BLINDNESS) == null,
				"added=" + added);
	}

	private static void checkBootsWithoutShield(ServerPlayer player) {
		EchoArmorEffects.refreshBoots(player);
		checkBootsRatio(player, 1.20D, "靴子无盾移速 ×1.20");
	}

	private static void checkBootsWithShield(ServerPlayer player) {
		PlayerEximusShield.set(player, 50.0D);
		EchoArmorEffects.refreshBoots(player);
		checkBootsRatio(player, 1.40D, "靴子有盾移速 ×1.40");
	}

	/**
	 * 只比较“剔除回响靴子修饰符后的基线”与“当前总移速”。
	 * FULL 档下 SR 会额外加移速，不能拿原版 0.1 当分母。
	 */
	private static void checkBootsRatio(ServerPlayer player, double want, String label) {
		var speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed == null) {
			check(label, false, "缺少 movement_speed 属性");
			return;
		}
		var boots = speed.getModifiers().stream()
				.filter(m -> "summy_threat:echo_boots_speed".equals(m.getName()))
				.findFirst().orElse(null);
		if (boots == null) {
			check(label, false, "未找到回响靴子修饰符");
			return;
		}
		double base = speed.getBaseValue();
		double addition = 0.0D;
		double multiplyBase = 0.0D;
		double multiplyTotalProduct = 1.0D;
		for (var modifier : speed.getModifiers()) {
			if (modifier.getId().equals(boots.getId())) {
				continue;
			}
			switch (modifier.getOperation()) {
				case ADDITION -> addition += modifier.getAmount();
				case MULTIPLY_BASE -> multiplyBase += modifier.getAmount();
				// 原版 MULTIPLY_TOTAL 是逐个乘 (1+amount)，不是先求和再乘。
				case MULTIPLY_TOTAL -> multiplyTotalProduct *= 1.0D + modifier.getAmount();
			}
		}
		double baseline = (base + addition + base * multiplyBase) * multiplyTotalProduct;
		double now = speed.getValue();
		double ratio = baseline == 0.0D ? 0.0D : now / baseline;
		check(label, Math.abs(ratio - want) < 0.02D,
				"baseline=%.4f now=%.4f ratio=%.3f".formatted(baseline, now, ratio));
	}

	private static void checkLeggings(ServerPlayer player) {
		PlayerEximusShield.set(player, 0.0D);
		check("护腿无盾对护盾伤害 ×1.30",
				Math.abs(EchoArmorEffects.shieldDamageMultiplier(player) - 1.30D) < 0.001D,
				"mult=" + EchoArmorEffects.shieldDamageMultiplier(player));
		PlayerEximusShield.set(player, 50.0D);
		check("护腿有盾对护盾伤害 ×1.50",
				Math.abs(EchoArmorEffects.shieldDamageMultiplier(player) - 1.50D) < 0.001D,
				"mult=" + EchoArmorEffects.shieldDamageMultiplier(player));
	}

	private static void prepareChestKill(ServerPlayer player) {
		PlayerEximusShield.set(player, 0.0D);
		Zombie eximus = EntityType.ZOMBIE.create(player.serverLevel());
		if (eximus == null) {
			check("胸甲击杀测试生成卓越者", false, "create 返回 null");
			return;
		}
		eximus.moveTo(player.getX() + 2.0D, player.getY(), player.getZ());
		eximus.getPersistentData().putBoolean(MonsterScaler.TAG_EXIMUS, true);
		player.serverLevel().addFreshEntity(eximus);
		chestEximus = eximus;
		MonsterScaler.scaleNow(eximus, 20, MonsterScaler.Kind.EXIMUS);
		check("胸甲击杀测试卓越者有护盾", EximusShield.current(eximus) > 0.0D,
				"shield=" + EximusShield.current(eximus));
		log("胸甲击杀前：chest=%s eximus=%s shield=%.1f"
				.formatted(EchoArmorEffects.hasChestplate(player), EximusShield.isEximus(eximus),
						EximusShield.current(eximus)));
		boolean hurt = eximus.hurt(player.damageSources().playerAttack(player), 1_000_000.0F);
		log("破盾击：hurt=%s dead=%s shield=%.1f"
				.formatted(hurt, eximus.isDeadOrDying(), EximusShield.current(eximus)));
	}

	private static void finishChestKill(ServerPlayer player) {
		if (chestEximus == null) {
			check("胸甲击杀卓越者 +20 护盾", false, "chestEximus 为空");
			return;
		}
		boolean hurt = chestEximus.hurt(player.damageSources().playerAttack(player), 1_000_000.0F);
		log("第二击：hurt=%s dead=%s playerShield=%.1f"
				.formatted(hurt, chestEximus.isDeadOrDying(), PlayerEximusShield.current(player)));
		check("胸甲击杀卓越者 +20 护盾",
				Math.abs(PlayerEximusShield.current(player) - 20.0D) < 0.01D,
				"playerShield=" + PlayerEximusShield.current(player));
		chestEximus.discard();
		chestEximus = null;
	}

	private static void checkAutoTl(ServerPlayer player) {
		run(player, "summythreat tl auto");
		Zombie zombie = EntityType.ZOMBIE.create(player.serverLevel());
		if (zombie == null) {
			check("生成自动 TL 测试僵尸", false, "create 返回 null");
			return;
		}
		zombie.moveTo(player.getX() + 2.0D, player.getY(), player.getZ());
		player.serverLevel().addFreshEntity(zombie);
		int tl = zombie.getPersistentData().getInt(MonsterScaler.TAG_TL);
		check("普通怪自动 TL 在 2~7", tl >= 2 && tl <= 7, "tl=" + tl);
		zombie.discard();
	}

	/**
	 * 固定 PCF/CDF → TL 映射：普通/卓越者单玩家 PCF 同值，区域倍率由位置决定。
	 * 每组采样 8 次，覆盖 TlSelector 的 random(-1,1) 浮动。
	 */
	private static void checkPcfCdfMapping(ServerPlayer player) {
		ServerLevel level = player.serverLevel();
		run(player, "summythreat tier full");
		run(player, "summythreat tl auto");
		// normal_biome：平台在 x=300，距出生点 300 格（>200）。
		player.teleportTo(level, 300, 100, 0, 0.0F, 0.0F);
		mappingCase(player, 0, 0, "normal", 0, 1);
		mappingCase(player, 5, 5, "normal", 4, 6);
		mappingCase(player, 10, 5, "normal", 4, 6);
		mappingCase(player, 20, 20, "normal", 19, 20);
		mappingCase(player, 20, 0, "normal", 0, 1);
		// spawn_protection：出生点 200 格内，区域倍率 0.5。
		BlockPos spawn = level.getSharedSpawnPos();
		player.teleportTo(level, spawn.getX(), 100, spawn.getZ(), 0.0F, 0.0F);
		mappingCase(player, 10, 10, "protect", 4, 6);
		mappingCase(player, 20, 20, "protect", 9, 11);
		mappingCase(player, 0, 0, "protect", 0, 1);
		player.teleportTo(level, 300, 100, 0, 0.0F, 0.0F);
		log("MAPPING " + String.join(" | ", MAPPING));
	}

	private static void mappingCase(ServerPlayer player, int pcf, int cdf, String region,
			int lo, int hi) {
		PcfManager.setValue(player, pcf);
		CdfManager.setCurrent(player.serverLevel(), player.blockPosition(), cdf);
		int min = 99;
		int max = -1;
		Map<Integer, Integer> freq = new TreeMap<>();
		// 随机浮动 ±1：每组 200 次，覆盖边界并验证中间值的分布。
		for (int i = 0; i < 200; i++) {
			Zombie zombie = EntityType.ZOMBIE.create(player.serverLevel());
			if (zombie == null) {
				check("映射生成僵尸", false, "create 返回 null");
				return;
			}
			zombie.moveTo(player.getX(), player.getY(), player.getZ());
			player.serverLevel().addFreshEntity(zombie);
			int tl = zombie.getPersistentData().getInt(MonsterScaler.TAG_TL);
			min = Math.min(min, tl);
			max = Math.max(max, tl);
			freq.merge(tl, 1, Integer::sum);
			zombie.discard();
		}
		StringBuilder dist = new StringBuilder();
		for (Map.Entry<Integer, Integer> entry : freq.entrySet()) {
			if (dist.length() > 0) {
				dist.append(',');
			}
			dist.append(entry.getKey()).append('x').append(entry.getValue());
		}
		MAPPING.add("%s pcf=%d cdf=%d tl=%d..%d dist=%s"
				.formatted(region, pcf, cdf, min, max, dist));
		check("映射 %s PCF=%d CDF=%d TL %d~%d".formatted(region, pcf, cdf, lo, hi),
				min >= lo && max <= hi, "实测 %d..%d".formatted(min, max));
		if (lo > 0 && hi < 20) {
			int center = (lo + hi) / 2;
			int lowCount = freq.getOrDefault(center - 1, 0);
			int highCount = freq.getOrDefault(center + 1, 0);
			check("映射 %s PCF=%d CDF=%d ±1 两端覆盖（各≥10/200）"
					.formatted(region, pcf, cdf),
					lowCount >= 10 && highCount >= 10,
					"center=%d low=%d high=%d dist=%s".formatted(center, lowCount, highCount, dist));
		}
	}

	/**
	 * 随机性专项：5% 概率、8 原型均匀、14 能力抽取的数量/不重复/频率。
	 * 样本量按测试准则：概率 20,000 次、原型每类 ≥1,500 次、能力每档 2,000 次。
	 */
	private static void checkRandomness(ServerPlayer player) {
		RandomSource random = RandomSource.create();
		int hits = 0;
		for (int i = 0; i < 20_000; i++) {
			if (EximusSpawner.roll(random, 0.05D)) {
				hits++;
			}
		}
		double ratio = hits / 20_000.0D;
		log("RANDOM_EXIMUS hits=%d total=20000 ratio=%.5f".formatted(hits, ratio));
		check("随机 5% 卓越者比例 5%±0.5%", Math.abs(ratio - 0.05D) <= 0.005D,
				"hits=%d/20000 ratio=%.2f%%".formatted(hits, ratio * 100.0D));

		random = RandomSource.create();
		int[] archetypes = new int[EximusArchetype.values().length];
		for (int i = 0; i < 16_000; i++) {
			archetypes[EximusArchetype.random(random).ordinal()]++;
		}
		StringBuilder archLog = new StringBuilder();
		boolean archOk = true;
		for (EximusArchetype type : EximusArchetype.values()) {
			int count = archetypes[type.ordinal()];
			archLog.append(type.id()).append('=').append(count).append(' ');
			archOk &= count >= 1_500 && count <= 2_500;
		}
		log("RANDOM_ARCH " + archLog);
		check("随机 8 原型均匀（各 1500~2500/16000）", archOk, archLog.toString());

		int[] tls = {0, 3, 4, 5, 7, 8, 9, 11, 12, 13, 15, 16, 17, 20};
		for (int tl : tls) {
			int poolSize = 0;
			for (AbilityId id : AbilityId.values()) {
				if (id.unlockTl() <= tl) {
					poolSize++;
				}
			}
			int expected = Math.min(poolSize, AbilityId.baseCountFor(tl));
			Map<String, Integer> freq = new HashMap<>();
			int dup = 0;
			int wrongCount = 0;
			for (int i = 0; i < 2_000; i++) {
				String rolled = AbilityEffects.roll(tl, 0, random, true);
				List<String> ids = rolled.isEmpty() ? List.of() : List.of(rolled.split(","));
				if (ids.size() != expected) {
					wrongCount++;
				}
				Set<String> unique = new HashSet<>(ids);
				if (unique.size() != ids.size()) {
					dup++;
				}
				for (String id : ids) {
					freq.merge(id, 1, Integer::sum);
				}
			}
			double expectedFreq = poolSize == 0 ? 0.0D : 2_000.0D * expected / poolSize;
			boolean freqOk = true;
			for (Map.Entry<String, Integer> entry : freq.entrySet()) {
				if (entry.getValue() < expectedFreq * 0.75D
						|| entry.getValue() > expectedFreq * 1.25D) {
					freqOk = false;
				}
			}
			boolean allSeen = freq.size() == poolSize;
			log("RANDOM_ABILITY tl=%d pool=%d expected=%d wrong=%d dup=%d freq=%s"
					.formatted(tl, poolSize, expected, wrongCount, dup, freq));
			check("随机能力 TL%d 数量精确（%d）".formatted(tl, expected), wrongCount == 0,
					"wrong=%d".formatted(wrongCount));
			check("随机能力 TL%d 不重复".formatted(tl), dup == 0, "dup=%d".formatted(dup));
			check("随机能力 TL%d 池内全覆盖".formatted(tl), allSeen,
					"seen=%d pool=%d".formatted(freq.size(), poolSize));
			check("随机能力 TL%d 频率 ±25%%".formatted(tl), freqOk,
					"expectedFreq=%.1f freq=%s".formatted(expectedFreq, freq));
		}
	}

	/** 结构 ×1.5 与下界/末地 ×1.2 的区域倍率实测。 */
	private static void checkRegionMultipliers(ServerPlayer player) {
		ServerLevel overworld = player.serverLevel();
		BlockPos found = overworld.findNearestMapStructure(
				StructureTags.VILLAGE, player.blockPosition(), 256, false);
		if (found == null) {
			check("结构倍率：找到村庄", false, "findNearestMapStructure 返回 null");
		} else {
			BlockPos pos = found;
			player.teleportTo(overworld, pos.getX(), pos.getY() + 2, pos.getZ(), 0.0F, 0.0F);
			String region = SpawnRegion.detect(overworld, player.position(), MobSpawnType.NATURAL).id();
			check("结构区域判定", "structure".equals(region),
					"region=%s pos=%s".formatted(region, pos));
			PcfManager.setValue(player, 10.0D);
			CdfManager.setCurrent(overworld, player.blockPosition(), 10.0D);
			mappingCase(player, 10, 10, "structure", 14, 16);
		}
		ServerLevel nether = player.server.getLevel(Level.NETHER);
		if (nether == null) {
			check("下界维度可用", false, "getLevel(NETHER) 返回 null");
		} else {
			player.teleportTo(nether, 1000, 100, 1000, 0.0F, 0.0F);
			String region = SpawnRegion.detect(nether, player.position(), MobSpawnType.NATURAL).id();
			check("下界区域判定", "nether_end".equals(region), "region=" + region);
			PcfManager.setValue(player, 10.0D);
			CdfManager.setCurrent(nether, player.blockPosition(), 10.0D);
			mappingCase(player, 10, 10, "nether", 11, 13);
		}
		player.teleportTo(overworld, 300, 100, 0, 0.0F, 0.0F);
		log("MAPPING_REGION " + String.join(" | ", MAPPING));
	}

	/** 在 51x51 黑暗平台上验证自然刷新；chance 用来区分 100% 与 5% 两档。 */
	private static void prepareNaturalSpawn(ServerPlayer player, double chance, String label) {
		ServerLevel level = player.serverLevel();
		run(player, "gamerule doMobSpawning true");
		run(player, "time set midnight");
		run(player, "weather clear");
		run(player, "difficulty normal");
		Config.EXIMUS_NATURAL_SPAWN_CHANCE.set(chance);
		SpawnAudit.reset();
		SpawnAudit.setRecording(true);
		player.teleportTo(level, 300, 130, 0, 0.0F, 0.0F);
		log("自然刷新准备（" + label + "）：51x51 平台，玩家 y=130，chance=" + chance);
	}

	private static void checkNatural100(ServerPlayer player) {
		String summary = SpawnAudit.summary();
		log("SPAWN_AUDIT_100 " + summary);
		int natural = parseCount(summary, "natural=");
		int eximus = parseCount(summary, "naturalEximus=");
		check("100% 自然刷新发生（natural>0）", natural > 0, "natural=" + natural);
		check("100% 卓越者（naturalEximus=natural）", natural > 0 && eximus == natural,
				"natural=%d eximus=%d".formatted(natural, eximus));
		SpawnAudit.reset();
	}

	private static void checkNatural5(ServerPlayer player) {
		String summary = SpawnAudit.summary();
		log("SPAWN_AUDIT_5 " + summary);
		int natural = parseCount(summary, "natural=");
		int eximus = parseCount(summary, "naturalEximus=");
		double ratio = natural > 0 ? (double) eximus / natural : 0.0D;
		check("5% 自然刷新发生（natural>0）", natural > 0, "natural=" + natural);
		check("5% 分支不是全卓越者", natural > 0 && eximus < natural,
				"natural=%d eximus=%d".formatted(natural, eximus));
		check("5% 比例在 0~15% 量级", natural > 0 && ratio <= 0.15D,
				"ratio=%.1f%%".formatted(ratio * 100.0D));
		Config.EXIMUS_NATURAL_SPAWN_CHANCE.set(0.05D);
		SpawnAudit.setRecording(false);
		run(player, "gamerule doMobSpawning false");
		run(player, "kill @e[type=!minecraft:player]");
	}

	/** 刷怪笼对照：SPAWNER 类型不参与自然卓越者掷骰。 */
	private static void prepareSpawner(ServerPlayer player) {
		run(player, "setblock 300 99 0 minecraft:spawner{SpawnData:{entity:{id:\"minecraft:zombie\"}},"
				+ "SpawnCount:1,SpawnRange:4,Delay:20,MinSpawnDelay:20,MaxSpawnDelay:40,RequiredPlayerRange:16}");
		player.teleportTo(player.serverLevel(), 300, 100, 0, 0.0F, 0.0F);
		SpawnAudit.reset();
		SpawnAudit.setRecording(true);
		log("刷怪笼对照准备：300 99 0，僵尸刷怪笼，玩家距离 1 格");
	}

	private static void checkSpawner(ServerPlayer player) {
		String summary = SpawnAudit.summary();
		log("SPAWN_AUDIT_SPAWNER " + summary);
		int natural = parseCount(summary, "natural=");
		int eximus = parseCount(summary, "naturalEximus=");
		check("刷怪笼生成记录 SPAWNER", summary.contains("SPAWNER:"), summary);
		check("刷怪笼不计自然候选", natural == 0, "natural=" + natural);
		check("刷怪笼不产生卓越者", eximus == 0, "naturalEximus=" + eximus);
		SpawnAudit.setRecording(false);
		run(player, "setblock 300 99 0 minecraft:stone");
		run(player, "kill @e[type=!minecraft:player]");
	}

	private static int parseCount(String text, String prefix) {
		int start = text.indexOf(prefix);
		if (start < 0) {
			return -1;
		}
		start += prefix.length();
		int end = start;
		while (end < text.length() && Character.isDigit(text.charAt(end))) {
			end++;
		}
		return end > start ? Integer.parseInt(text.substring(start, end)) : -1;
	}

	private static void checkManualTl(ServerPlayer player) {
		run(player, "summythreat tl 20");
		Zombie zombie = EntityType.ZOMBIE.create(player.serverLevel());
		if (zombie == null) {
			check("生成手动 TL 测试僵尸", false, "create 返回 null");
			return;
		}
		zombie.moveTo(player.getX() + 2.0D, player.getY(), player.getZ());
		player.serverLevel().addFreshEntity(zombie);
		int tl = zombie.getPersistentData().getInt(MonsterScaler.TAG_TL);
		check("手动 TL 覆盖为 20", tl == 20, "tl=" + tl);
		zombie.discard();
		run(player, "summythreat tl auto");
	}

	/** 在玩家正前方生成带能力的卓越者，客户端截图用于人工确认 Jade 两行格式。 */
	private static void prepareJadeTarget(ServerPlayer player) {
		Zombie eximus = EntityType.ZOMBIE.create(player.serverLevel());
		if (eximus == null) {
			return;
		}
		// 固定生成在 +x 3 格，并把玩家朝向设为 -90°（东），避免 getLookAngle()
		// 在服务端读到旧视角、导致截图里看不到目标。
		eximus.moveTo(player.getX() + 3.0D, player.getY(), player.getZ());
		eximus.getPersistentData().putBoolean(MonsterScaler.TAG_EXIMUS, true);
		player.serverLevel().addFreshEntity(eximus);
		MonsterScaler.scaleNow(eximus, 20, MonsterScaler.Kind.EXIMUS);
		player.teleportTo(player.serverLevel(), player.getX(), player.getY(), player.getZ(), -90.0F, 0.0F);
		log("Jade 目标已生成：TL20 卓越者，距离 3.5 格");
	}

	private static void prepareHud(ServerPlayer player) {
		PlayerEximusShield.set(player, 50.0D);
		log("HUD 测试值已设为 50/100");
	}

	private static void cleanup(ServerPlayer player) {
		PlayerEximusShield.set(player, 0.0D);
		run(player, "summythreat tl auto");
		run(player, "summythreat tier auto");
		run(player, "kill @e[type=minecraft:zombie]");
	}

	private static void screenshot(Minecraft client, String name) {
		Screenshot.grab(client.gameDirectory, name, client.getMainRenderTarget(),
				message -> log("截图 " + name + "：" + message));
	}

	private static void run(ServerPlayer player, String command) {
		player.getServer().getCommands().performPrefixedCommand(
				player.createCommandSourceStack().withPermission(3), command);
	}

	private static void check(String label, boolean ok, String detail) {
		checks++;
		if (!ok) {
			FAILURES.add(label + "｜" + detail);
		}
		log((ok ? "OK  " : "FAIL") + " " + label + "｜" + detail);
	}

	private static void finish(Minecraft client, String reason) {
		if (finished) {
			return;
		}
		finished = true;
		log("===== " + reason + "：检查 " + checks + " 项，失败 " + FAILURES.size() + " 项 =====");
		for (String failure : FAILURES) {
			log("FAILED｜" + failure);
		}
		client.stop();
	}

	private static void log(String message) {
		SummyThreat.LOGGER.info("[DEVCHECK] " + message);
	}

	// 供同包的自检模块复用统一的计数 / 日志出口。
	static void checkDetailed(String label, boolean ok, String detail) {
		check(label, ok, detail);
	}

	static void logDetailed(String message) {
		log(message);
	}

	static void runCommand(ServerPlayer player, String command) {
		run(player, command);
	}
}
