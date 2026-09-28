package com.domen.apollowaypoints.model;

import java.util.Locale;

/** Field rules shared by the server (authoritative) and the client screen (early feedback). Errors are in Russian. */
public final class Validation {
	public static final int MAX_NAME = 32;
	public static final int MAX_SYMBOL = 2;
	public static final int MAX_DESCRIPTION = 256;
	public static final int MAX_HORIZONTAL = 30_000_000;
	public static final int MIN_Y = -2048;
	public static final int MAX_Y = 4096;

	private Validation() {
	}

	/** Returns an error message or null. */
	public static String checkName(String name) {
		if (name == null || name.isBlank()) {
			return "Имя не может быть пустым";
		}
		if (name.length() > MAX_NAME) {
			return "Имя длиннее " + MAX_NAME + " символов";
		}
		if (name.startsWith("#")) {
			return "Имя не может начинаться с # — так пишутся номера точек";
		}
		if (name.chars().allMatch(Character::isDigit)) {
			return "Имя не может состоять из одних цифр — так пишутся номера точек";
		}
		if (hasForbiddenChars(name)) {
			return "В имени есть недопустимые символы";
		}
		return null;
	}

	public static String checkSymbol(String symbol) {
		if (symbol == null || symbol.isEmpty()) {
			return null;
		}
		if (symbol.codePointCount(0, symbol.length()) > MAX_SYMBOL) {
			return "Символ — не больше " + MAX_SYMBOL + " знаков";
		}
		if (symbol.isBlank() || hasForbiddenChars(symbol)) {
			return "В символе есть недопустимые знаки";
		}
		return null;
	}

	public static String checkDescription(String description) {
		if (description == null || description.isEmpty()) {
			return null;
		}
		if (description.length() > MAX_DESCRIPTION) {
			return "Описание длиннее " + MAX_DESCRIPTION + " символов";
		}
		if (hasForbiddenChars(description)) {
			return "В описании есть недопустимые символы";
		}
		return null;
	}

	public static String checkPosition(int x, Integer y, int z) {
		if (Math.abs(x) > MAX_HORIZONTAL || Math.abs(z) > MAX_HORIZONTAL) {
			return "Координаты за границей мира";
		}
		if (y != null && (y < MIN_Y || y > MAX_Y)) {
			return "Высота вне допустимых пределов";
		}
		return null;
	}

	public static String defaultSymbol(String name) {
		String trimmed = name == null ? "" : name.trim();
		if (trimmed.isEmpty()) {
			return "?";
		}
		int first = trimmed.codePointAt(0);
		return new String(Character.toChars(first)).toUpperCase(Locale.ROOT);
	}

	private static boolean hasForbiddenChars(String text) {
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c == '§' || Character.isISOControl(c)) {
				return true;
			}
		}
		return false;
	}
}
