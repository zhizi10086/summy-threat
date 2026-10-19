package com.summy.threat.command;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.summy.threat.Config;
import com.summy.threat.ability.AbilityEffects;
import com.summy.threat.ability.AbilityId;
import com.summy.threat.armor.PlayerEximusShield;
import com.summy.threat.eximus.EximusArchetype;
import com.summy.threat.combat.ArmorDamageFormula;
import com.summy.threat.pcf.CdfManager;
import com.summy.threat.pcf.PcfManager;
import com.summy.threat.pcf.SpawnRegion;
import com.summy.threat.scaling.CombatMeter;
import com.summy.threat.scaling.MonsterScaler;
import com.summy.threat.scaling.ScalingState;
import com.summy.threat.scaling.SpawnAudit;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Locale;

/**
 * 调试 / 测量命令：{@code /summythreat ...}（OP 2 级）。
 *
 * <ul>
 *     <li>{@code tier [mode]}：查看或强制生态档位（auto / none / sr / se / sr_apocrypha / se_sr / full / free）。</li>
 *     <li>{@code free list|get|set}：查看或修改 free 档白名单内的成长缩放参数。</li>
 *     <li>{@code tl [0-20|auto|off]}：查看或设置全局 TL（auto = PCF/CDF，off = 完全关闭）。</li>
 *     <li>{@code pcf [玩家]} / {@code pcf set <玩家> <0-20>}：查看或调试 PCF。</li>
 *     <li>{@code cdf} / {@code cdf <0-20>}：查看或调试当前区块 CDF。</li>
 *     <li>{@code spawnstats [on|off|reset]}：查看刷怪类型与卓越者词条发布审计。</li>
 *     <li>{@code spawn <实体ID> <tl> [eximus|leader] [pcf]}：定点召唤一只已数值化的怪。</li>
 *     <li>{@code eximus <实体ID> [tl] [原型|能力] [原型|能力]}：直接召唤卓越者；原型支持
 *         flame / frost / poison / storm / shock / guardian / vampire / magnet，
 *         {@code clear} 清空原型，{@code none} 抑制 TL 能力。</li>
 *     <li>{@code stats [玩家]}：查看计量结果（有效 DPS / 击杀耗时 / 无视护甲占比 / 承伤）。</li>
 *     <li>{@code shield [0-100]}：查看或设置自己的玩家卓越护盾（调试用）。</li>
 * </ul>
 */
public final class SummyThreatCommand {
	private SummyThreatCommand() {
	}

