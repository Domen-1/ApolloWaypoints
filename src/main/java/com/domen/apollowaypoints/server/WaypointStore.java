package com.domen.apollowaypoints.server;

import com.domen.apollowaypoints.ApolloWaypoints;
import com.domen.apollowaypoints.model.Category;
import com.domen.apollowaypoints.model.Waypoint;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * All waypoints of one world, kept in {@code <world>/apollowaypoints/waypoints.json}.
 * Only touched from the server thread. Writes are atomic and keep the previous file as waypoints.json.bak.
 */
public final class WaypointStore {
	private static final int FORMAT = 1;
	private static final long SAVE_DELAY_MS = 2000;
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	public record Trashed(Waypoint waypoint, long deletedAt, String deletedBy) {
	}

	private final Path dir;
	private final Path file;
	private UUID serverId = UUID.randomUUID();
	private int nextId = 1;
	private final Map<Integer, Waypoint> waypoints = new LinkedHashMap<>();
	private final Map<Integer, Trashed> trash = new LinkedHashMap<>();
	private final List<Category> categories = new ArrayList<>(Category.defaults());
	/** Set when the file exists but could not be parsed: we must never overwrite it with an empty list. */
	private String loadError;
	private long dirtySince = -1;

	private WaypointStore(Path dir) {
		this.dir = dir;
		this.file = dir.resolve("waypoints.json");
	}

	public static WaypointStore load(Path dir) {
		WaypointStore store = new WaypointStore(dir);
		if (!Files.exists(store.file)) {
			ApolloWaypoints.LOGGER.info("No {} yet, starting with an empty list", store.file);
			store.markDirty();
			return store;
		}
		try {
			store.read(JsonParser.parseString(Files.readString(store.file, StandardCharsets.UTF_8)).getAsJsonObject());
			ApolloWaypoints.LOGGER.info("Loaded {} waypoints from {}", store.waypoints.size(), store.file);
		} catch (Exception e) {
			store.loadError = e.toString();
			store.waypoints.clear();
			store.trash.clear();
			ApolloWaypoints.LOGGER.error("Cannot read {}; waypoints are READ-ONLY until the file is fixed and the server restarted", store.file, e);
		}
		return store;
	}

	private void read(JsonObject json) {
		serverId = UUID.fromString(json.get("serverId").getAsString());
		nextId = json.get("nextId").getAsInt();
		if (json.has("categories")) {
			categories.clear();
			for (JsonElement e : json.getAsJsonArray("categories")) {
				categories.add(StoreJson.readCategory(e.getAsJsonObject()));
			}
		}
		for (JsonElement e : json.getAsJsonArray("waypoints")) {
			Waypoint w = StoreJson.readWaypoint(e.getAsJsonObject());
			waypoints.put(w.id(), w);
			nextId = Math.max(nextId, w.id() + 1);
		}
		if (json.has("trash")) {
			for (JsonElement e : json.getAsJsonArray("trash")) {
				JsonObject o = e.getAsJsonObject();
				Waypoint w = StoreJson.readWaypoint(o.getAsJsonObject("waypoint"));
				trash.put(w.id(), new Trashed(w, o.get("deletedAt").getAsLong(), o.get("deletedBy").getAsString()));
				nextId = Math.max(nextId, w.id() + 1);
			}
		}
	}

	private JsonObject write() {
		JsonObject json = new JsonObject();
		json.addProperty("format", FORMAT);
		json.addProperty("serverId", serverId.toString());
		json.addProperty("nextId", nextId);
		JsonArray cats = new JsonArray();
		categories.forEach(c -> cats.add(StoreJson.write(c)));
		json.add("categories", cats);
		JsonArray list = new JsonArray();
		waypoints.values().forEach(w -> list.add(StoreJson.write(w)));
		json.add("waypoints", list);
		JsonArray removed = new JsonArray();
		for (Trashed t : trash.values()) {
			JsonObject o = new JsonObject();
			o.add("waypoint", StoreJson.write(t.waypoint()));
			o.addProperty("deletedAt", t.deletedAt());
			o.addProperty("deletedBy", t.deletedBy());
			removed.add(o);
		}
		json.add("trash", removed);
		return json;
	}

	public void markDirty() {
		if (dirtySince < 0) {
			dirtySince = System.currentTimeMillis();
		}
	}

	/** Called every server tick; saves a couple of seconds after the last burst of changes. */
	public void tick() {
		if (dirtySince >= 0 && System.currentTimeMillis() - dirtySince >= SAVE_DELAY_MS) {
			save();
		}
	}

	public void save() {
		if (dirtySince < 0 || loadError != null) {
			return;
		}
		dirtySince = -1;
		try {
			Files.createDirectories(dir);
			Path tmp = dir.resolve("waypoints.json.tmp");
			Files.writeString(tmp, GSON.toJson(write()), StandardCharsets.UTF_8);
			if (Files.exists(file)) {
				Files.copy(file, dir.resolve("waypoints.json.bak"), StandardCopyOption.REPLACE_EXISTING);
			}
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			ApolloWaypoints.LOGGER.error("Cannot save {}", file, e);
			markDirty();
		}
	}

	/** Drops trash entries older than {@code days}. */
	public void purgeTrash(int days) {
		long cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(days);
		if (trash.values().removeIf(t -> t.deletedAt() < cutoff)) {
			markDirty();
		}
	}

	public boolean isReadOnly() {
		return loadError != null;
	}

	public String loadError() {
		return loadError;
	}

	public Path dir() {
		return dir;
	}

	public UUID serverId() {
		return serverId;
	}

	public int allocateId() {
		markDirty();
		return nextId++;
	}

	public Waypoint get(int id) {
		return waypoints.get(id);
	}

	public Collection<Waypoint> all() {
		return Collections.unmodifiableCollection(waypoints.values());
	}

	public void put(Waypoint waypoint) {
		waypoints.put(waypoint.id(), waypoint);
		markDirty();
	}

	public void moveToTrash(Waypoint waypoint, String deletedBy) {
		waypoints.remove(waypoint.id());
		trash.put(waypoint.id(), new Trashed(waypoint, System.currentTimeMillis(), deletedBy));
		markDirty();
	}

	public Trashed getTrashed(int id) {
		return trash.get(id);
	}

	public Collection<Trashed> trash() {
		return Collections.unmodifiableCollection(trash.values());
	}

	public void takeFromTrash(int id) {
		trash.remove(id);
		markDirty();
	}

	public List<Category> categories() {
		return Collections.unmodifiableList(categories);
	}

	public Category category(String id) {
		for (Category c : categories) {
			if (c.id().equals(id)) {
				return c;
			}
		}
		return null;
	}

	public void putCategory(Category category) {
		for (int i = 0; i < categories.size(); i++) {
			if (categories.get(i).id().equals(category.id())) {
				categories.set(i, category);
				markDirty();
				return;
			}
		}
		categories.add(category);
		markDirty();
	}

	public void removeCategory(String id) {
		categories.removeIf(c -> c.id().equals(id));
		markDirty();
	}
}
