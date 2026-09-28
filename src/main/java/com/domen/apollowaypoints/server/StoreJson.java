package com.domen.apollowaypoints.server;

import com.domen.apollowaypoints.model.Category;
import com.domen.apollowaypoints.model.Colors;
import com.domen.apollowaypoints.model.Validation;
import com.domen.apollowaypoints.model.Visibility;
import com.domen.apollowaypoints.model.Waypoint;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.UUID;

/** JSON form of the model in waypoints.json. Readers are lenient: a missing field gets a default. */
final class StoreJson {
	private StoreJson() {
	}

	static JsonObject write(Waypoint w) {
		JsonObject o = new JsonObject();
		o.addProperty("id", w.id());
		o.addProperty("name", w.name());
		o.addProperty("symbol", w.symbol());
		o.addProperty("dimension", w.dimension());
		o.addProperty("x", w.x());
		if (w.y() != null) {
			o.addProperty("y", w.y());
		}
		o.addProperty("z", w.z());
		if (w.yaw() != null) {
			o.addProperty("yaw", w.yaw());
		}
		o.addProperty("color", Colors.id(w.color()));
		o.addProperty("category", w.category());
		o.addProperty("visibility", w.visibility().id);
		if (!w.description().isEmpty()) {
			o.addProperty("description", w.description());
		}
		o.addProperty("owner", w.owner().toString());
		o.addProperty("ownerName", w.ownerName());
		o.addProperty("createdAt", w.createdAt());
		o.addProperty("updatedBy", w.updatedBy());
		o.addProperty("updatedAt", w.updatedAt());
		return o;
	}

	static Waypoint readWaypoint(JsonObject o) {
		String name = string(o, "name", "?");
		return new Waypoint(
			o.get("id").getAsInt(),
			name,
			string(o, "symbol", Validation.defaultSymbol(name)),
			string(o, "dimension", "minecraft:overworld"),
			o.get("x").getAsInt(),
			o.has("y") && !o.get("y").isJsonNull() ? o.get("y").getAsInt() : null,
			o.get("z").getAsInt(),
			o.has("yaw") && !o.get("yaw").isJsonNull() ? o.get("yaw").getAsInt() : null,
			color(o.get("color")),
			string(o, "category", Category.DEFAULT_ID),
			visibility(string(o, "visibility", "local")),
			string(o, "description", ""),
			uuid(string(o, "owner", Waypoint.CONSOLE.toString())),
			string(o, "ownerName", "?"),
			o.has("createdAt") ? o.get("createdAt").getAsLong() : 0L,
			string(o, "updatedBy", string(o, "ownerName", "?")),
			o.has("updatedAt") ? o.get("updatedAt").getAsLong() : 0L
		);
	}

	static JsonObject write(Category c) {
		JsonObject o = new JsonObject();
		o.addProperty("id", c.id());
		o.addProperty("name", c.name());
		o.addProperty("color", Colors.id(c.color()));
		o.addProperty("visibility", c.visibility().id);
		return o;
	}

	static Category readCategory(JsonObject o) {
		String id = o.get("id").getAsString();
		return new Category(id, string(o, "name", id), color(o.get("color")), visibility(string(o, "visibility", "local")));
	}

	private static String string(JsonObject o, String key, String fallback) {
		JsonElement e = o.get(key);
		return e == null || e.isJsonNull() ? fallback : e.getAsString();
	}

	private static int color(JsonElement e) {
		if (e == null || e.isJsonNull()) {
			return 15;
		}
		int parsed = Colors.parse(e.getAsString());
		return parsed < 0 ? 15 : parsed;
	}

	private static Visibility visibility(String id) {
		Visibility visibility = Visibility.byId(id);
		return visibility == null ? Visibility.LOCAL : visibility;
	}

	private static UUID uuid(String text) {
		try {
			return UUID.fromString(text);
		} catch (IllegalArgumentException e) {
			return Waypoint.CONSOLE;
		}
	}
}
