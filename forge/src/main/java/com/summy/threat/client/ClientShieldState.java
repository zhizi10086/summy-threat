package com.summy.threat.client;

/** 客户端缓存的玩家卓越护盾值，只用于 HUD 显示。 */
public final class ClientShieldState {
	private static double value;

	private ClientShieldState() {
	}

	public static void set(double next) {
		value = Math.max(0.0D, next);
	}

	public static double get() {
		return value;
	}

	public static void clear() {
		value = 0.0D;
	}
}
