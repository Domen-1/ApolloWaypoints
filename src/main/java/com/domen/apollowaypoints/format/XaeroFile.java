package com.domen.apollowaypoints.format;

import com.domen.apollowaypoints.model.Colors;

import java.util.ArrayList;
import java.util.List;

/**
 * Xaero's waypoint file format (WaypointIO in Minimap 26.5.3), e.g. {@code xaero/minimap/<server>/dim%0/mw$default_1.txt}:
 * {@code waypoint:name:initials:x:y:z:color:disabled:type:set:rotate_on_tp:tp_yaw:visibility_type:destination}.
 * Colons inside names are stored as "§§".
 */
public final class XaeroFile {
	public static final String HEADER = "#waypoint:name:initials:x:y:z:color:disabled:type:set:rotate_on_tp:tp_yaw:visibility_type:destination";
	public static final String DEFAULT_SET = "gui.xaero_default";
	/** Xaero's waypoint type for normal waypoints; 1 and 2 are deathpoints. */
	public static final int TYPE_NORMAL = 0;

	public record Entry(String name, String symbol, int x, Integer y, int z, int color, boolean disabled, int type,
		String set, Integer yaw, int visibilityType) {
	}

	private XaeroFile() {
	}

	/** Skips lines that are not waypoints or are malformed. */
	public static List<Entry> parse(List<String> lines) {
		List<Entry> entries = new ArrayList<>();
		for (String line : lines) {
			if (!line.startsWith("waypoint:")) {
				continue;
			}
			String[] p = line.split(":", -1);
			if (p.length < 12) {
				continue;
			}
			try {
				Integer y = "~".equals(p[4]) ? null : Integer.valueOf(p[4]);
				boolean rotate = Boolean.parseBoolean(p[10]);
				int yaw = Integer.parseInt(p[11]);
				entries.add(new Entry(
					p[1].replace("§§", ":"),
					p[2].replace("§§", ":"),
					Integer.parseInt(p[3]),
					y,
					Integer.parseInt(p[5]),
					Colors.clamp(Integer.parseInt(p[6])),
					Boolean.parseBoolean(p[7]),
					Integer.parseInt(p[8]),
					p[9],
					rotate ? yaw : null,
					p.length > 12 ? parseVisibility(p[12]) : 0
				));
			} catch (NumberFormatException e) {
				// malformed line, skip it
			}
		}
		return entries;
	}

	public static List<String> write(String setName, List<Entry> entries) {
		List<String> lines = new ArrayList<>();
		lines.add("#");
		lines.add(HEADER);
		lines.add("#");
		lines.add(DEFAULT_SET.equals(setName) ? "sets:" + DEFAULT_SET : "sets:" + DEFAULT_SET + ":" + setName);
		for (Entry e : entries) {
			lines.add("waypoint:"
				+ e.name().replace(":", "§§") + ":"
				+ e.symbol().replace(":", "§§") + ":"
				+ e.x() + ":"
				+ (e.y() == null ? "~" : e.y()) + ":"
				+ e.z() + ":"
				+ e.color() + ":"
				+ e.disabled() + ":"
				+ e.type() + ":"
				+ setName + ":"
				+ (e.yaw() != null) + ":"
				+ (e.yaw() == null ? 0 : e.yaw()) + ":"
				+ e.visibilityType() + ":"
				+ "false");
		}
		return lines;
	}

	private static int parseVisibility(String field) {
		// Old files had a "global" boolean in this position.
		if ("true".equals(field)) {
			return 1;
		}
		if ("false".equals(field)) {
			return 0;
		}
		return Integer.parseInt(field);
	}
}
