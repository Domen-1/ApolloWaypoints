package com.domen.apollowaypoints.server;

import com.domen.apollowaypoints.ApolloWaypoints;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;

/**
 * Append-only log of changes in {@code <world>/apollowaypoints/history.jsonl}, one JSON object per line.
 * The latest entries are also kept in memory for /wp history.
 */
public final class HistoryLog {
	private static final int KEEP_IN_MEMORY = 500;

	public record Entry(long time, String actor, String action, int id, String name, String details) {
	}

	private final Path file;
	private final Deque<Entry> recent = new ArrayDeque<>();

	public HistoryLog(Path dir) {
		this.file = dir.resolve("history.jsonl");
		if (Files.exists(file)) {
			try {
				for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
					if (line.isBlank()) {
						continue;
					}
					JsonObject o = JsonParser.parseString(line).getAsJsonObject();
					remember(new Entry(o.get("time").getAsLong(), o.get("actor").getAsString(), o.get("action").getAsString(),
						o.get("id").getAsInt(), o.get("name").getAsString(), o.has("details") ? o.get("details").getAsString() : ""));
				}
			} catch (Exception e) {
				ApolloWaypoints.LOGGER.warn("Cannot read {}, history starts empty", file, e);
			}
		}
	}

	public void add(String actor, String action, int id, String name, String details) {
		Entry entry = new Entry(System.currentTimeMillis(), actor, action, id, name, details);
		remember(entry);
		JsonObject o = new JsonObject();
		o.addProperty("time", entry.time());
		o.addProperty("actor", actor);
		o.addProperty("action", action);
		o.addProperty("id", id);
		o.addProperty("name", name);
		if (!details.isEmpty()) {
			o.addProperty("details", details);
		}
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, o + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		} catch (IOException e) {
			ApolloWaypoints.LOGGER.error("Cannot append to {}", file, e);
		}
	}

	/** Newest first; {@code id} null means all waypoints. */
	public List<Entry> latest(Integer id, int limit) {
		List<Entry> result = new ArrayList<>();
		for (Iterator<Entry> it = recent.descendingIterator(); it.hasNext() && result.size() < limit; ) {
			Entry entry = it.next();
			if (id == null || entry.id() == id) {
				result.add(entry);
			}
		}
		return result;
	}

	private void remember(Entry entry) {
		recent.addLast(entry);
		while (recent.size() > KEEP_IN_MEMORY) {
			recent.removeFirst();
		}
	}
}
