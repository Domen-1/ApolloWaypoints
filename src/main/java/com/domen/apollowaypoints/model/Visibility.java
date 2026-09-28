package com.domen.apollowaypoints.model;

import java.util.Locale;

/** Where Xaero draws a waypoint. Ordinals go over the network, so only append new values. */
public enum Visibility {
	LOCAL("local", "в пределах дистанции"),
	GLOBAL("global", "всегда"),
	MAP("map", "только на карте");

	public final String id;
	public final String description;

	Visibility(String id, String description) {
		this.id = id;
		this.description = description;
	}

	public static Visibility byId(String id) {
		if (id == null) {
			return null;
		}
		String key = id.toLowerCase(Locale.ROOT);
		for (Visibility visibility : values()) {
			if (visibility.id.equals(key)) {
				return visibility;
			}
		}
		return null;
	}

	public static Visibility byOrdinal(int ordinal) {
		Visibility[] values = values();
		return ordinal >= 0 && ordinal < values.length ? values[ordinal] : LOCAL;
	}

	/** Xaero's WaypointVisibilityType index: 0 local, 1 global, 2 world map local, 3 world map global. */
	public int xaeroType() {
		return switch (this) {
			case LOCAL -> 0;
			case GLOBAL -> 1;
			case MAP -> 3;
		};
	}

	public static Visibility fromXaeroType(int type) {
		return switch (type) {
			case 1 -> GLOBAL;
			case 2, 3 -> MAP;
			default -> LOCAL;
		};
	}
}
