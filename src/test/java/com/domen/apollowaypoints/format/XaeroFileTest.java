package com.domen.apollowaypoints.format;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XaeroFileTest {
	@Test
	void parsesCurrentAndOldLines() {
		List<XaeroFile.Entry> entries = XaeroFile.parse(List.of(
			"#",
			XaeroFile.HEADER,
			"#",
			"sets:gui.xaero_default:Farms",
			"waypoint:Iron§§farm:I:100:~:-200:6:false:0:Farms:true:90:1:false",
			"waypoint:Old:O:1:2:3:11:true:0:gui.xaero_default:false:0",
			"waypoint:Death:D:5:6:7:0:false:1:gui.xaero_default:false:0:0:false",
			"waypoint:broken:B:x:2:3:11:false:0:gui.xaero_default:false:0:0:false"
		));
		assertEquals(3, entries.size());

		XaeroFile.Entry iron = entries.getFirst();
		assertEquals("Iron:farm", iron.name());
		assertNull(iron.y());
		assertEquals(6, iron.color());
		assertEquals(90, iron.yaw());
		assertEquals(1, iron.visibilityType());
		assertEquals("Farms", iron.set());

		XaeroFile.Entry old = entries.get(1);
		assertTrue(old.disabled());
		assertNull(old.yaw());
		assertEquals(0, old.visibilityType());

		assertEquals(1, entries.get(2).type());
	}

	@Test
	void writeThenParse() {
		List<XaeroFile.Entry> written = List.of(
			new XaeroFile.Entry("Хаб: вход", "Х", -1, 70, 2, 13, false, 0, "Apollo", null, 3),
			new XaeroFile.Entry("Spawn", "S", 0, null, 0, 15, false, 0, "Apollo", 180, 0));
		List<String> lines = XaeroFile.write("Apollo", written);
		assertEquals("sets:gui.xaero_default:Apollo", lines.get(3));
		assertFalse(lines.get(4).contains("Хаб: вход"));
		assertEquals(written, XaeroFile.parse(lines));
	}
}
