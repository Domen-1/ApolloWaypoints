package com.domen.apollowaypoints.model;

public final class Dimensions {
	public static final String OVERWORLD = "minecraft:overworld";
	public static final String NETHER = "minecraft:the_nether";
	public static final String END = "minecraft:the_end";

	private Dimensions() {
	}

	public static String displayName(String dimension) {
		return switch (dimension) {
			case OVERWORLD -> "Верхний мир";
			case NETHER -> "Незер";
			case END -> "Энд";
			default -> dimension;
		};
	}

	/** Chat color index (see {@link Colors}) used for dimension labels. */
	public static int colorIndex(String dimension) {
		return switch (dimension) {
			case OVERWORLD -> 10;
			case NETHER -> 12;
			case END -> 13;
			default -> 7;
		};
	}
}
