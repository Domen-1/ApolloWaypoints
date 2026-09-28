package com.domen.apollowaypoints.model;

import java.util.List;

/** A waypoint group. New waypoints take the category's color and visibility unless set explicitly. */
public record Category(String id, String name, int color, Visibility visibility) {
	public static final String DEFAULT_ID = "misc";

	public static List<Category> defaults() {
		return List.of(
			new Category("farms", "Фермы", 10, Visibility.LOCAL),
			new Category("portals", "Порталы", 13, Visibility.LOCAL),
			new Category("bases", "Базы", 11, Visibility.LOCAL),
			new Category("structures", "Структуры", 6, Visibility.LOCAL),
			new Category(DEFAULT_ID, "Прочее", 15, Visibility.LOCAL)
		);
	}

	public static boolean isValidId(String id) {
		return id != null && id.matches("[a-z0-9_]{1,24}");
	}
}
