package com.domen.apollowaypoints.format;

import com.domen.apollowaypoints.model.Dimensions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class XaeroShareTest {
	@Test
	void buildsTheSameStringAsXaero() {
		// Field order and escaping as in Xaero's WaypointSharingHandler (Minimap 26.5.3).
		assertEquals("xaero-waypoint:Base:B:100:64:-200:11:false:0:Internal-overworld",
			XaeroShare.build("Base", "B", 100, 64, -200, 11, null, Dimensions.OVERWORLD));
		assertEquals("xaero-waypoint:Hub:H:10:~:20:13:true:90:Internal-the-nether",
			XaeroShare.build("Hub", "H", 10, null, 20, 13, 90, Dimensions.NETHER));
	}

	@Test
	void escapesLikeXaero() {
		// "-" -> "^min^", "_" -> "-", "*" -> "^ast^", ":" -> "^col^"
		assertEquals("xaero-waypoint:a^min^b-c^ast^d^col^e:X:0:0:0:0:false:0:Internal-the-end",
			XaeroShare.build("a-b_c*d:e", "X", 0, 0, 0, 0, null, Dimensions.END));
	}

	@Test
	void roundTrip() {
		String line = XaeroShare.build("Ферма-железа_2 *new*: вход", "Ж", -12345, null, 67890, 20, -45, "mymod:deep/dark");
		XaeroShare.Shared shared = XaeroShare.parse(line);
		assertEquals("Ферма-железа_2 *new*: вход", shared.name());
		assertEquals("Ж", shared.symbol());
		assertEquals(-12345, shared.x());
		assertNull(shared.y());
		assertEquals(67890, shared.z());
		assertEquals(20, shared.color());
		assertEquals(-45, shared.yaw());
		assertEquals("mymod:deep/dark", shared.dimension());
	}

	@Test
	void parsesVanillaDimensionsAndLegacySuffix() {
		assertEquals(Dimensions.OVERWORLD, XaeroShare.parse("xaero-waypoint:A:A:1:2:3:4:false:0:Internal-overworld").dimension());
		assertEquals(Dimensions.NETHER, XaeroShare.parse("xaero-waypoint:A:A:1:2:3:4:false:0:Internal-the-nether-waypoints").dimension());
		assertEquals(Dimensions.END, XaeroShare.parse("xaero-waypoint:A:A:1:2:3:4:false:0:Internal-dim%1").dimension());
	}

	@Test
	void rotationFlagControlsYaw() {
		assertNull(XaeroShare.parse("xaero-waypoint:A:A:1:2:3:4:false:90:Internal-overworld").yaw());
		assertEquals(90, XaeroShare.parse("xaero-waypoint:A:A:1:2:3:4:true:90:Internal-overworld").yaw());
	}

	@Test
	void otherWorldsHaveNoDimension() {
		assertNull(XaeroShare.parse("xaero-waypoint:A:A:1:2:3:4:false:0:External-overworld").dimension());
	}

	@Test
	void rejectsNonShares() {
		assertNull(XaeroShare.parse("hello"));
		assertNull(XaeroShare.parse("xaero-waypoint:A:A:x:2:3:4:false:0:Internal-overworld"));
		assertNull(XaeroShare.parse("xaero-waypoint:A:A:1:2:3"));
		assertNull(XaeroShare.parse(null));
	}
}
