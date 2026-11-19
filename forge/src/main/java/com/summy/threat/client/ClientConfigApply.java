package com.summy.threat.client;

import com.summy.threat.attribute.AttributeCaps;
import net.minecraft.client.Minecraft;

/** 将远程配置对应的属性上限修改交给客户端线程。 */
public final class ClientConfigApply {
	private ClientConfigApply() {
	}

	public static void enqueue() {
		Minecraft.getInstance().execute(AttributeCaps::apply);
	}
}
