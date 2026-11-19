package com.summy.threat.legendary;

/** 判定与渲染共用波次的时序、半径和缺口。 */
enum LegendaryWavePattern {
	DRAGON(35, 5.0D, 2.5D, Math.toRadians(30.0D)),
	DECAY(30, 3.0D, 1.2D, 0.0D);

	static final int COUNT = 4;
	static final int INTERVAL = 15;
	static final double SPEED = 0.6D;
	static final double MAX_RADIUS = 18.0D;

	final int warningTicks;
	final double warningRadius;
	final double width;
	final double gapHalfWidth;

	LegendaryWavePattern(int warningTicks, double warningRadius, double width, double gapHalfWidth) {
		this.warningTicks = warningTicks;
		this.warningRadius = warningRadius;
		this.width = width;
		this.gapHalfWidth = gapHalfWidth;
	}

	int waveAge(int age, int index) {
		return age - warningTicks - index * INTERVAL;
	}

	int endAge() {
		return LegendaryActionMath.waveEndExclusiveAge(warningTicks + (COUNT - 1) * INTERVAL,
				MAX_RADIUS, SPEED);
	}

	boolean isPresent(int waveAge) {
		return waveAge >= 0 && waveAge <= MAX_RADIUS / SPEED + 2;
	}

	double radiusAt(double waveAge) {
		return Math.min(MAX_RADIUS, waveAge * SPEED);
	}

	double gapCenter(double baseAngle, int index) {
		return baseAngle + LegendaryActionMath.clockwiseSectorOffset(index);
	}

	boolean inDangerAngle(double angle, double baseAngle, int index) {
		return gapHalfWidth == 0.0D
				|| LegendaryVisuals.angularDistance(angle, gapCenter(baseAngle, index)) > gapHalfWidth;
	}
}
