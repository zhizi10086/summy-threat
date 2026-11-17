package com.summy.threat.equipment;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class EquipmentReturnPayloadTest {
	@Test
	void returnPayloadIsTakenOnceAndRemovedFromOutputTag() {
		CompoundTag output = new CompoundTag();
		CompoundTag root = new CompoundTag();
		root.putString(EquipmentSmithingRecipe.RETURN_ID, "transaction");
		ListTag returns = new ListTag();
		returns.add(new CompoundTag());
		returns.add(new CompoundTag());
		root.put(EquipmentSmithingRecipe.RETURN_PRISMS, returns);
		output.put(EximusEquipmentData.EQUIPMENT_ROOT, root);

		EquipmentCraftingHandler.ReturnPayload payload =
				EquipmentCraftingHandler.takeReturnPayload(output);

		assertNotNull(payload);
		assertEquals("transaction", payload.transactionId());
		assertEquals(2, payload.stacks().size());
		assertFalse(output.getCompound(EximusEquipmentData.EQUIPMENT_ROOT)
				.contains(EquipmentSmithingRecipe.RETURN_PRISMS));
		assertFalse(output.getCompound(EximusEquipmentData.EQUIPMENT_ROOT)
				.contains(EquipmentSmithingRecipe.RETURN_ID, Tag.TAG_STRING));
	}
}
