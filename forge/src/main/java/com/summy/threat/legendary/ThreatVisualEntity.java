package com.summy.threat.legendary;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** 仅同步表现。技能实例持有命中坐标、计时和伤害。 */
public final class ThreatVisualEntity extends Entity {
	public static final byte LEGENDARY = 0, LEADER = 1, EXIMUS = 2, BOSS = 3;
	public static final byte PRIMARY = 0, BEAM = 1, MARKER = 2, AURA = 3, GROUND_RING = 4;
	public static final byte WARNING = 0, ACTIVE = 1, IMPACT = 2, FADING = 3;
	public static final int LOCKED = 1;
	static final float JADE_BEAM_HEIGHT = 12.0F;
	private static final int FADE_IN_TICKS = 6;
	private static final int FADE_OUT_TICKS = 10;

	private static final EntityDataAccessor<Byte> CATEGORY = SynchedEntityData.defineId(ThreatVisualEntity.class, EntityDataSerializers.BYTE);
	private static final EntityDataAccessor<String> VISUAL_ID = SynchedEntityData.defineId(ThreatVisualEntity.class, EntityDataSerializers.STRING);
	private static final EntityDataAccessor<Integer> COLOR = SynchedEntityData.defineId(ThreatVisualEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Byte> ROLE = SynchedEntityData.defineId(ThreatVisualEntity.class, EntityDataSerializers.BYTE);
	private static final EntityDataAccessor<Byte> PHASE = SynchedEntityData.defineId(ThreatVisualEntity.class, EntityDataSerializers.BYTE);
	private static final EntityDataAccessor<Float> PROGRESS = SynchedEntityData.defineId(ThreatVisualEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> RADIUS = SynchedEntityData.defineId(ThreatVisualEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> ANGLE = SynchedEntityData.defineId(ThreatVisualEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> INTENSITY = SynchedEntityData.defineId(ThreatVisualEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Integer> FLAGS = SynchedEntityData.defineId(ThreatVisualEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Vector3f> OFFSET = SynchedEntityData.defineId(ThreatVisualEntity.class, EntityDataSerializers.VECTOR3);

	private long expiresAt;
	private int clientFadeStart;
	private Vector3f previousOffset = new Vector3f();

	public ThreatVisualEntity(EntityType<? extends ThreatVisualEntity> type, Level level) {
		super(type, level);
		noPhysics = true;
		setNoGravity(true);
	}

	/** 场地代理固定在地面投影，生成失败时技能照常结算。 */
	@javax.annotation.Nullable
	static ThreatVisualEntity spawnGround(ServerLevel level, LegendaryActionId id, Vec3 center,
			int lifetime, float radius, float angle, float intensity) {
		return spawnGround(level, id, center, lifetime, radius, angle, intensity, id.color());
	}

	@javax.annotation.Nullable
	static ThreatVisualEntity spawnGround(ServerLevel level, LegendaryActionId id, Vec3 center,
			int lifetime, float radius, float angle, float intensity, int color) {
		ThreatVisualEntity visual = ModLegendaryEntities.THREAT_VISUAL.get().create(level);
		if (visual == null) {
			return null;
		}
		visual.initialize(LEGENDARY, id.id(), PRIMARY, color, level.getGameTime() + lifetime);
		visual.setPos(center.x, center.y, center.z);
		visual.update(WARNING, 0.0F, radius, angle, intensity, 0, Vec3.ZERO);
		return level.addFreshEntity(visual) ? visual : null;
	}

	void initialize(byte category, String id, byte role, int color, long expiresAt) {
		this.expiresAt = expiresAt;
		entityData.set(CATEGORY, category);
		entityData.set(VISUAL_ID, id);
		entityData.set(ROLE, role);
		entityData.set(COLOR, color);
	}

	void update(byte phase, float progress, float radius, float angle, float intensity, int flags, Vec3 offset) {
		entityData.set(PHASE, phase);
		entityData.set(PROGRESS, progress);
		entityData.set(RADIUS, radius);
		entityData.set(ANGLE, angle);
		entityData.set(INTENSITY, intensity);
		entityData.set(FLAGS, flags);
		entityData.set(OFFSET, new Vector3f((float) offset.x, (float) offset.y, (float) offset.z));
	}

	/** 正常收尾只延长渲染，技能实例已从服务器的运行列表移除。 */
	void finish(LegendaryActionInstance.StopReason reason) {
		if (isRemoved()) {
			return;
		}
		if (reason != LegendaryActionInstance.StopReason.COMPLETED) {
			discard();
			return;
		}
		entityData.set(PHASE, FADING);
		// 给相位同步留两刻余量，客户端能先降到零，再收到移除。
		expiresAt = level().getGameTime() + FADE_OUT_TICKS + 2;
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
		super.onSyncedDataUpdated(key);
		if (key == PHASE && level().isClientSide() && phase() == FADING) {
			clientFadeStart = tickCount;
		}
	}

	float opacity(float partialTick) {
		float age = tickCount + partialTick;
		float fadeIn = net.minecraft.util.Mth.clamp(age / FADE_IN_TICKS, 0.0F, 1.0F);
		if (phase() != FADING) {
			return fadeIn;
		}
		float fadeOut = net.minecraft.util.Mth.clamp(1.0F - (age - clientFadeStart) / FADE_OUT_TICKS, 0.0F, 1.0F);
		return Math.min(fadeIn, fadeOut);
	}

	public byte category() { return entityData.get(CATEGORY); }
	public String visualId() { return entityData.get(VISUAL_ID); }
	public byte role() { return entityData.get(ROLE); }
	public int color() { return entityData.get(COLOR); }
	public byte phase() { return entityData.get(PHASE); }
	public float progress() { return entityData.get(PROGRESS); }
	public float radius() { return entityData.get(RADIUS); }
	public float angle() { return entityData.get(ANGLE); }
	public float intensity() { return entityData.get(INTENSITY); }
	public int flags() { return entityData.get(FLAGS); }
	public Vector3f offset() { return entityData.get(OFFSET); }

	public Vector3f interpolatedOffset(float partialTick) {
		return new Vector3f(previousOffset).lerp(offset(), partialTick);
	}

	@Override
	protected void defineSynchedData() {
		entityData.define(CATEGORY, LEGENDARY);
		entityData.define(VISUAL_ID, "");
		entityData.define(COLOR, 0xFFFFFF);
		entityData.define(ROLE, PRIMARY);
		entityData.define(PHASE, WARNING);
		entityData.define(PROGRESS, 0.0F);
		entityData.define(RADIUS, 0.0F);
		entityData.define(ANGLE, 0.0F);
		entityData.define(INTENSITY, 0.0F);
		entityData.define(FLAGS, 0);
		entityData.define(OFFSET, new Vector3f());
	}

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide()) {
			previousOffset = offset();
		} else if (level().getGameTime() > expiresAt) {
			discard();
		}
	}

	@Override
	public boolean isPickable() { return false; }

	@Override
	public boolean isPushable() { return false; }

	@Override
	public boolean canBeCollidedWith() { return false; }

	@Override
	public boolean isAttackable() { return false; }

	@Override
	public boolean shouldRenderAtSqrDistance(double distanceSquared) {
		// 裁剪距离随客户端视距缩放，避免远处持续渲染。
		double range = 64.0D * getViewScale();
		return distanceSquared < range * range;
	}

	@Override
	public AABB getBoundingBoxForCulling() {
		Vector3f offset = offset();
		Vec3 beam = position().add(offset.x(), offset.y(), offset.z());
		float horizontal = Math.max(1.0F, radius());
		if (role() == PRIMARY && LegendaryActionId.COLLAPSING_HORIZON.id().equals(visualId())) {
			// 同步半径是安全圈，裁剪仍须覆盖固定外圈。
			horizontal = (float) CollapsingHorizonAction.OUTER_RADIUS;
		}
		double height = JADE_BEAM_HEIGHT;
		if (role() == PRIMARY) {
			// 地面贴花以外也要覆盖喷发柱、上升柱及身体裂纹。
			height = switch (visualId()) {
				case "lair_breach" -> 4.2D;
				case "legendary_riposte" -> 2.55D;
				case "reverse_gravity" -> ReverseGravityAction.COLUMN_HEIGHT;
				default -> 0.0D;
			};
		}
		return new AABB(position(), beam).inflate(horizontal, 1.0D, horizontal)
				.expandTowards(0.0D, height, 0.0D);
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag tag) {
		// 恢复世界时不存在对应技能实例。
		discard();
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag tag) {
	}

	@Override
	public Packet<ClientGamePacketListener> getAddEntityPacket() {
		return new ClientboundAddEntityPacket(this);
	}
}
