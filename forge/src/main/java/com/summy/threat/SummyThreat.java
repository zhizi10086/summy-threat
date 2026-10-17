package com.summy.threat;

import com.summy.threat.armor.PlayerArmorRefill;
import com.summy.threat.attribute.AttributeCaps;
import com.summy.threat.command.SummyThreatCommand;
import com.summy.threat.ability.AbilityEffects;
import com.summy.threat.eximus.EximusSpawner;
import com.summy.threat.eximus.Overguard;
import com.summy.threat.item.ModItems;
import com.summy.threat.scaling.CombatMeter;
import com.summy.threat.scaling.MonsterScaler;
import com.summy.threat.scaling.ScalingState;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 模组入口：Summy Threat。
 *
 * <p>已实现：属性上限抬升、自定义护甲减伤公式、基础数值表（怪物侧 TL 缩放 + 玩家护甲重填）、
 * 生态分档（§5.0）与调试 / 测量命令。技能池（卓越者 / TL / 领袖 / Boss 能力）与 PCF/CDF 尚未实现。
 * 配置走 Forge 原生 SERVER 档，Forge 会把服务端的值自动同步给客户端，双端口径天然一致。
 */
@Mod(SummyThreat.MOD_ID)
public class SummyThreat {
	public static final String MOD_ID = "summy_threat";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	/**
	 * 1.20.1 的推荐写法：由 Forge 注入 {@link FMLJavaModLoadingContext}，
	 * 不用已弃用（标记为待删除）的 {@code FMLJavaModLoadingContext.get()} / {@code ModLoadingContext.get()}。
	 */
	public SummyThreat(FMLJavaModLoadingContext context) {
		// SERVER 档配置：文件生成在存档的 serverconfig/summy_threat-server.toml
		context.registerConfig(ModConfig.Type.SERVER, Config.SPEC);

		IEventBus modBus = context.getModEventBus();
		// 物品注册（回响下界合金四件 + 卓越者精华）
		ModItems.ITEMS.register(modBus);
		// 创造页注入：BuildCreativeModeTabContentsEvent 是 **模组总线** 事件（IModBusEvent），
		// 挂在 Forge 总线上不会被调用（第一版就踩了这个坑，物品注册成功但创造页里找不到）。
		modBus.addListener(SummyThreat::onBuildCreativeTabContents);
		// 配置的"首次加载 / 客户端收到服务端同步 / 运行期重载"都会触发 ModConfigEvent，
		// 在这里统一应用上限：一处入口同时覆盖服务端与客户端。
		modBus.addListener(SummyThreat::onConfigEvent);

		// 游戏事件总线：数值化 / 计量 / 命令 / 玩家护甲重填 / 开服时判定档位
		MinecraftForge.EVENT_BUS.register(SummyThreat.class);
		MinecraftForge.EVENT_BUS.register(MonsterScaler.class);
		MinecraftForge.EVENT_BUS.register(CombatMeter.class);
		MinecraftForge.EVENT_BUS.register(AbilityEffects.class);
		MinecraftForge.EVENT_BUS.register(Overguard.class);
		MinecraftForge.EVENT_BUS.register(EximusSpawner.class);
		MinecraftForge.EVENT_BUS.register(SummyThreatCommand.class);
		MinecraftForge.EVENT_BUS.register(PlayerArmorRefill.class);
	}

	private static void onConfigEvent(ModConfigEvent event) {
		// 只认自己的配置，别把其它模组的配置加载/重载也当成应用时机
		if (!MOD_ID.equals(event.getConfig().getModId())) {
			return;
		}
		AttributeCaps.apply();
		// 数值表 / 档位 / 全局 TL 都从配置读，配置一变就刷新
		ScalingState.refresh();
	}

	/** 开服时判定生态档位并写一条日志，便于实机核对（尤其是 ∅ 档短路） */
	@SubscribeEvent
	public static void onServerStarted(ServerStartedEvent event) {
		ScalingState.refresh();
		ScalingState.Tier tier = ScalingState.tier();
		if (tier == ScalingState.Tier.NONE) {
			LOGGER.info("[Summy Threat] 生态档位：none（三件套均未装，怪物侧整体短路）；全局 TL {}",
					ScalingState.globalTl() < 0 ? "关闭" : ScalingState.globalTl());
		} else {
			LOGGER.info("[Summy Threat] 生态档位：{}（r_hp={}，r_atk={}）；全局 TL {}", tier,
					ScalingState.rHp(), ScalingState.rAtk(),
					ScalingState.globalTl() < 0 ? "关闭（可用 /summythreat tl <0-20> 开启）" : ScalingState.globalTl());
		}
	}

	/** 把本轮新物品放进对应原版创造页（护甲 → 战斗，精华 → 材料），方便测试期取用 */
	public static void onBuildCreativeTabContents(BuildCreativeModeTabContentsEvent event) {
		if (event.getTabKey() == CreativeModeTabs.COMBAT) {
			event.accept(ModItems.ECHO_HELMET.get());
			event.accept(ModItems.ECHO_CHESTPLATE.get());
			event.accept(ModItems.ECHO_LEGGINGS.get());
			event.accept(ModItems.ECHO_BOOTS.get());
			if (!loggedCombatTab) {
				loggedCombatTab = true;
				LOGGER.info("[Summy Threat] 已向原版「战斗」创造页注入 4 件回响下界合金护甲");
			}
		} else if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
			event.accept(ModItems.EXIMUS_ESSENCE.get());
			if (!loggedIngredientsTab) {
				loggedIngredientsTab = true;
				LOGGER.info("[Summy Threat] 已向原版「材料」创造页注入卓越者精华");
			}
		}
	}

	/** 创造页注入只打一次日志（避免每次打开创造界面都刷屏） */
	private static boolean loggedCombatTab;
	private static boolean loggedIngredientsTab;
}
