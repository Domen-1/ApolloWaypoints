package com.domen.apollowaypoints.client;

import com.domen.apollowaypoints.ApolloWaypoints;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Per-player preferences in {@code config/apollowaypoints-client.json}. Waypoints switched off ("hidden") and hidden
 * categories are stored per server (by the server's waypoint database id), because waypoint numbers only mean something
 * on their server.
 */
public final class ClientSettings {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private static final class ServerPrefs {
		final Set<Integer> hidden = new HashSet<>();
		final Set<String> hiddenCategories = new HashSet<>();
	}

	private static boolean toasts = true;
	private static final Map<UUID, ServerPrefs> servers = new HashMap<>();

	private ClientSettings() {
	}

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve(ApolloWaypoints.MOD_ID + "-client.json");
	}

	static void load() {
		Path path = path();
		if (!Files.exists(path)) {
			return;
		}
		try {
			JsonObject json = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
			if (json.has("toasts")) {
				toasts = json.get("toasts").getAsBoolean();
			}
			if (json.has("servers")) {
				for (Map.Entry<String, JsonElement> e : json.getAsJsonObject("servers").entrySet()) {
					ServerPrefs prefs = new ServerPrefs();
					JsonObject o = e.getValue().getAsJsonObject();
					if (o.has("hidden")) {
						o.getAsJsonArray("hidden").forEach(id -> prefs.hidden.add(id.getAsInt()));
					}
					if (o.has("hiddenCategories")) {
						o.getAsJsonArray("hiddenCategories").forEach(id -> prefs.hiddenCategories.add(id.getAsString()));
					}
					servers.put(UUID.fromString(e.getKey()), prefs);
				}
			}
		} catch (Exception e) {
			ApolloWaypoints.LOGGER.warn("Cannot read {}, using defaults", path, e);
		}
	}

	private static void save() {
		JsonObject json = new JsonObject();
		json.addProperty("toasts", toasts);
		JsonObject serversJson = new JsonObject();
		servers.forEach((id, prefs) -> {
			JsonObject o = new JsonObject();
			JsonArray hidden = new JsonArray();
			prefs.hidden.stream().sorted().forEach(hidden::add);
			o.add("hidden", hidden);
			JsonArray categories = new JsonArray();
			prefs.hiddenCategories.stream().sorted().forEach(categories::add);
			o.add("hiddenCategories", categories);
			serversJson.add(id.toString(), o);
		});
		json.add("servers", serversJson);
		try {
			Files.createDirectories(path().getParent());
			Files.writeString(path(), GSON.toJson(json), StandardCharsets.UTF_8);
		} catch (IOException e) {
			ApolloWaypoints.LOGGER.warn("Cannot write {}", path(), e);
		}
	}

	private static ServerPrefs prefs() {
		UUID serverId = ClientState.serverId();
		return serverId == null ? new ServerPrefs() : servers.computeIfAbsent(serverId, id -> new ServerPrefs());
	}

	public static boolean toasts() {
		return toasts;
	}

	public static void setToasts(boolean enabled) {
		toasts = enabled;
		save();
	}

	public static boolean isHidden(int waypointId) {
		return prefs().hidden.contains(waypointId);
	}

	/** Writes the file once, however many waypoints change. */
	public static void setHidden(Collection<Integer> waypointIds, boolean hidden) {
		if (ClientState.serverId() == null || waypointIds.isEmpty()) {
			return;
		}
		if (hidden ? prefs().hidden.addAll(waypointIds) : prefs().hidden.removeAll(waypointIds)) {
			save();
		}
	}

	public static boolean isCategoryHidden(String category) {
		return prefs().hiddenCategories.contains(category);
	}

	public static void setCategoryHidden(String category, boolean hidden) {
		if (ClientState.serverId() == null) {
			return;
		}
		if (hidden ? prefs().hiddenCategories.add(category) : prefs().hiddenCategories.remove(category)) {
			save();
		}
	}
}
