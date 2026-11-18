package com.summy.threat.legendary;

/** 内置传奇动作处理器注册入口。 */
public final class LegendaryActionHandlers {
	private LegendaryActionHandlers() {
	}

	public static void registerAll() {
		registerAll(LegendaryActionRuntime.handlers());
	}

	public static void registerAll(LegendaryActionHandlerRegistry registry) {
		registry.register(LegendaryActionId.JADE_LIGHT, new JadeLightAction());
		registry.register(LegendaryActionId.MIRROR_STEP, new MirrorStepAction());
		registry.register(LegendaryActionId.COLLAPSING_HORIZON,
				new CollapsingHorizonAction());
		registry.register(LegendaryActionId.LAIR_BREACH, new LairBreachAction());
		registry.register(LegendaryActionId.DOOM_CLOCK, new DoomClockAction());
		registry.register(LegendaryActionId.REVERSE_GRAVITY, new ReverseGravityAction());
		registry.register(LegendaryActionId.LEGENDARY_RIPOSTE,
				new LegendaryRiposteAction());
		registry.register(LegendaryActionId.FRIGHTFUL_PRESENCE,
				new FrightfulPresenceAction());
		registry.register(LegendaryActionId.DRAGON_LAIR_PULSE,
				new DragonLairPulseAction());
		registry.register(LegendaryActionId.DRAGONFALL_EDICT,
				new DragonfallEdictAction());
		registry.register(LegendaryActionId.DECAY_TIDE, new DecayTideAction());
		registry.register(LegendaryActionId.ECHO_SENTENCE, new EchoSentenceAction());
	}
}
