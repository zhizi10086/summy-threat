package com.summy.threat;

import com.summy.threat.attribute.AttributeCaps;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 模组入口：Summy Threat。
 *
 * <p>本次只实现"属性上限抬升"：把 generic.armor 与 generic.max_health 的上限改由配置驱动。
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
		// 配置的"首次加载 / 客户端收到服务端同步 / 运行期重载"都会触发 ModConfigEvent，
		// 在这里统一应用上限：一处入口同时覆盖服务端与客户端。
		modBus.addListener(SummyThreat::onConfigEvent);
	}

	private static void onConfigEvent(ModConfigEvent event) {
		// 只认自己的配置，别把其它模组的配置加载/重载也当成应用时机
		if (!MOD_ID.equals(event.getConfig().getModId())) {
			return;
		}
		AttributeCaps.apply();
	}
}
