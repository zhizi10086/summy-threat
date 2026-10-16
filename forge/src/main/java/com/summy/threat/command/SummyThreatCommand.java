package com.summy.threat.command;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.summy.threat.Config;
import com.summy.threat.scaling.CombatMeter;
import com.summy.threat.scaling.MonsterScaler;
import com.summy.threat.scaling.ScalingState;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
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
										.executes(ctx -> spawn(ctx, "normal", 20.0D))
										.then(Commands.argument("kind", StringArgumentType.word())
												.executes(ctx -> spawn(ctx, StringArgumentType.getString(ctx, "kind"), 20.0D))
												.then(Commands.argument("pcf", DoubleArgumentType.doubleArg(0.0D, 20.0D))
														.executes(ctx -> spawn(ctx,
																StringArgumentType.getString(ctx, "kind"),
																DoubleArgumentType.getDouble(ctx, "pcf"))))))))
				.then(Commands.literal("stats")
						.executes(ctx -> showStats(ctx, ctx.getSource().getPlayer()))
						.then(Commands.argument("player", EntityArgument.player())
								.executes(ctx -> showStats(ctx, self(EntityArgument.getPlayer(ctx, "player")))))));
	}

	private static ServerPlayer self(ServerPlayer player) {
		return player;
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

	private static int spawn(CommandContext<CommandSourceStack> ctx, String kindRaw, double pcf) {
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
		level.addFreshEntity(created);

		source.sendSuccess(() -> Component.literal("[Summy Threat] 已召唤 %s：%s".formatted(
				entityId, MonsterScaler.describe(living).getString())), true);
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
}
