package com.summy.threat.combat;

import com.summy.threat.equipment.ThreatCategory;
import com.summy.threat.equipment.ThreatHitContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BossHardcodedDamageScalingTest {
	@Test
	void legendaryActionsAreAlreadyTlScaledBeforeHardcodedBossLayer() {
		assertTrue(BossHardcodedDamageScaling.alreadyScaled(
				new ThreatHitContext.Hit(ThreatCategory.LEGENDARY_ACTION, "jade_light")));
		assertFalse(BossHardcodedDamageScaling.alreadyScaled(
				new ThreatHitContext.Hit(ThreatCategory.LEADER_ABILITY, "quake_fault")));
		assertFalse(BossHardcodedDamageScaling.alreadyScaled(null));
	}
}