	@SubscribeEvent
	public static void onRegisterCommands(RegisterCommandsEvent event) {
		event.getDispatcher().register(Commands.literal("summythreat")
				.requires(source -> source.hasPermission(2))
				.then(Commands.literal("tier")
						.executes(SummyThreatCommand::showTier)
						.then(Commands.literal("free")
								.executes(ctx -> setTier(ctx, "free"))
								.then(Commands.argument("hp", DoubleArgumentType.doubleArg(0.0D, 2.0D))
										.executes(ctx -> setFreeTier(ctx,
												DoubleArgumentType.getDouble(ctx, "hp"), null))
										.then(Commands.argument("atk", DoubleArgumentType.doubleArg(0.0D, 2.0D))
												.executes(ctx -> setFreeTier(ctx,
														DoubleArgumentType.getDouble(ctx, "hp"),
														DoubleArgumentType.getDouble(ctx, "atk"))))))
						.then(Commands.argument("mode", StringArgumentType.word())
								.executes(ctx -> setTier(ctx, StringArgumentType.getString(ctx, "mode")))))
				.then(Commands.literal("tl")
						.executes(SummyThreatCommand::showTl)
						.then(Commands.literal("off").executes(ctx -> setTl(ctx, -2)))
						.then(Commands.literal("auto").executes(ctx -> setTl(ctx, -1)))
						.then(Commands.argument("tl", IntegerArgumentType.integer(0, 20))
								.executes(ctx -> setTl(ctx, IntegerArgumentType.getInteger(ctx, "tl")))))
				.then(Commands.literal("pcf")
						.executes(ctx -> showPcf(ctx, ctx.getSource().getPlayer()))
						.then(Commands.literal("set")
								.then(Commands.argument("player", EntityArgument.player())
										.then(Commands.argument("value", DoubleArgumentType.doubleArg(0.0D, 20.0D))
												.executes(SummyThreatCommand::setPcf))))
						.then(Commands.argument("player", EntityArgument.player())
								.executes(ctx -> showPcf(ctx, EntityArgument.getPlayer(ctx, "player")))))
				.then(Commands.literal("cdf")
						.executes(SummyThreatCommand::showCdf)
						.then(Commands.argument("value", DoubleArgumentType.doubleArg(0.0D, 20.0D))
								.executes(SummyThreatCommand::setCdf)))
				.then(Commands.literal("free")
						.executes(ctx -> listFreeGroup(ctx, null))
						.then(Commands.literal("list")
								.executes(ctx -> listFreeGroup(ctx, null))
								.then(Commands.argument("group", StringArgumentType.word())
										.suggests((ctx, builder) ->
												net.minecraft.commands.SharedSuggestionProvider.suggest(
														FreeTierSettings.groups(), builder))
										.executes(ctx -> listFreeGroup(ctx,
												StringArgumentType.getString(ctx, "group")))))
						.then(Commands.literal("get")
								.then(Commands.argument("key", StringArgumentType.word())
										.suggests((ctx, builder) ->
												net.minecraft.commands.SharedSuggestionProvider.suggest(
														FreeTierSettings.keys(), builder))
										.executes(SummyThreatCommand::getFree)))
						.then(Commands.literal("set")
								.then(Commands.argument("key", StringArgumentType.word())
										.suggests((ctx, builder) ->
												net.minecraft.commands.SharedSuggestionProvider.suggest(
														FreeTierSettings.keys(), builder))
										.then(Commands.argument("value", StringArgumentType.word())
												.executes(SummyThreatCommand::setFree)))))
				.then(Commands.literal("spawnstats")
						.executes(SummyThreatCommand::showSpawnStats)
						.then(Commands.literal("reset").executes(SummyThreatCommand::resetSpawnStats))
						.then(Commands.literal("on").executes(ctx -> setSpawnAudit(ctx, true)))
						.then(Commands.literal("off").executes(ctx -> setSpawnAudit(ctx, false))))
				.then(Commands.literal("spawn")
						.then(Commands.argument("entity", ResourceLocationArgument.id())
								.then(Commands.argument("tl", IntegerArgumentType.integer(0, 20))
										.executes(ctx -> spawn(ctx, "normal", 20.0D, null))
										.then(Commands.argument("kind", StringArgumentType.word())
												.executes(ctx -> spawn(ctx, StringArgumentType.getString(ctx, "kind"), 20.0D, null))
												.then(Commands.argument("pcf", DoubleArgumentType.doubleArg(0.0D, 20.0D))
														.executes(ctx -> spawn(ctx,
																StringArgumentType.getString(ctx, "kind"),
																DoubleArgumentType.getDouble(ctx, "pcf"), null))
														.then(Commands.argument("ability", StringArgumentType.word())
																.executes(ctx -> spawn(ctx,
																		StringArgumentType.getString(ctx, "kind"),
																		DoubleArgumentType.getDouble(ctx, "pcf"),
																		StringArgumentType.getString(ctx, "ability")))))))))
				.then(Commands.literal("eximus")
						.then(Commands.argument("entity", ResourceLocationArgument.id())
								.executes(ctx -> spawnEximus(ctx, defaultSpawnTl(), null, null))
								.then(Commands.argument("tl", IntegerArgumentType.integer(0, 20))
										.executes(ctx -> spawnEximus(ctx, IntegerArgumentType.getInteger(ctx, "tl"), null, null))
										.then(Commands.argument("first", StringArgumentType.word())
												.executes(ctx -> spawnEximus(ctx,
														IntegerArgumentType.getInteger(ctx, "tl"),
														StringArgumentType.getString(ctx, "first"), null))
												.then(Commands.argument("second", StringArgumentType.word())
														.executes(ctx -> spawnEximus(ctx,
																IntegerArgumentType.getInteger(ctx, "tl"),
																StringArgumentType.getString(ctx, "first"),
																StringArgumentType.getString(ctx, "second"))))))))
				.then(Commands.literal("stats")
						.executes(ctx -> showStats(ctx, ctx.getSource().getPlayer()))
						.then(Commands.argument("player", EntityArgument.player())
								.executes(ctx -> showStats(ctx, EntityArgument.getPlayer(ctx, "player")))))
				.then(Commands.literal("ability")
						.then(Commands.argument("target", EntityArgument.entity())
								.then(Commands.argument("id", StringArgumentType.word())
										.executes(SummyThreatCommand::setAbility))))
				.then(Commands.literal("shield")
						.executes(SummyThreatCommand::showShield)
						.then(Commands.argument("value", IntegerArgumentType.integer(0, 100))
								.executes(ctx -> setShield(ctx, IntegerArgumentType.getInteger(ctx, "value")))))
				.then(Commands.literal("armor")
						.executes(ctx -> showArmor(ctx, ctx.getSource().getPlayer()))
						.then(Commands.argument("player", EntityArgument.player())
								.executes(ctx -> showArmor(ctx, EntityArgument.getPlayer(ctx, "player"))))));
	}

