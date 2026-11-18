package com.summy.threat.legendary;

/** 单个传奇动作的运行实例工厂。 */
@FunctionalInterface
public interface LegendaryActionHandler {
	LegendaryActionInstance start(LegendaryActionContext context);
}
