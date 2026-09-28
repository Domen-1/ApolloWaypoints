package com.domen.apollowaypoints.server;

import com.domen.apollowaypoints.model.Category;
import com.domen.apollowaypoints.model.Visibility;
import com.domen.apollowaypoints.model.Waypoint;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class StoreJsonTest {
	@Test
	void waypointRoundTrip() {
		Waypoint w = new Waypoint(3, "Хаб", "Х", "minecraft:the_nether", 10, null, -20, -90, 13, "portals", Visibility.GLOBAL,
			"центр", UUID.randomUUID(), "Domen", 5L, "Obabok", 6L);
		assertEquals(w, StoreJson.readWaypoint(JsonParser.parseString(StoreJson.write(w).toString()).getAsJsonObject()));
	}

	@Test
	void lenientRead() {
		JsonObject minimal = JsonParser.parseString("{\"id\":1,\"name\":\"a\",\"x\":1,\"z\":2,\"color\":\"gold\"}").getAsJsonObject();
		Waypoint w = StoreJson.readWaypoint(minimal);
		assertNull(w.y());
		assertEquals(6, w.color());
		assertEquals("minecraft:overworld", w.dimension());
		assertEquals(Category.DEFAULT_ID, w.category());
		assertEquals(Visibility.LOCAL, w.visibility());
	}

	@Test
	void categoryRoundTrip() {
		for (Category c : Category.defaults()) {
			assertEquals(c, StoreJson.readCategory(StoreJson.write(c)));
		}
	}
}
