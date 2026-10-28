package com.summy.threat.network;

import com.summy.threat.api.AbilityDefinition;
import com.summy.threat.api.ArchetypeDefinition;
import com.summy.threat.api.SummyThreatApi;
import com.summy.threat.ability.AbilityId;
import com.summy.threat.eximus.EximusArchetype;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** 服务端 → 客户端：同步 KJS 自定义能力/原型展示元数据。 */
public record RegistrySyncPacket(CompoundTag data) {
	public static RegistrySyncPacket current() {
		return new RegistrySyncPacket(encodeDefinitions(
				AbilityId.customDefinitions(), EximusArchetype.customDefinitions()));
	}

	public static void encode(RegistrySyncPacket packet, FriendlyByteBuf buffer) {
		buffer.writeNbt(packet.data);
	}

	public static RegistrySyncPacket decode(FriendlyByteBuf buffer) {
		CompoundTag tag = buffer.readNbt();
		return new RegistrySyncPacket(tag == null ? new CompoundTag() : tag);
	}

	public static void handle(RegistrySyncPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
		NetworkEvent.Context context = contextSupplier.get();
		context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
				() -> () -> SummyThreatApi.replaceClientDefinitions(
						decodeAbilities(packet.data), decodeArchetypes(packet.data))));
		context.setPacketHandled(true);
	}

	private static CompoundTag encodeDefinitions(List<AbilityDefinition> abilities,
			List<ArchetypeDefinition> archetypes) {
		CompoundTag root = new CompoundTag();
		ListTag abilityList = new ListTag();
		for (AbilityDefinition definition : abilities) {
			CompoundTag tag = new CompoundTag();
			tag.putString("id", definition.id());
			tag.putInt("unlock_tl", definition.unlockTl());
			tag.putString("display_name", definition.displayName());
			tag.putInt("color", definition.color());
			tag.putBoolean("rollable", definition.rollable());
			tag.putInt("weight", definition.weight());
			tag.putBoolean("eximus_only", definition.eximusOnly());
			abilityList.add(tag);
		}
		root.put("abilities", abilityList);
		ListTag archetypeList = new ListTag();
		for (ArchetypeDefinition definition : archetypes) {
			CompoundTag tag = new CompoundTag();
			tag.putString("id", definition.id());
			tag.putString("display_name", definition.displayName());
			tag.putInt("color", definition.color());
			tag.putBoolean("rollable", definition.rollable());
			tag.putInt("weight", definition.weight());
			archetypeList.add(tag);
		}
		root.put("archetypes", archetypeList);
		return root;
	}

	private static List<AbilityDefinition> decodeAbilities(CompoundTag root) {
		List<AbilityDefinition> out = new ArrayList<>();
		ListTag list = root.getList("abilities", Tag.TAG_COMPOUND);
		for (int i = 0; i < list.size(); i++) {
			CompoundTag tag = list.getCompound(i);
			out.add(new AbilityDefinition(tag.getString("id"), tag.getInt("unlock_tl"),
					tag.getString("display_name"), tag.getInt("color"),
					tag.getBoolean("rollable"), tag.getInt("weight"),
					tag.getBoolean("eximus_only")));
		}
		return out;
	}

	private static List<ArchetypeDefinition> decodeArchetypes(CompoundTag root) {
		List<ArchetypeDefinition> out = new ArrayList<>();
		ListTag list = root.getList("archetypes", Tag.TAG_COMPOUND);
		for (int i = 0; i < list.size(); i++) {
			CompoundTag tag = list.getCompound(i);
			out.add(new ArchetypeDefinition(tag.getString("id"), tag.getString("display_name"),
					tag.getInt("color"), tag.getBoolean("rollable"), tag.getInt("weight")));
		}
		return out;
	}
}