	private static int showTier(CommandContext<CommandSourceStack> ctx) {
		ScalingState.Tier tier = ScalingState.tier();
		int globalTl = ScalingState.globalTl();
		ctx.getSource().sendSuccess(() -> Component.literal(
				"[Summy Threat] 档位 %s（r_hp=%.2f，r_atk=%.2f）｜模式 %s｜全局 TL %s".formatted(
						tier, ScalingState.rHp(), ScalingState.rAtk(),
						Config.ENVIRONMENT_TIER_MODE.get(),
						globalTl == -2 ? "关闭" : globalTl == -1 ? "自动 PCF/CDF" : String.valueOf(globalTl))), false);
		return 1;
	}

	private static int setTier(CommandContext<CommandSourceStack> ctx, String raw) {
		Config.TierMode mode;
		try {
			mode = Config.TierMode.valueOf(raw.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			ctx.getSource().sendFailure(Component.literal(
					"未知档位：%s（可用：auto / none / sr / se / sr_apocrypha / se_sr / full / free）"
							.formatted(raw)));
			return 0;
		}
		Config.ENVIRONMENT_TIER_MODE.set(mode);
		ScalingState.refresh();
		ctx.getSource().sendSuccess(() -> Component.literal(
				"[Summy Threat] 档位模式已设为 %s（实际档位 %s，r_hp=%.2f，r_atk=%.2f）".formatted(
						mode, ScalingState.tier(), ScalingState.rHp(), ScalingState.rAtk())), true);
		return 1;
	}

	/** tier free [hp] [atk]：先写 free 系数，再切换档位；两个参数都可省略。 */
	private static int setFreeTier(CommandContext<CommandSourceStack> ctx, double hp, Double atk) {
		try {
			if (atk == null) {
				FreeTierSettings.set("core.hp", Double.toString(hp));
			} else {
				FreeTierSettings.setCore(hp, atk);
			}
		} catch (IllegalArgumentException exception) {
			ctx.getSource().sendFailure(Component.literal("[Summy Threat] " + exception.getMessage()));
			return 0;
		}
		return setTier(ctx, "free");
	}

	/** free / free list [group]：无分组时列出分组，有分组时列出该组键与当前值。 */
	private static int listFreeGroup(CommandContext<CommandSourceStack> ctx, String group) {
		if (group == null) {
			ctx.getSource().sendSuccess(() -> Component.literal(
					"[Summy Threat] free 配置分组：" + String.join(" / ", FreeTierSettings.groups())), false);
			ctx.getSource().sendSuccess(() -> Component.literal(
					"用法：/summythreat free list <group>；/summythreat free get <key>；/summythreat free set <key> <value>"), false);
			return 1;
		}
		try {
			ctx.getSource().sendSuccess(() -> Component.literal(
					"[Summy Threat] free 分组 " + group + "：" + FreeTierSettings.describeGroup(group)), false);
			for (String line : FreeTierSettings.list(group)) {
				ctx.getSource().sendSuccess(() -> Component.literal("  " + line), false);
			}
			return 1;
		} catch (IllegalArgumentException exception) {
			ctx.getSource().sendFailure(Component.literal("[Summy Threat] " + exception.getMessage()));
			return 0;
		}
	}

	private static int getFree(CommandContext<CommandSourceStack> ctx) {
		String key = StringArgumentType.getString(ctx, "key");
		try {
			ctx.getSource().sendSuccess(() -> Component.literal("[Summy Threat] " + FreeTierSettings.get(key)), false);
			return 1;
		} catch (IllegalArgumentException exception) {
			ctx.getSource().sendFailure(Component.literal("[Summy Threat] " + exception.getMessage()));
			return 0;
		}
	}

	private static int setFree(CommandContext<CommandSourceStack> ctx) {
		String key = StringArgumentType.getString(ctx, "key");
		String value = StringArgumentType.getString(ctx, "value");
		try {
			String message = FreeTierSettings.set(key, value);
			String suffix = ScalingState.tier() == ScalingState.Tier.FREE
					? ""
					: "（已保存；当前不是 free，切换到 free 后生效）";
			ctx.getSource().sendSuccess(() -> Component.literal("[Summy Threat] " + message + suffix), true);
			return 1;
		} catch (IllegalArgumentException exception) {
			ctx.getSource().sendFailure(Component.literal("[Summy Threat] " + exception.getMessage()));
			return 0;
		}
	}

	private static int showTl(CommandContext<CommandSourceStack> ctx) {
		int tl = ScalingState.globalTl();
		ctx.getSource().sendSuccess(() -> Component.literal(
				"[Summy Threat] 全局 TL = %s（档位 %s，接管%s）".formatted(
						tl == -2 ? "关闭" : tl == -1 ? "自动 PCF/CDF" : String.valueOf(tl), ScalingState.tier(),
						ScalingState.enabled() ? "生效" : "未生效")), false);
		return 1;
	}

	private static int setTl(CommandContext<CommandSourceStack> ctx, int tl) {
		ScalingState.setGlobalTl(tl);
		String suffix = tl == -2
				? "新刷的敌对生物不会数值化"
				: tl == -1
						? "新刷的敌对生物将按 PCF/CDF 自动计算 TL"
						: "新刷的敌对生物将按此 TL 数值化";
		if (tl >= 0 && ScalingState.tier() == ScalingState.Tier.NONE) {
			suffix = "注意：当前档位为 none（短路），怪物侧不会数值化";
		}
		String finalSuffix = suffix;
		ctx.getSource().sendSuccess(() -> Component.literal(
				"[Summy Threat] 全局 TL 已设为 %s；%s".formatted(
						tl == -2 ? "完全关闭（不再接管新刷的怪）"
								: tl == -1 ? "自动 PCF/CDF" : String.valueOf(tl),
						finalSuffix)), true);
		return 1;
	}

	private static int showPcf(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
		if (player == null) {
			ctx.getSource().sendFailure(Component.literal("当前上下文没有玩家；请用 /summythreat pcf <玩家>"));
			return 0;
		}
		PcfManager.Snapshot s = PcfManager.snapshot(player);
		double normal = PcfManager.selectedPcf(player.serverLevel(), player.position(), MonsterScaler.Kind.NORMAL);
		double eximus = PcfManager.selectedPcf(player.serverLevel(), player.position(), MonsterScaler.Kind.EXIMUS);
		double leader = PcfManager.selectedPcf(player.serverLevel(), player.position(), MonsterScaler.Kind.LEADER);
		ctx.getSource().sendSuccess(() -> Component.literal(
				"[Summy Threat] PCF %s（原始 %s）｜装备 %s｜Boss %s｜探索 %s｜资源 %s｜重置锁 %s"
						.formatted(fmt(s.value()), fmt(s.calc()), fmt(s.equipment()), fmt(s.boss()),
								fmt(s.exploration()), fmt(s.resource()), s.locked() ? "生效" : "无")
						+ "｜选中：普通 " + fmt(normal) + " / 卓越 " + fmt(eximus) + " / 首领 " + fmt(leader)), false);
		return 1;
	}

	private static int setPcf(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
		double value = DoubleArgumentType.getDouble(ctx, "value");
		PcfManager.setValue(player, value);
		ctx.getSource().sendSuccess(() -> Component.literal(
				"[Summy Threat] PCF 已设为 %s（%s）".formatted(fmt(value), player.getGameProfile().getName())), true);
		return 1;
	}

	private static int showCdf(CommandContext<CommandSourceStack> ctx) {
		ServerLevel level = ctx.getSource().getLevel();
		BlockPos pos = BlockPos.containing(ctx.getSource().getPosition());
		CdfManager.Snapshot s = CdfManager.snapshot(level, pos);
		SpawnRegion region = SpawnRegion.detect(level, ctx.getSource().getPosition(), MobSpawnType.NATURAL);
		ctx.getSource().sendSuccess(() -> Component.literal(
				"[Summy Threat] CDF %s（目标 %s，峰值 %s）｜区域 %s（×%s，首领上限 %d）｜击杀加成 %s｜死亡惩罚 %s｜玩家 %s"
						.formatted(fmt(s.value()), fmt(s.target()), fmt(s.peakPcf()), region.id(),
								fmt(region.multiplier()), region.leaderCap(), fmt(s.killBonus()),
								s.deathPenaltyUntil() > level.getGameTime() ? "有效" : "无",
								s.hasPlayers() ? "在区块内" : "无")), false);
		return 1;
	}

	private static int setCdf(CommandContext<CommandSourceStack> ctx) {
		ServerLevel level = ctx.getSource().getLevel();
		BlockPos pos = BlockPos.containing(ctx.getSource().getPosition());
		double value = DoubleArgumentType.getDouble(ctx, "value");
		CdfManager.setCurrent(level, pos, value);
		ctx.getSource().sendSuccess(() -> Component.literal(
				"[Summy Threat] 当前区块 CDF 已设为 %s".formatted(fmt(value))), true);
		return 1;
	}

	private static int showSpawnStats(CommandContext<CommandSourceStack> ctx) {
		ctx.getSource().sendSuccess(() -> Component.literal("[Summy Threat] 刷怪审计：" + SpawnAudit.summary()), false);
		return 1;
	}

	private static int resetSpawnStats(CommandContext<CommandSourceStack> ctx) {
		SpawnAudit.reset();
		ctx.getSource().sendSuccess(() -> Component.literal("[Summy Threat] 刷怪审计计数已清空"), true);
		return 1;
	}

	private static int setSpawnAudit(CommandContext<CommandSourceStack> ctx, boolean enabled) {
		SpawnAudit.setRecording(enabled);
		ctx.getSource().sendSuccess(() -> Component.literal(
				"[Summy Threat] 刷怪审计记录已" + (enabled ? "开启" : "关闭")), true);
		return 1;
	}

	private static int spawn(CommandContext<CommandSourceStack> ctx, String kindRaw, double pcf, String ability) {
		return spawnInternal(ctx.getSource(), ResourceLocationArgument.getId(ctx, "entity"),
				IntegerArgumentType.getInteger(ctx, "tl"), kindRaw, pcf, ability, null, false);
	}

	/**
	 * 调试：直接生成卓越者；tl 缺省时用当前全局 TL，未开启则 20。
	 * 前两个可选词按“原型 / 能力”自动识别，因此以下写法都成立：
	 * {@code flame}、{@code none flame}、{@code flame none}、{@code flame swift}。
	 */
	private static int spawnEximus(CommandContext<CommandSourceStack> ctx, int tl, String first, String second) {
		String ability = null;
		boolean suppressAbility = false;
		EximusArchetype archetype = null;
		boolean clearArchetype = false;
		for (String token : new String[]{first, second}) {
			if (token == null) {
				continue;
			}
			String value = token.toLowerCase(Locale.ROOT);
			if ("none".equals(value)) {
				if (!suppressAbility) {
					suppressAbility = true;
					continue;
				}
				if (!clearArchetype) {
					clearArchetype = true;
					continue;
				}
				return fail(ctx, "none 重复过多；格式：eximus <实体> [tl] [原型|能力] [原型|能力]");
			}
			if ("clear".equals(value)) {
				if (clearArchetype) {
					return fail(ctx, "clear 重复");
				}
				clearArchetype = true;
				continue;
			}
			if ("random".equals(value)) {
				continue; // 保持原型随机
			}
			EximusArchetype foundArchetype = EximusArchetype.byId(value);
			if (foundArchetype != null) {
				if (archetype != null || clearArchetype) {
					return fail(ctx, "原型重复指定：" + token);
				}
				archetype = foundArchetype;
				continue;
			}
			AbilityId foundAbility = AbilityId.byId(value);
			if (foundAbility != null) {
				if (ability != null || suppressAbility) {
					return fail(ctx, "TL 能力重复或被 none 抑制：" + token);
				}
				ability = foundAbility.id();
				continue;
			}
			return fail(ctx, "未知原型/能力：" + token
					+ "（原型：flame/frost/poison/storm/shock/guardian/vampire/magnet；能力见 /summythreat ability）");
		}
		return spawnInternal(ctx.getSource(), ResourceLocationArgument.getId(ctx, "entity"),
				tl, "eximus", 20.0D, suppressAbility ? "none" : ability, archetype, clearArchetype);
	}

	private static int fail(CommandContext<CommandSourceStack> ctx, String message) {
		ctx.getSource().sendFailure(Component.literal(message));
		return 0;
	}

	private static int defaultSpawnTl() {
		int tl = ScalingState.globalTl();
		return tl >= 0 ? tl : 20;
	}

	private static String fmt(double value) {
		return String.format("%.2f", value);
	}

	private static int spawnInternal(CommandSourceStack source, ResourceLocation entityId, int tl,
			String kindRaw, double pcf, String ability, EximusArchetype archetype, boolean clearArchetype) {
		EntityType<?> type = EntityType.byString(entityId.toString()).orElse(null);
		if (type == null) {
			source.sendFailure(Component.literal("未知实体 ID：" + entityId));
			return 0;
		}
		ServerLevel level = source.getLevel();
		Entity created = type.create(level);
		if (created == null) {
			source.sendFailure(Component.literal("无法创建实体：" + entityId));
			return 0;
		}
		if (!(created instanceof LivingEntity living) || !MonsterScaler.isScalable(living)) {
			source.sendFailure(Component.literal("该实体不在接管范围内（只处理敌对生物与末影龙）：" + entityId));
			return 0;
		}

		String kind = kindRaw.toLowerCase(Locale.ROOT);
		if ("leader".equals(kind) && !MonsterScaler.isLeaderEligible(living)) {
			source.sendFailure(Component.literal("区域首领只能是 zombie / piglin_brute / elder_guardian"));
			return 0;
		}
		if (!"normal".equals(kind) && !"eximus".equals(kind) && !"leader".equals(kind)) {
			source.sendFailure(Component.literal("kind 只支持 normal / eximus / leader"));
			return 0;
		}

		Vec3 pos = source.getPosition();
		created.moveTo(pos.x, pos.y, pos.z, source.getRotation().y, 0.0F);
		if (created instanceof Mob mob) {
			mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()), MobSpawnType.COMMAND,
					null, null);
			mob.setPersistenceRequired();
		}

