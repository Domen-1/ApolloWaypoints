package com.domen.apollowaypoints.model;

import java.util.UUID;

/** A stored waypoint. Immutable: edits build a new instance through {@link #draft()}. */
public record Waypoint(
	int id,
	String name,
	String symbol,
	String dimension,
	int x,
	Integer y,
	int z,
	Integer yaw,
	int color,
	String category,
	Visibility visibility,
	String description,
	UUID owner,
	String ownerName,
	long createdAt,
	String updatedBy,
	long updatedAt
) {
	/** Owner of waypoints created from the server console or RCON. */
	public static final UUID CONSOLE = new UUID(0L, 0L);

	public WaypointDraft draft() {
		WaypointDraft draft = new WaypointDraft();
		draft.name = name;
		draft.symbol = symbol;
		draft.dimension = dimension;
		draft.x = x;
		draft.y = y;
		draft.z = z;
		draft.yaw = yaw;
		draft.color = color;
		draft.category = category;
		draft.visibility = visibility;
		draft.description = description;
		return draft;
	}

	public boolean hasY() {
		return y != null;
	}

	public String coordinates() {
		return x + " " + (y == null ? "~" : y) + " " + z;
	}

	public double distanceSq(double px, double py, double pz) {
		double dx = x + 0.5 - px;
		double dy = y == null ? 0 : y + 0.5 - py;
		double dz = z + 0.5 - pz;
		return dx * dx + dy * dy + dz * dz;
	}
}
