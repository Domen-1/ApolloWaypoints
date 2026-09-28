package com.domen.apollowaypoints.format;

import com.domen.apollowaypoints.model.Colors;
import com.domen.apollowaypoints.model.Dimensions;

/**
 * Xaero's chat sharing format, as written by WaypointSharingHandler in Minimap 26.5.3:
 * {@code xaero-waypoint:name:symbol:x:y|~:z:color:rotate:yaw:Internal-<dimension>}.
 * Xaero turns such a message into an [Add] button, including system messages from the server.
 */
public final class XaeroShare {
	public static final String PREFIX = "xaero-waypoint:";
	private static final String INTERNAL = "Internal-";
	private static final String COLON = "^col^";

	/** A parsed share. {@code dimension} is null when the waypoint came from another world. */
	public record Shared(String name, String symbol, int x, Integer y, int z, int color, Integer yaw, String dimension) {
	}

	private XaeroShare() {
	}

	public static String build(String name, String symbol, int x, Integer y, int z, int color, Integer yaw, String dimension) {
		return PREFIX
			+ escape(name) + ":"
			+ escape(symbol) + ":"
			+ x + ":"
			+ (y == null ? "~" : y) + ":"
			+ z + ":"
			+ Colors.clamp(color) + ":"
			+ (yaw != null) + ":"
			+ (yaw == null ? 0 : yaw) + ":"
			+ INTERNAL + escape(destinationToken(dimension));
	}

	/** Returns null if the message is not a well-formed share. */
	public static Shared parse(String message) {
		if (message == null) {
			return null;
		}
		String text = message.trim();
		if (!text.startsWith(PREFIX)) {
			return null;
		}
		String[] parts = text.split(":", -1);
		if (parts.length != 10) {
			return null;
		}
		try {
			String name = unescape(parts[1]);
			String symbol = unescape(parts[2]);
			int x = Integer.parseInt(parts[3]);
			Integer y = "~".equals(parts[4]) ? null : Integer.valueOf(parts[4]);
			int z = Integer.parseInt(parts[5]);
			int color = Integer.parseInt(parts[6]);
			boolean rotate = Boolean.parseBoolean(parts[7]);
			int yaw = Integer.parseInt(parts[8]);
			return new Shared(name, symbol, x, y, z, Colors.clamp(color), rotate ? yaw : null, parseDestination(parts[9]));
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private static String destinationToken(String dimension) {
		return switch (dimension) {
			case Dimensions.OVERWORLD -> "overworld";
			case Dimensions.NETHER -> "the_nether";
			case Dimensions.END -> "the_end";
			default -> {
				int separator = dimension.indexOf(':');
				String namespace = separator < 0 ? "minecraft" : dimension.substring(0, separator);
				String path = separator < 0 ? dimension : dimension.substring(separator + 1);
				yield "dim%" + namespace + "$" + path.replace('/', '%');
			}
		};
	}

	private static String parseDestination(String destination) {
		if (!destination.startsWith(INTERNAL)) {
			return null;
		}
		String token = unescape(destination.substring(INTERNAL.length()));
		int slash = token.indexOf('/');
		if (slash >= 0) {
			token = token.substring(0, slash);
		}
		// Older Xaero versions wrote "Internal-overworld-waypoints".
		if (token.endsWith("_waypoints")) {
			token = token.substring(0, token.length() - "_waypoints".length());
		}
		return switch (token) {
			case "overworld", "dim%0" -> Dimensions.OVERWORLD;
			case "the_nether", "dim%-1" -> Dimensions.NETHER;
			case "the_end", "dim%1" -> Dimensions.END;
			default -> {
				if (!token.startsWith("dim%")) {
					yield null;
				}
				String id = token.substring("dim%".length());
				int separator = id.indexOf('$');
				yield separator < 0 ? null : id.substring(0, separator) + ":" + id.substring(separator + 1).replace('%', '/');
			}
		};
	}

	// Xaero's removeFormatting/restoreFormatting plus its ":" escape; the order matters.
	private static String escape(String text) {
		return text.replace(":", COLON).replace("-", "^min^").replace("_", "-").replace("*", "^ast^");
	}

	private static String unescape(String text) {
		return text.replace("^ast^", "*").replace("-", "_").replace("^min^", "-").replace(COLON, ":");
	}
}