		if ("eximus".equals(kind)) {
			created.getPersistentData().putBoolean(MonsterScaler.TAG_EXIMUS, true);
			if (archetype != null) {
				created.getPersistentData().putString(MonsterScaler.TAG_ARCHETYPE, archetype.id());
			}
		} else if ("leader".equals(kind)) {
			created.getPersistentData().putBoolean(MonsterScaler.TAG_LEADER, true);
			created.getPersistentData().putDouble(MonsterScaler.TAG_PCF, pcf);
		}

		// 先数值化（此时实体还没进世界，避免 join 事件重复处理），再加进世界
		MonsterScaler.scaleNow(living, tl);
		if ("eximus".equals(kind) && clearArchetype) {
			created.getPersistentData().remove(MonsterScaler.TAG_ARCHETYPE);
		}
		// 能力覆盖（测试用）：none = 抑制抽取；<id> = 强制单个
		if (ability != null) {
			if ("none".equalsIgnoreCase(ability)) {
				AbilityEffects.setAbilities(living, tl, "");
			} else {
				AbilityId id = AbilityId.byId(ability);
				if (id == null) {
					source.sendFailure(Component.literal("未知能力 id：" + ability));
					return 0;
				}
				AbilityEffects.setAbilities(living, tl, id.id());
			}
		}
		level.addFreshEntity(created);

