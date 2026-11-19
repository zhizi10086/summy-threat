package com.summy.threat.equipment;

import com.google.gson.JsonObject;
import com.summy.threat.Config;
import com.summy.threat.item.ModItems;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.level.Level;

import java.util.Optional;
import java.util.UUID;

/** 单一动态锻造配方，按 operation 执行 B15 的六种事务。 */
public final class EquipmentSmithingRecipe implements SmithingRecipe {
	public static final String RETURN_PRISMS = "summy_threat:return_prisms";
	public static final String RETURN_ID = "summy_threat:return_id";
	private final ResourceLocation id;
	private final EquipmentOperation operation;

	public EquipmentSmithingRecipe(ResourceLocation id, EquipmentOperation operation) {
		this.id = id;
		this.operation = operation;
	}

	public EquipmentOperation operation() {
		return operation;
	}

	@Override
	public boolean matches(Container container, Level level) {
		if (!Config.SPEC.isLoaded() || !Config.EXIMUS_EQUIPMENT_ENABLED.get()) {
			return false;
		}
		ItemStack template = container.getItem(0);
		ItemStack base = container.getItem(1);
		ItemStack addition = container.getItem(2);
		if (!template.is(Items.ECHO_SHARD) || base.isEmpty()) {
			return false;
		}
		EquipmentCategory category = EquipmentTypeResolver.category(base);
		return switch (operation) {
			case UPGRADE_I -> category != null
					&& addition.is(ModItems.EXIMUS_ESSENCE.get())
					&& EquipmentRules.canUpgrade(base.getTag(), 1, category);
			case UPGRADE_II -> category != null
					&& addition.is(ModItems.EXIMUS_ESSENCE.get())
					&& EquipmentRules.canUpgrade(base.getTag(), 2, category);
			case EMBED_PRISM -> StarPrismConfig.enabled()
					&& category != null
					&& addition.is(ModItems.ABYSSAL_STAR_PRISM.get())
					&& EquipmentRules.embeddingSlot(base.getTag(), addition.getTag(), category) >= 0;
			case REFRESH_STANDARD -> StarPrismConfig.enabled()
					&& base.is(ModItems.ABYSSAL_STAR_PRISM.get())
					&& addition.isEmpty()
					&& StarPrismRoller.hasCandidates(PrismRollMode.STANDARD,
							StarPrismConfig.rollSettings(), StarPrismConfig.weightProvider());
			case REFRESH_ADVANCED -> StarPrismConfig.enabled()
					&& base.is(ModItems.ABYSSAL_STAR_PRISM.get())
					&& addition.is(ModItems.EXIMUS_ESSENCE.get())
					&& StarPrismRoller.hasCandidates(PrismRollMode.ADVANCED,
							StarPrismConfig.rollSettings(), StarPrismConfig.weightProvider());
			case DISASSEMBLE_ALL -> category != null
					&& EquipmentRules.categoryEnabled(category)
					&& addition.is(Items.AMETHYST_SHARD)
					&& occupiedSlots(base.getTag()) > 0;
		};
	}

	@Override
	public ItemStack assemble(Container container, RegistryAccess registries) {
		if (!matches(container, null)) {
			return ItemStack.EMPTY;
		}
		ItemStack base = container.getItem(1);
		ItemStack addition = container.getItem(2);
		return switch (operation) {
			case UPGRADE_I -> EximusEquipmentData.withUpgradeLevel(base, 1);
			case UPGRADE_II -> EximusEquipmentData.withUpgradeLevel(base, 2);
			case EMBED_PRISM -> assembleEmbed(base, addition);
			case REFRESH_STANDARD -> withPendingRefresh(base, "standard");
			case REFRESH_ADVANCED -> withPendingRefresh(base, "advanced");
			case DISASSEMBLE_ALL -> assembleDisassembly(base);
		};
	}

	@Override
	public ItemStack getResultItem(RegistryAccess registries) {
		return ItemStack.EMPTY;
	}

	@Override
	public boolean isTemplateIngredient(ItemStack stack) {
		return stack.is(Items.ECHO_SHARD);
	}

