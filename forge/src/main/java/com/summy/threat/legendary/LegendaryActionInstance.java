package com.summy.threat.legendary;

/** 单个已启动传奇动作实例；返回 false 表示当前 tick 后结束。 */
@FunctionalInterface
public interface LegendaryActionInstance {
	boolean tick(LegendaryActionContext context, int age);

	default void stop(LegendaryActionContext context, StopReason reason) {
	}

	enum StopReason {
		COMPLETED,
		OWNER_INVALID,
		TARGET_INVALID,
		SERVER_STOPPED,
		CANCELLED
	}
}
