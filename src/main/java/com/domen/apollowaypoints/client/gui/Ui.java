package com.domen.apollowaypoints.client.gui;

/** ARGB text colors: since 1.21.6 text without an alpha byte is invisible. */
final class Ui {
	static final int WHITE = 0xFFFFFFFF;
	static final int GRAY = 0xFFAAAAAA;
	static final int DARK_GRAY = 0xFF777777;
	static final int GREEN = 0xFF55FF55;
	static final int RED = 0xFFFF5555;
	static final int YELLOW = 0xFFFFFF55;

	private Ui() {
	}

	static int opaque(int rgb) {
		return 0xFF000000 | rgb;
	}

	static Integer parseInt(String text) {
		try {
			return Integer.parseInt(text.trim());
		} catch (NumberFormatException e) {
			return null;
		}
	}
}
