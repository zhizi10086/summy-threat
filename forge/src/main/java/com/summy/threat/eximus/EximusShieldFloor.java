package com.summy.threat.eximus;

/**
 * 由 {@code AttributeInstanceMixin} 实现：记录卓越护盾期间的属性快照地板。
 * 读取属性时返回 {@code max(实际值, floor)}，让护甲/攻击/移速等全部按快照结算。
 */
public interface EximusShieldFloor {
	void summyThreat$setFloor(double value);

	void summyThreat$clearFloor();

	double summyThreat$getFloor();
}
