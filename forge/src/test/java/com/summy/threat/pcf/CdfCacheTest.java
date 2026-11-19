package com.summy.threat.pcf;

import org.junit.jupiter.api.Test;

import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

final class CdfCacheTest {
	@Test
	void keepsSavedStateLoadedDuringLegacyLookup() {
		var cache = new ConcurrentHashMap<Integer, String>();

		String result = CdfManager.resolveState(cache, 1, () -> {
			// 结构查询可同步触发区块读档。同一哈希桶中的其他区块也可能在此时载入。
			cache.put(17, "neighbor");
			cache.put(1, "saved");
			return "legacy";
		});

		assertEquals("neighbor", cache.get(17));
		assertSame(cache.get(1), result);
		assertEquals("saved", result);
	}
}
