package com.summy.threat.command;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.summy.threat.Config;
import com.summy.threat.ability.AbilityEffects;
import com.summy.threat.ability.AbilityId;
import com.summy.threat.combat.ArmorDamageFormula;
import com.summy.threat.scaling.CombatMeter;
import com.summy.threat.scaling.MonsterScaler;
import com.summy.threat.scaling.ScalingState;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.commands.arguments.ResourceLocationArgument;
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
 *     <li>{@code tier [mode]}：查看或强制生态档位（auto / none / sr / se / sr_apocrypha / se_sr / full）。</li>
 *     <li>{@code tl [0-20|off]}：查看或设置全局 TL（临时驱动，正式 TL 由后续 PCF/CDF 提供）。</li>
 *     <li>{@code spawn <实体ID> <tl> [eximus|leader] [pcf]}：定点召唤一只已数值化的怪。</li>
 *     <li>{@code stats [玩家]}：查看计量结果（有效 DPS / 击杀耗时 / 无视护甲占比 / 承伤）。</li>
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
						.then(Commands.argument("mode", StringArgumentType.word())
								.executes(ctx -> setTier(ctx, StringArgumentType.getString(ctx, "mode")))))
				.then(Commands.literal("tl")
						.executes(SummyThreatCommand::showTl)
						.then(Commands.literal("off").executes(ctx -> setTl(ctx, -1)))
						.then(Commands.argument("tl", IntegerArgumentType.integer(0, 20))
								.executes(ctx -> setTl(ctx, IntegerArgumentType.getInteger(ctx, "tl")))))
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
				.then(Commands.literal("stats")
						.executes(ctx -> showStats(ctx, ctx.getSource().getPlayer()))
						.then(Commands.argument("player", EntityArgument.player())
								.executes(ctx -> showStats(ctx, EntityArgument.getPlayer(ctx, "player")))))
				.then(Commands.literal("ability")
						.then(Commands.argument("target", EntityArgument.entity())
								.then(Commands.argument("id", StringArgumentType.word())
										.executes(SummyThreatCommand::setAbility))))
				.then(Commands.literal("armor")
						.executes(ctx -> showArmor(ctx, ctx.getSource().getPlayer()))
						.then(Commands.argument("player", EntityArgument.player())
								.executes(ctx -> showArmor(ctx, EntityArgument.getPlayer(ctx, "player"))))));
	}

	private static int showTier(CommandContext<CommandSourceStack> ctx) {
		ScalingState.Tier tier = ScalingState.tier();
		ctx.getSource().sendSuccess(() -> Component.literal(
				"[Summy Threat] 档位 %s（r_hp=%.2f，r_atk=%.2f）｜模式 %s｜全局 TL %s".formatted(
						tier, ScalingState.rHp(), ScalingState.rAtk(),
						Config.ENVIRONMENT_TIER_MODE.get(),
						ScalingState.globalTl() < 0 ? "关闭" : String.valueOf(ScalingState.globalTl()))), false);
		return 1;
	}

	private static int setTier(CommandContext<CommandSourceStack> ctx, String raw) {
		Config.TierMode mode;
		try {
			mode = Config.TierMode.valueOf(raw.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			ctx.getSource().sendFailure(Component.literal(
					"未知档位：%s（可用：auto / none / sr / se / sr_apocrypha / se_sr / full）".formatted(raw)));
			return 0;
		}
		Config.ENVIRONMENT_TIER_MODE.set(mode);
		ScalingState.refresh();
		ctx.getSource().sendSuccess(() -> Component.literal(
				"[Summy Threat] 档位模式已设为 %s（实际档位 %s，r_hp=%.2f，r_atk=%.2f）".formatted(
						mode, ScalingState.tier(), ScalingState.rHp(), ScalingState.rAtk())), true);
		return 1;
	}

	private static int showTl(CommandContext<CommandSourceStack> ctx) {
		int tl = ScalingState.globalTl();
		ctx.getSource().sendSuccess(() -> Component.literal(
				"[Summy Threat] 全局 TL = %s（档位 %s，接管%s）".formatted(
						tl < 0 ? "关闭" : String.valueOf(tl), ScalingState.tier(),
						ScalingState.enabled() ? "生效" : "未生效")), false);
		return 1;
	}

	private static int setTl(CommandContext<CommandSourceStack> ctx, int tl) {
		ScalingState.setGlobalTl(tl);
		ctx.getSource().sendSuccess(() -> Component.literal(
				"[Summy Threat] 全局 TL 已设为 %s；%s".formatted(
						tl < 0 ? "关闭（不再接管新刷的怪）" : String.valueOf(tl),
						tl >= 0 && ScalingState.tier() == ScalingState.Tier.NONE
								? "注意：当前档位为 none（短路），怪物侧不会数值化" : "新刷的敌对生物将按此 TL 数值化")), true);
		return 1;
	}

	private static int spawn(CommandContext<CommandSourceStack> ctx, String kindRaw, double pcf, String ability) {
		CommandSourceStack source = ctx.getSource();
		ResourceLocation entityId = ResourceLocationArgument.getId(ctx, "entity");
		int tl = IntegerArgumentType.getInteger(ctx, "tl");

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
		} else if ("leader".equals(kind)) {
			created.getPersistentData().putBoolean(MonsterScaler.TAG_LEADER, true);
			created.getPersistentData().putDouble(MonsterScaler.TAG_PCF, pcf);
		}

		// 先数值化（此时实体还没进世界，避免 join 事件重复处理），再加进世界
		MonsterScaler.scaleNow(living, tl);
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
				  击杀 TL 怪：%d 次；最近一次：生命 %s，耗时 %s s，有效 DPS %s，无视护甲占比 %s
				  窗口 DPS（自上次首击起）：%s
				  承受伤害：最大 %s / 平均 %s（%d 次）；被 TL 怪击杀 %d 次""".formatted(
				player.getGameProfile().getName(), s.kills,
				CombatMeter.fmt(s.lastKillHp),
				s.lastKillSeconds < 0 ? "—" : CombatMeter.fmt(s.lastKillSeconds),
				s.lastKillDps < 0 ? "—" : CombatMeter.fmt(s.lastKillDps),
				s.lastKillSeconds < 0 ? "—" : CombatMeter.pct(s.lastKillBypassShare),
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
