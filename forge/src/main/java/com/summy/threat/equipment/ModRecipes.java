package com.summy.threat.equipment;

import com.summy.threat.SummyThreat;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/** B15 动态锻造序列化器注册。 */
public final class ModRecipes {
	public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
			DeferredRegister.create(Registries.RECIPE_SERIALIZER, SummyThreat.MOD_ID);
	public static final RegistryObject<RecipeSerializer<EquipmentSmithingRecipe>>
			EQUIPMENT_SMITHING = SERIALIZERS.register("equipment_smithing",
					EquipmentSmithingRecipe.Serializer::new);

	private ModRecipes() {
	}
}
