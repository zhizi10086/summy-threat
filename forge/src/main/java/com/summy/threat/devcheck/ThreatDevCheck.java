package com.summy.threat.devcheck;

import com.summy.threat.SummyThreat;
import com.summy.threat.ability.AbilityEffects;
import com.summy.threat.ability.AbilityId;
import com.summy.threat.eximus.EximusArchetype;
import com.summy.threat.eximus.EximusSpawner;
import com.summy.threat.equipment.EximusEquipmentData;
import com.summy.threat.equipment.StarPrismData;
import com.summy.threat.item.ModItems;
import com.summy.threat.pcf.CdfManager;
import com.summy.threat.pcf.PcfManager;
import com.summy.threat.pcf.SpawnRegion;
import com.summy.threat.pcf.TlSelector;
import com.summy.threat.scaling.MonsterScaler;
import com.summy.threat.scaling.ScalingState;
import com.summy.threat.scaling.SpawnAudit;
import com.summy.threat.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
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
	private static final boolean BETRAYAL_CHECK = Boolean.getBoolean("summy_threat.betrayalcheck");
	private static final boolean LEADER_CHECK = Boolean.getBoolean("summy_threat.leadercheck");
	private static final boolean EQUIPMENT_CHECK = Boolean.getBoolean("summy_threat.equipmentcheck");
	private static final boolean MAGIC_CHECK = Boolean.getBoolean("summy_threat.magiccheck");
	// 主菜单阶段要留给玩家创建/选择 DevCheckWorld 的时间；30 秒会在世界创建前
	// 就把客户端关掉，因此这里放宽到 10 分钟，总运行上限放宽到 20 分钟。
	private static final int IDLE_STOP_TICKS = 12000;
	private static final int TOTAL_STOP_TICKS = 24000;
	private static final int CLIENT_STOP_TICK = EQUIPMENT_CHECK ? 400
			: BETRAYAL_CHECK ? 180 : LEADER_CHECK ? 240 : MAGIC_CHECK ? 140 : 1520;
	private static final List<String> FAILURES = new ArrayList<>();
	private static int serverTick;
	private static int clientTick;
	private static int idleTicks;
	private static int totalClientTicks;
	private static boolean finished;
	private static boolean b15EquipmentStarted;
	private static boolean b15EquipmentTicked;
	private static boolean equipmentClientRan;
	private static int b15EquipmentStartTick;
	private static int equipmentStableTicks;
	private static Map<String, Object> equipmentConfigOriginal;
	private static int checks;
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
			if (MAGIC_CHECK) {
				switch (serverTick) {
					case 20 -> prepareWorld(player);
					case 60 -> FAILURES.addAll(MagicResistanceDevCheck.run(player));
					default -> {
					}
				}
				return;
			}
			if (EQUIPMENT_CHECK) {
				switch (serverTick) {
					case 20 -> {
						prepareWorld(player);
					}
					default -> {
					}
				}
				// KubeJS 启动期保存配置会触发一次异步重载；连续稳定后再落断言。
				if (serverTick >= 100) {
					equipmentStableTicks = Config.SPEC.isLoaded() ? equipmentStableTicks + 1 : 0;
				}
				if (serverTick >= 200 && equipmentStableTicks >= 80 && !b15EquipmentStarted) {
					b15EquipmentStarted = true;
					b15EquipmentStartTick = serverTick;
					FAILURES.addAll(EquipmentSystemDevCheck.start(player));
					FAILURES.addAll(KubeJSCompatDevCheck.run(player));
				} else if (b15EquipmentStarted && !b15EquipmentTicked
						&& serverTick > b15EquipmentStartTick) {
					b15EquipmentTicked = true;
					FAILURES.addAll(EquipmentSystemDevCheck.tick(player));
				}
				return;
			}
			if (BETRAYAL_CHECK) {
				switch (serverTick) {
					case 20 -> prepareWorld(player);
					case 100 -> FAILURES.addAll(BetrayalIntegrationDevCheck.run(player));
					default -> {
					}
				}
				return;
			}
			if (LEADER_CHECK) {
				switch (serverTick) {
					case 20 -> prepareWorld(player);
					case 40 -> prepareEquipment(player);
					case 60 -> FAILURES.addAll(LeaderAbilityDevCheck.run(player));
					default -> {
					}
				}
				return;
			}
			switch (serverTick) {
				case 20 -> prepareWorld(player);
				case 40 -> prepareEquipment(player);
				case 120 -> checkPcfEquipment(player);
				case 260 -> checkAutoTl(player);
				case 270 -> checkPcfCdfMapping(player);
				case 280 -> checkRandomness(player);
				case 285 -> checkRegionMultipliers(player);
				case 292 -> FAILURES.addAll(BossHardcodedDamageDevCheck.run(player));
				case 293 -> FAILURES.addAll(MagicResistanceDevCheck.run(player));
				case 294 -> FAILURES.addAll(ArmorPenetrationDevCheck.start(player));
				case 296 -> FAILURES.addAll(KubeJSCompatDevCheck.run(player));
				case 298 -> FAILURES.addAll(ClosureRuleDevCheck.run(player));
				case 299 -> {
					if (LEADER_CHECK) {
						FAILURES.addAll(LeaderAbilityDevCheck.run(player));
					}
				}
				case 300 -> {
					if (BETRAYAL_CHECK) {
						FAILURES.addAll(BetrayalIntegrationDevCheck.run(player));
					}
				}
				case 100 -> equipmentConfigOriginal = EquipmentDropDevCheck.prepareDeterministicConfig();
				case 320 -> FAILURES.addAll(EquipmentDropSrIntegrationDevCheck.run(player));
				case 321 -> {
					EquipmentDropDevCheck.restoreConfig(equipmentConfigOriginal);
					equipmentConfigOriginal = null;
				}
				case 340 -> FAILURES.addAll(ArmorPenetrationDevCheck.checkShortWindow());
				case 430 -> FAILURES.addAll(ArmorPenetrationDevCheck.finish());
				case 350 -> prepareNaturalSpawn(player, 1.0D, "100%");
				case 750 -> checkNatural100(player);
				case 770 -> prepareNaturalSpawn(player, 0.05D, "5%");
				case 1170 -> checkNatural5(player);
				case 1190 -> prepareSpawner(player);
				case 1290 -> checkSpawner(player);
				case 1310 -> checkManualTl(player);
				case 1330 -> prepareJadeTarget(player);
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
		if (EQUIPMENT_CHECK && clientTick >= 300 && !equipmentClientRan) {
			equipmentClientRan = true;
			checkEquipmentClientTooltips(client);
		}
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

	private static void checkEquipmentClientTooltips(Minecraft client) {
		ItemStack prism = new ItemStack(ModItems.ABYSSAL_STAR_PRISM.get());
		StarPrismData.write(prism, new StarPrismData(StarPrismData.SCHEMA, true, null,
				"astral_plating", "prism_edge", "starlit_bowstring"));
		String prismText = prism.getTooltipLines(client.player, TooltipFlag.Default.NORMAL)
				.stream().map(net.minecraft.network.chat.Component::getString)
				.reduce("", (left, right) -> left + "\n" + right);
		check("B15 客户端棱晶提示显示三系与武器锁定",
				(prismText.contains("星铸甲胄") || prismText.contains("Astral Plating"))
						&& (prismText.contains("武器功能尚未启用")
								|| prismText.contains("Weapon function is not yet enabled"))
						&& prismText.contains("[T1]"),
				prismText);

		ItemStack armor = EximusEquipmentData.withUpgradeLevel(
				new ItemStack(Items.DIAMOND_CHESTPLATE), 2);
		armor = EximusEquipmentData.withPrism(armor, 0, prism);
		String armorText = armor.getTooltipLines(client.player, TooltipFlag.Default.NORMAL)
				.stream().map(net.minecraft.network.chat.Component::getString)
				.reduce("", (left, right) -> left + "\n" + right);
		check("B15 客户端装备提示显示编号槽位与激活类别",
				(armorText.contains("槽位 1") || armorText.contains("Slot 1"))
						&& (armorText.contains("当前激活类别") || armorText.contains("Active category")),
				armorText);
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
		player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.NETHERITE_HELMET));
		player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.NETHERITE_CHESTPLATE));
		player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.NETHERITE_LEGGINGS));
		player.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.NETHERITE_BOOTS));
		player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.NETHERITE_SWORD));
		PcfManager.setValue(player, 0.0D);
	}

	private static void checkPcfEquipment(ServerPlayer player) {
		PcfManager.Snapshot snapshot = PcfManager.snapshot(player);
		check("PCF 装备分有效", snapshot.equipment() > 0.0D,
				"equipment=" + snapshot.equipment());
		log("PCF 快照：value=%.2f calc=%.2f equipment=%.2f boss=%.2f exploration=%.2f resource=%.2f"
				.formatted(snapshot.value(), snapshot.calc(), snapshot.equipment(), snapshot.boss(),
						snapshot.exploration(), snapshot.resource()));
		PcfManager.setValue(player, 10.0D);
		CdfManager.setCurrent(player.serverLevel(), player.blockPosition(), 5.0D);
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
		Zombie directProbe = EntityType.ZOMBIE.create(player.serverLevel());
		if (directProbe != null) {
			directProbe.moveTo(player.getX(), player.getY(), player.getZ());
		}
		double probePcf = PcfManager.selectedPcf(player.serverLevel(), player.position(),
				MonsterScaler.Kind.NORMAL);
		int probeTl = directProbe == null ? -1
				: TlSelector.select(player.serverLevel(), directProbe,
						MonsterScaler.Kind.NORMAL, MobSpawnType.NATURAL);
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
		log(("MAPPING_DIAG case=%s pcf=%d cdf=%d min=%d max=%d selectedPcf=%.3f directTl=%d "
				+ "players=%d probePos=%s playerPos=%s playerLevel=%s")
				.formatted(region, pcf, cdf, min, max, probePcf, probeTl,
						player.serverLevel().getPlayers(value -> true).size(),
						directProbe == null ? "null" : directProbe.position(),
						player.position(), player.serverLevel().dimension().location()));
		check("映射 %s PCF=%d CDF=%d TL %d~%d".formatted(region, pcf, cdf, lo, hi),
				min >= lo && max <= hi, "实测 %d..%d globalTl=%d cdfNow=%.3f pcfNow=%.3f "
						+ "selectedPcf=%.3f directTl=%d regionNow=%s players=%d probePos=%s "
						+ "playerPos=%s sameLevel=%s"
						.formatted(probePcf, probeTl,
								SpawnRegion.forEntity(player.serverLevel(), player).id(),
								player.serverLevel().getPlayers(value -> true).size(),
								directProbe == null ? "null" : directProbe.position(),
								player.position(), player.serverLevel() == player.level()));
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
		List<EximusArchetype> archetypeValues = List.of(EximusArchetype.values());
		int[] archetypes = new int[archetypeValues.size()];
		for (int i = 0; i < 16_000; i++) {
			EximusArchetype rolled = EximusArchetype.random(random);
			int index = archetypeValues.indexOf(rolled);
			if (index >= 0) {
				archetypes[index]++;
			}
		}
		StringBuilder archLog = new StringBuilder();
		boolean archOk = true;
		for (int i = 0; i < archetypeValues.size(); i++) {
			EximusArchetype type = archetypeValues.get(i);
			int count = archetypes[i];
			archLog.append(type.id()).append('=').append(count).append(' ');
			if (type.builtIn()) {
				archOk &= count >= 1_500 && count <= 2_500;
			}
		}
		log("RANDOM_ARCH " + archLog);
		check("随机 8 原型均匀（各 1500~2500/16000）", archOk, archLog.toString());

		int[] tls = {0, 3, 4, 5, 7, 8, 9, 11, 12, 13, 15, 16, 17, 20};
		for (int tl : tls) {
			int poolSize = AbilityId.rollable(tl, false, true).length;
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

	private static void cleanup(ServerPlayer player) {
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
		if (EQUIPMENT_CHECK) {
			log("[EQUIPMENT] FAIL=" + FAILURES.size());
		}
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

	static void recordSubcheck() {
		checks++;
	}

	static void runCommand(ServerPlayer player, String command) {
		run(player, command);
	}
}
