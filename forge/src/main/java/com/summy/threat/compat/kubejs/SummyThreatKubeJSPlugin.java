package com.summy.threat.compat.kubejs;

import com.summy.threat.SummyThreat;
import com.summy.threat.api.SummyThreatApi;
import com.summy.threat.api.event.ThreatAbilityChangedEvent;
import com.summy.threat.api.event.ThreatArchetypeChangedEvent;
import com.summy.threat.api.event.ThreatConfigChangedEvent;
import com.summy.threat.api.event.ThreatScalePostEvent;
import com.summy.threat.api.event.ThreatScalePreEvent;
import com.summy.threat.compat.kubejs.event.ThreatAbilityChangedEventJS;
import com.summy.threat.compat.kubejs.event.ThreatArchetypeChangedEventJS;
import com.summy.threat.compat.kubejs.event.ThreatConfigChangedEventJS;
import com.summy.threat.compat.kubejs.event.ThreatScalePostEventJS;
import com.summy.threat.compat.kubejs.event.ThreatScalePreEventJS;
import com.summy.threat.network.ModNetwork;
import dev.latvian.mods.kubejs.KubeJSPlugin;
import dev.latvian.mods.kubejs.event.EventGroup;
import dev.latvian.mods.kubejs.event.EventHandler;
import dev.latvian.mods.kubejs.script.BindingsEvent;
import dev.latvian.mods.kubejs.script.ScriptType;
import dev.latvian.mods.kubejs.util.ClassFilter;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

/** KubeJS 可选插件；未安装 KubeJS 时本类不会被加载。 */
public final class SummyThreatKubeJSPlugin extends KubeJSPlugin {
	public static final EventGroup EVENTS = EventGroup.of("SummyThreatEvents");
	public static final EventHandler SCALE_PRE = EVENTS.server("preScale",
			() -> ThreatScalePreEventJS.class);
	public static final EventHandler SCALE_POST = EVENTS.server("postScale",
			() -> ThreatScalePostEventJS.class);
	public static final EventHandler ABILITY_CHANGED = EVENTS.server("abilityChanged",
			() -> ThreatAbilityChangedEventJS.class);
	public static final EventHandler ARCHETYPE_CHANGED = EVENTS.server("archetypeChanged",
			() -> ThreatArchetypeChangedEventJS.class);
	public static final EventHandler CONFIG_CHANGED = EVENTS.server("configChanged",
			() -> ThreatConfigChangedEventJS.class);

	@Override
	public void init() {
		SummyThreat.LOGGER.info("[Summy Threat] KubeJS API 已绑定：SummyThreat / SummyThreatConfig");
	}

	@Override
	public void registerEvents() {
		EVENTS.register();
		MinecraftForge.EVENT_BUS.register(this);
	}

	@Override
	public void clearCaches() {
		// KubeJS 不向插件传入脚本类型；只在服务端脚本重载栈中清理，避免客户端重载误删服务器定义。
		if (isServerScriptReload()) {
			SummyThreatApi.clearCustomContent();
		}
	}

	private static boolean isServerScriptReload() {
		return StackWalker.getInstance().walk(frames -> frames.anyMatch(frame ->
				"dev.latvian.mods.kubejs.server.ServerScriptManager".equals(frame.getClassName())));
	}

	@Override
	public void onServerReload() {
		ModNetwork.broadcastRegistry(ServerLifecycleHooks.getCurrentServer());
	}

	@Override
	public void registerBindings(BindingsEvent event) {
		if (event.getType() == ScriptType.SERVER) {
			event.add("SummyThreat", SummyThreatBindings.class);
			event.add("SummyThreatConfig", SummyThreatConfigBindings.class);
		} else if (event.getType() == ScriptType.CLIENT) {
			event.add("SummyThreat", SummyThreatClientBindings.class);
		}
	}

	@Override
	public void registerClasses(ScriptType type, ClassFilter filter) {
		filter.allow("com.summy.threat.api");
	}

	@SubscribeEvent
	public void onScalePre(ThreatScalePreEvent event) {
		ThreatScalePreEventJS js = new ThreatScalePreEventJS(event);
		SCALE_PRE.post(js);
		if (js.canceled) {
			event.setCanceled(true);
		}
		event.setTl(js.tl);
		event.setKind(SummyThreatApi.parseKind(js.kind));
	}

	@SubscribeEvent
	public void onScalePost(ThreatScalePostEvent event) {
		SCALE_POST.post(new ThreatScalePostEventJS(event));
	}

	@SubscribeEvent
	public void onAbilityChanged(ThreatAbilityChangedEvent event) {
		ABILITY_CHANGED.post(new ThreatAbilityChangedEventJS(event));
	}

	@SubscribeEvent
	public void onArchetypeChanged(ThreatArchetypeChangedEvent event) {
		ARCHETYPE_CHANGED.post(new ThreatArchetypeChangedEventJS(event));
	}

	@SubscribeEvent
	public void onConfigChanged(ThreatConfigChangedEvent event) {
		CONFIG_CHANGED.post(new ThreatConfigChangedEventJS(event));
	}
}
