package com.summy.threat.legendary;

/** 单个传奇动作的后续实现入口；基础框架阶段允许暂未注册。 */
@FunctionalInterface
public interface LegendaryActionHandler {
	void start(LegendaryActionContext context);
}