	@Override
	public boolean isBaseIngredient(ItemStack stack) {
		if (operation == EquipmentOperation.REFRESH_STANDARD
				|| operation == EquipmentOperation.REFRESH_ADVANCED) {
			return stack.is(ModItems.ABYSSAL_STAR_PRISM.get());
		}
		EquipmentCategory category = EquipmentTypeResolver.category(stack);
		return category != null && EquipmentRules.categoryEnabled(category);
	}

	@Override
	public boolean isAdditionIngredient(ItemStack stack) {
		return switch (operation) {
			case UPGRADE_I, UPGRADE_II, REFRESH_ADVANCED ->
					stack.is(ModItems.EXIMUS_ESSENCE.get());
			case EMBED_PRISM -> stack.is(ModItems.ABYSSAL_STAR_PRISM.get());
			case REFRESH_STANDARD -> stack.isEmpty();
			case DISASSEMBLE_ALL -> stack.is(Items.AMETHYST_SHARD);
		};
	}

	@Override
	public boolean isSpecial() {
		return true;
	}

	@Override
	public boolean isIncomplete() {
		return false;
	}

	@Override
	public ResourceLocation getId() {
		return id;
	}

	@Override
	public RecipeSerializer<?> getSerializer() {
		return ModRecipes.EQUIPMENT_SMITHING.get();
	}

	private static ItemStack assembleEmbed(ItemStack base, ItemStack prism) {
		EquipmentCategory category = EquipmentTypeResolver.category(base);
		int slot = EquipmentRules.embeddingSlot(base.getTag(), prism.getTag(), category);
		return slot < 0 ? ItemStack.EMPTY : EximusEquipmentData.withPrism(base, slot, prism);
	}

	private static ItemStack withPendingRefresh(ItemStack base, String pending) {
		StarPrismData current = StarPrismData.read(base).orElse(
				new StarPrismData(StarPrismData.SCHEMA, false, null,
						null, null, null));
		ItemStack out = base.copy();
		out.setCount(1);
		StarPrismData.write(out, new StarPrismData(StarPrismData.SCHEMA,
				current.revealed(), pending, current.armorAffix(),
				current.meleeAffix(), current.rangedAffix()));
		return out;
	}

	private static ItemStack assembleDisassembly(ItemStack base) {
		CompoundTag tag = base.getTag();
		ListTag returns = new ListTag();
		for (int slot = 0; slot < EximusEquipmentData.SLOT_COUNT; slot++) {
			ItemStack prism = EximusEquipmentData.prism(base, slot);
			if (!prism.isEmpty()) {
				prism.setCount(1);
				returns.add(prism.save(new CompoundTag()));
			}
		}
		ItemStack out = EximusEquipmentData.clearPrisms(base);
		CompoundTag root = out.getOrCreateTag().getCompound(EximusEquipmentData.EQUIPMENT_ROOT);
		root.put(RETURN_PRISMS, returns);
		root.putString(RETURN_ID, UUID.randomUUID().toString());
		return out;
	}

	private static int occupiedSlots(CompoundTag equipment) {
		if (!EximusEquipmentData.isValid(equipment)) {
			return 0;
		}
		int count = 0;
		for (int slot = 0; slot < EximusEquipmentData.SLOT_COUNT; slot++) {
			if (!EximusEquipmentData.prismTag(equipment, slot).isEmpty()) {
				count++;
			}
		}
		return count;
	}

	public static final class Serializer implements RecipeSerializer<EquipmentSmithingRecipe> {
		@Override
		public EquipmentSmithingRecipe fromJson(ResourceLocation id, JsonObject json) {
			EquipmentOperation operation = EquipmentOperation.byId(
					json.has("operation") ? json.get("operation").getAsString() : "");
			if (operation == null) {
				throw new IllegalArgumentException("未知装备锻造操作：" + id);
			}
			return new EquipmentSmithingRecipe(id, operation);
		}

		@Override
		public EquipmentSmithingRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
			EquipmentOperation operation = EquipmentOperation.byId(buffer.readUtf());
			if (operation == null) {
				throw new IllegalArgumentException("未知装备锻造操作：" + id);
			}
			return new EquipmentSmithingRecipe(id, operation);
		}

		@Override
		public void toNetwork(FriendlyByteBuf buffer, EquipmentSmithingRecipe recipe) {
			buffer.writeUtf(recipe.operation.id());
		}
	}
}
