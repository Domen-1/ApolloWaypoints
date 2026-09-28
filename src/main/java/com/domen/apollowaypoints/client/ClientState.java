package com.domen.apollowaypoints.client;

import com.domen.apollowaypoints.model.Category;
import com.domen.apollowaypoints.model.Waypoint;
import com.domen.apollowaypoints.net.Payloads;
import com.domen.apollowaypoints.net.PermissionFlags;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** What the server told us. Changed only by packets, on the client thread; {@link #version()} tells screens to refresh. */
public final class ClientState {
	private static boolean synced;
	private static UUID serverId;
	private static int permissions;
	private static List<Category> categories = List.of();
	private static final Map<Integer, Waypoint> waypoints = new LinkedHashMap<>();
	private static int version;

	private ClientState() {
	}

	static void reset() {
		synced = false;
		serverId = null;
		permissions = 0;
		categories = List.of();
		waypoints.clear();
		version++;
	}

	static void welcome(Payloads.Welcome welcome) {
		serverId = welcome.serverId();
		permissions = welcome.permissions();
		categories = List.copyOf(welcome.categories());
		version++;
	}

	static void snapshot(List<Waypoint> list) {
		waypoints.clear();
		for (Waypoint w : list) {
			waypoints.put(w.id(), w);
		}
		synced = true;
		version++;
	}

	static void upsert(Waypoint w) {
		waypoints.put(w.id(), w);
		version++;
	}

	static void remove(int id) {
		waypoints.remove(id);
		version++;
	}

	static void categories(List<Category> list) {
		categories = List.copyOf(list);
		version++;
	}

	/** True once the first snapshot arrived: the server has the mod and we may show its waypoints. */
	public static boolean isSynced() {
		return synced;
	}

	public static UUID serverId() {
		return serverId;
	}

	public static int version() {
		return version;
	}

	public static Collection<Waypoint> waypoints() {
		return Collections.unmodifiableCollection(waypoints.values());
	}

	public static Waypoint get(int id) {
		return waypoints.get(id);
	}

	public static List<Category> categories() {
		return categories;
	}

	public static Category category(String id) {
		for (Category c : categories) {
			if (c.id().equals(id)) {
				return c;
			}
		}
		return null;
	}

	public static List<String> categoryIds() {
		List<String> ids = new ArrayList<>();
		categories.forEach(c -> ids.add(c.id()));
		return ids;
	}

	public static boolean can(int flag) {
		return PermissionFlags.has(permissions, flag);
	}

	public static boolean canEdit(Waypoint w) {
		return can(isMine(w) ? PermissionFlags.EDIT_OWN : PermissionFlags.EDIT_ANY);
	}

	public static boolean canRemove(Waypoint w) {
		return can(isMine(w) ? PermissionFlags.REMOVE_OWN : PermissionFlags.REMOVE_ANY);
	}

	public static boolean canTeleportNow() {
		LocalPlayer player = Minecraft.getInstance().player;
		return can(PermissionFlags.TP) || (can(PermissionFlags.TP_IN_SPECTATOR) && player != null && player.isSpectator());
	}

	private static boolean isMine(Waypoint w) {
		LocalPlayer player = Minecraft.getInstance().player;
		return player != null && w.owner().equals(player.getUUID());
	}
}