		source.sendSuccess(() -> Component.literal("[Summy Threat] 已召唤 %s：".formatted(entityId))
				.append(MonsterScaler.describe(living)), true);
		return 1;
	}

	/** 给已有实体挂载单个能力（clear = 清空）。 */
	private static int setAbility(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		Entity target = EntityArgument.getEntity(ctx, "target");
		String raw = StringArgumentType.getString(ctx, "id");
		if (!(target instanceof LivingEntity living)) {
			ctx.getSource().sendFailure(Component.literal("目标必须是生物"));
			return 0;
		}
		if (!living.getPersistentData().getBoolean(MonsterScaler.TAG_SCALED)) {
			ctx.getSource().sendFailure(Component.literal("目标尚未被数值化（先 /summythreat spawn 或开启全局 TL）"));
			return 0;
		}
		int tl = living.getPersistentData().getInt(MonsterScaler.TAG_TL);
		if ("clear".equalsIgnoreCase(raw)) {
			AbilityEffects.setAbilities(living, tl, "");
		} else {
			AbilityId id = AbilityId.byId(raw);
			if (id == null) {
				ctx.getSource().sendFailure(Component.literal("未知能力 id：" + raw));
				return 0;
			}
			AbilityEffects.setAbilities(living, tl, id.id());
		}
		ctx.getSource().sendSuccess(() -> Component.literal("[Summy Threat] ")
				.append(MonsterScaler.describe(living)), true);
		return 1;
	}

	private static int showShield(CommandContext<CommandSourceStack> ctx) {
		ServerPlayer player = ctx.getSource().getPlayer();
		if (player == null) {
			ctx.getSource().sendFailure(Component.literal("该命令必须由玩家执行"));
			return 0;
		}
		ctx.getSource().sendSuccess(() -> Component.literal(
				"[Summy Threat] 玩家卓越护盾 = %.0f / %.0f".formatted(
						PlayerEximusShield.current(player), PlayerEximusShield.MAX_SHIELD)), false);
		return 1;
	}

	private static int setShield(CommandContext<CommandSourceStack> ctx, int value) {
		ServerPlayer player = ctx.getSource().getPlayer();
		if (player == null) {
			ctx.getSource().sendFailure(Component.literal("该命令必须由玩家执行"));
			return 0;
		}
		PlayerEximusShield.set(player, value);
		ctx.getSource().sendSuccess(() -> Component.literal(
				"[Summy Threat] 玩家卓越护盾已设为 %.0f / %.0f".formatted(
						PlayerEximusShield.current(player), PlayerEximusShield.MAX_SHIELD)), true);
		return 1;
	}

	private static int showStats(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
		if (player == null) {
			ctx.getSource().sendFailure(Component.literal("该命令需要指定玩家（控制台请用 /summythreat stats <玩家>）"));
			return 0;
		}
		CombatMeter.Stats s = CombatMeter.stats(player);
		int now = ctx.getSource().getServer().getTickCount();
		double windowDps = CombatMeter.windowDps(s, now);
		double avgTaken = s.takenCount > 0 ? s.takenSum / s.takenCount : 0.0D;
		ctx.getSource().sendSuccess(() -> Component.literal("""
				[Summy Threat] 计量（%s）
				  击杀 TL 怪：%d 次；最近一次：生命 %s，耗时 %s s，有效 DPS %s，无视护甲占比 %s，护盾吸收 %s
				  窗口 DPS（自上次首击起）：%s
				  承受伤害：最大 %s / 平均 %s（%d 次）；被 TL 怪击杀 %d 次""".formatted(
				player.getGameProfile().getName(), s.kills,
				CombatMeter.fmt(s.lastKillHp),
				s.lastKillSeconds < 0 ? "—" : CombatMeter.fmt(s.lastKillSeconds),
				s.lastKillDps < 0 ? "—" : CombatMeter.fmt(s.lastKillDps),
				s.lastKillSeconds < 0 ? "—" : CombatMeter.pct(s.lastKillBypassShare),
				s.lastKillSeconds < 0 ? "—" : CombatMeter.fmt(s.lastKillShield),
				windowDps < 0 ? "—" : CombatMeter.fmt(windowDps),
				CombatMeter.fmt(s.takenMax), CombatMeter.fmt(avgTaken), s.takenCount, s.deaths)), false);
		return 1;
	}

	/** 玩家护甲重填的验收命令：打印 护甲 / 韧性 / EA / 减伤，以及四件装备逐件的护甲贡献 */
	private static int showArmor(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
		if (player == null) {
			ctx.getSource().sendFailure(Component.literal("该命令需要指定玩家（控制台请用 /summythreat armor <玩家>）"));
			return 0;
		}
		double armor = player.getAttributeValue(Attributes.ARMOR);
		double toughness = player.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
		double ea = ArmorDamageFormula.effectiveArmor(armor, toughness, Config.SYNERGY_DIVISOR.get());
		double dr = ArmorDamageFormula.playerReduction(ea, Config.PLAYER_K.get(),
				Config.PLAYER_MIN.get(), Config.PLAYER_MAX.get());

		EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
		String[] names = {"头盔", "胸甲", "护腿", "靴子"};
		StringBuilder pieces = new StringBuilder();
		for (int i = 0; i < slots.length; i++) {
			ItemStack stack = player.getItemBySlot(slots[i]);
			double value = 0.0D;
			for (AttributeModifier modifier : stack.getAttributeModifiers(slots[i]).get(Attributes.ARMOR)) {
				value += modifier.getAmount();
			}
			pieces.append("%n  %s %s → 护甲 %s".formatted(names[i],
					stack.isEmpty() ? "（空）" : stack.getHoverName().getString(),
					CombatMeter.fmt(value)));
		}

		ctx.getSource().sendSuccess(() -> Component.literal("""
				[Summy Threat] 玩家护甲（%s）｜重填开关 %s
				  护甲 %s / 韧性 %s → EA %s → 玩家侧减伤 %s%s""".formatted(
				player.getGameProfile().getName(),
				Config.ENABLE_PLAYER_ARMOR_REFILL.get() ? "开" : "关",
				CombatMeter.fmt(armor), CombatMeter.fmt(toughness), CombatMeter.fmt(ea),
				CombatMeter.pct(dr), pieces.toString())), false);
		return 1;
	}
}
