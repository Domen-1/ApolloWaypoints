package com.domen.apollowaypoints.model;

import java.util.Locale;

/**
 * Xaero's waypoint palette (xaero.hud.minimap.waypoint.WaypointColor in Minimap 26.5.3).
 * The index is what Xaero stores in files and share messages. The first 16 are the chat colors;
 * the last 5 Xaero takes from DyeColor.getTextColor(), copied here so the server does not need Xaero.
 */
public final class Colors {
	public static final int COUNT = 21;

	private static final String[] IDS = {
		"black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple", "gold", "gray",
		"dark_gray", "blue", "green", "aqua", "red", "purple", "yellow", "white",
		"magenta", "light_blue", "lime", "pink", "brown"
	};

	private static final String[] NAMES = {
		"чёрный", "тёмно-синий", "тёмно-зелёный", "тёмно-бирюзовый", "тёмно-красный", "фиолетовый", "золотой", "серый",
		"тёмно-серый", "синий", "зелёный", "бирюзовый", "красный", "сиреневый", "жёлтый", "белый",
		"пурпурный", "голубой", "лаймовый", "розовый", "коричневый"
	};

	private static final int[] RGB = {
		0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
		0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF0000, 0xFF55FF, 0xFFFF55, 0xFFFFFF,
		0xFF00FF, 0x9AC0CD, 0xBFFF00, 0xFF69B4, 0x8B4513
	};

	private Colors() {
	}

	public static boolean isValid(int index) {
		return index >= 0 && index < COUNT;
	}

	public static int clamp(int index) {
		return isValid(index) ? index : 15;
	}

	public static String id(int index) {
		return IDS[clamp(index)];
	}

	public static String name(int index) {
		return NAMES[clamp(index)];
	}

	public static int rgb(int index) {
		return RGB[clamp(index)];
	}

	/** Accepts the English id ("gold"), the Russian name ("золотой") or the number. Returns -1 if unknown. */
	public static int parse(String text) {
		if (text == null) {
			return -1;
		}
		String key = text.trim().toLowerCase(Locale.ROOT);
		for (int i = 0; i < COUNT; i++) {
			if (IDS[i].equals(key) || NAMES[i].equals(key)) {
				return i;
			}
		}
		try {
			int index = Integer.parseInt(key);
			return isValid(index) ? index : -1;
		} catch (NumberFormatException e) {
			return -1;
		}
	}

	public static String[] ids() {
		return IDS.clone();
	}
}
