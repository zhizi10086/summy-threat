package com.summy.threat.compat.jade;

import com.summy.threat.eximus.EximusShield;
import com.summy.threat.scaling.MonsterScaler;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IServerDataProvider;

/** 服务端数据：只对"被本模组数值化过"的怪写 TL / 类别 /（未来的）能力名。 */
public enum EntityAbilityDataProvider implements IServerDataProvider<EntityAccessor> {
	INSTANCE;

	public static final String KEY_TL = "tl";
	public static final String KEY_KIND = "kind";
	public static final String KEY_ABILITIES = "abilities";
	public static final String KEY_SHIELD = "eximus_shield";
	public static final String KEY_SHIELD_MAX = "eximus_shield_max";

	@Override
	public void appendServerData(CompoundTag data, EntityAccessor accessor) {
		if (!(accessor.getEntity() instanceof Mob mob)) {
			return;
		}
		CompoundTag persistent = mob.getPersistentData();
		if (!persistent.getBoolean(MonsterScaler.TAG_SCALED)) {
			return;
		}
		data.putInt(KEY_TL, persistent.getInt(MonsterScaler.TAG_TL));
		data.putString(KEY_KIND, MonsterScaler.kindOf(mob).name());
		if (EximusShield.isActive(mob)) {
			data.putDouble(KEY_SHIELD, EximusShield.current(mob));
			data.putDouble(KEY_SHIELD_MAX, EximusShield.max(mob));
		}
		// 能力系统落地后会把能力名写进该标签；现在没有时就不显示能力行
		String abilities = persistent.getString(MonsterScaler.TAG_ABILITIES);
		if (!abilities.isEmpty()) {
			data.putString(KEY_ABILITIES, abilities);
		}
	}

	@Override
	public ResourceLocation getUid() {
		return new ResourceLocation("summy_threat", "entity_status_data");
	}
}
