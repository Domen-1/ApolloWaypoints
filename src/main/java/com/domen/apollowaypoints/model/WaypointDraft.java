package com.domen.apollowaypoints.model;

/**
 * The fields a player can set on a waypoint. Commands, the client screen and imports all build one of these;
 * the server validates it and turns it into a {@link Waypoint}.
 */
public final class WaypointDraft {
	public String name = "";
	/** Empty means "first letter of the name". */
	public String symbol = "";
	public String dimension = "minecraft:overworld";
	public int x;
	/** Null means the waypoint has no height (Xaero shows "~"). */
	public Integer y;
	public int z;
	/** Null means teleporting keeps the player's rotation. */
	public Integer yaw;
	/** -1 means "the category's color". */
	public int color = -1;
	/** Empty means the server's default category. */
	public String category = "";
	/** Null means "the category's visibility". */
	public Visibility visibility;
	public String description = "";

	public WaypointDraft copy() {
		WaypointDraft copy = new WaypointDraft();
		copy.name = name;
		copy.symbol = symbol;
		copy.dimension = dimension;
		copy.x = x;
		copy.y = y;
		copy.z = z;
		copy.yaw = yaw;
		copy.color = color;
		copy.category = category;
		copy.visibility = visibility;
		copy.description = description;
		return copy;
	}
}
