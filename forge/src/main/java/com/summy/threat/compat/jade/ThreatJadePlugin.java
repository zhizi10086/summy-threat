package com.summy.threat.compat.jade;

import net.minecraft.world.entity.Mob;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/**
 * Jade 兼容插件（可选依赖）：只在 Jade 存在时由 Jade 扫描 {@code @WailaPlugin} 加载，
 * 主代码不引用本包，因此未安装 Jade 时零影响。
 */
@WailaPlugin("summy_threat")
public class ThreatJadePlugin implements IWailaPlugin {
	@Override
	public void register(IWailaCommonRegistration registration) {
		// 服务端：把 TL / 类别 /（未来的）能力名写进 Jade 的 serverData
		registration.registerEntityDataProvider(EntityAbilityDataProvider.INSTANCE, Mob.class);
	}

	@Override
	public void registerClient(IWailaClientRegistration registration) {
		// 客户端：在 Jade 提示框里显示这些字段
		registration.registerEntityComponent(EntityAbilityTooltip.INSTANCE, Mob.class);
	}
}
