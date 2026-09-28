package com.domen.apollowaypoints.server;

import com.domen.apollowaypoints.ApolloWaypoints;
import com.domen.apollowaypoints.model.Visibility;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.permissions.PermissionLevel;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/** {@code config/apollowaypoints.json}. Missing keys get defaults; a broken file is left alone and defaults are used. */
public final class ServerConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	public enum ShareMode {
		/** Ignore Xaero share messages in chat. */
		OFF,
		/** Offer the sender to add the shared waypoint to the server. */
		PROMPT,
		/** Offer it and keep the share message out of public chat. */
		PROMPT_AND_HIDE
	}

	public final Map<Perms.Perm, PermissionLevel> levels = new EnumMap<>(Perms.Perm.class);
	public boolean tpInSpectator = true;
	/** 0 means no limit. */
	public int maxWaypointsPerPlayer = 0;
	public int trashDays = 30;
	public ShareMode shareMode = ShareMode.PROMPT;
	public boolean announceInChat = false;
	public String defaultCategory = "misc";
	public Visibility defaultVisibility = Visibility.LOCAL;

	private ServerConfig() {
		for (Perms.Perm perm : Perms.Perm.values()) {
			levels.put(perm, perm.defaultLevel);
		}
	}

	public PermissionLevel level(Perms.Perm perm) {
		return levels.getOrDefault(perm, perm.defaultLevel);
	}

	public static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve(ApolloWaypoints.MOD_ID + ".json");
	}

	public static ServerConfig load() {
		ServerConfig config = new ServerConfig();
		Path path = path();
		if (!Files.exists(path)) {
			config.save(path);
			return config;
		}
		try {
			JsonObject json = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
			config.read(json);
			// Rewrite so options added in newer versions show up in the file.
			config.save(path);
		} catch (Exception e) {
			ApolloWaypoints.LOGGER.error("Cannot read {}, using defaults until it is fixed", path, e);
		}
		return config;
	}

	private void read(JsonObject json) {
		if (json.has("permissions")) {
			JsonObject perms = json.getAsJsonObject("permissions");
			for (Perms.Perm perm : Perms.Perm.values()) {
				if (perms.has(perm.key)) {
					PermissionLevel level = parseLevel(perms.get(perm.key).getAsString());
					if (level == null) {
						ApolloWaypoints.LOGGER.warn("Unknown permission level '{}' for {}", perms.get(perm.key).getAsString(), perm.key);
					} else {
						levels.put(perm, level);
					}
				}
			}
		}
		if (json.has("tpInSpectator")) {
			tpInSpectator = json.get("tpInSpectator").getAsBoolean();
		}
		if (json.has("maxWaypointsPerPlayer")) {
			maxWaypointsPerPlayer = Math.max(0, json.get("maxWaypointsPerPlayer").getAsInt());
		}
		if (json.has("trashDays")) {
			trashDays = Math.max(0, json.get("trashDays").getAsInt());
		}
		if (json.has("shareMode")) {
			try {
				shareMode = ShareMode.valueOf(json.get("shareMode").getAsString().toUpperCase(Locale.ROOT));
			} catch (IllegalArgumentException e) {
				ApolloWaypoints.LOGGER.warn("Unknown shareMode '{}'", json.get("shareMode").getAsString());
			}
		}
		if (json.has("announceInChat")) {
			announceInChat = json.get("announceInChat").getAsBoolean();
		}
		if (json.has("defaultCategory")) {
			defaultCategory = json.get("defaultCategory").getAsString();
		}
		if (json.has("defaultVisibility")) {
			Visibility visibility = Visibility.byId(json.get("defaultVisibility").getAsString());
			if (visibility != null) {
				defaultVisibility = visibility;
			}
		}
	}

	private void save(Path path) {
		JsonObject json = new JsonObject();
		JsonObject perms = new JsonObject();
		for (Perms.Perm perm : Perms.Perm.values()) {
			perms.addProperty(perm.key, level(perm).name().toLowerCase(Locale.ROOT));
		}
		json.add("permissions", perms);
		json.addProperty("tpInSpectator", tpInSpectator);
		json.addProperty("maxWaypointsPerPlayer", maxWaypointsPerPlayer);
		json.addProperty("trashDays", trashDays);
		json.addProperty("shareMode", shareMode.name().toLowerCase(Locale.ROOT));
		json.addProperty("announceInChat", announceInChat);
		json.addProperty("defaultCategory", defaultCategory);
		json.addProperty("defaultVisibility", defaultVisibility.id);
		try {
			Files.createDirectories(path.getParent());
			Files.writeString(path, GSON.toJson(json), StandardCharsets.UTF_8);
		} catch (IOException e) {
			ApolloWaypoints.LOGGER.error("Cannot write {}", path, e);
		}
	}

	/** "all", "moderators", "gamemasters", "admins", "owners" or the op level 0-4. */
	private static PermissionLevel parseLevel(String text) {
		String key = text.trim().toUpperCase(Locale.ROOT);
		for (PermissionLevel level : PermissionLevel.values()) {
			if (level.name().equals(key)) {
				return level;
			}
		}
		try {
			return PermissionLevel.byId(Integer.parseInt(key));
		} catch (RuntimeException e) {
			return null;
		}
	}
}
