package com.summy.threat.equipment;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrismProjectileDataTest {
	@Test
	void roundTripPreservesShotSnapshotAndApplicationFlags() {
		UUID shotId = UUID.fromString("6f5c1a2e-9000-4a0b-9d1e-000000000901");
		PrismProjectileData data = new PrismProjectileData(
				PrismProjectileData.SCHEMA, shotId, 2,
				"astral_plating", "starfire_cycle", "starfall_cycle_arrow",
				true, true, false, true, false);
		CompoundTag root = new CompoundTag();
		root.putString("UnknownRoot", "keep");

		PrismProjectileData.write(root, data);
		PrismProjectileData decoded = PrismProjectileData.read(root).orElseThrow();

		assertEquals(data, decoded);
		assertEquals("keep", root.getString("UnknownRoot"));
		assertTrue(decoded.velocityApplied());
		assertFalse(decoded.pierceApplied());
	}

	@Test
	void malformedSchemaIsIgnoredWithoutRewritingData() {
		CompoundTag root = new CompoundTag();
		CompoundTag projectile = new CompoundTag();
		projectile.putInt("schema", 99);
		root.put(PrismProjectileData.ROOT, projectile);

		assertTrue(PrismProjectileData.read(root).isEmpty());
		assertEquals(99, root.getCompound(PrismProjectileData.ROOT).getInt("schema"));
	}
}
