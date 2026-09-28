package com.domen.apollowaypoints.net;

import com.domen.apollowaypoints.model.Category;
import com.domen.apollowaypoints.model.Visibility;
import com.domen.apollowaypoints.model.Waypoint;
import com.domen.apollowaypoints.model.WaypointDraft;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

/** Wire format of the model classes. Changing anything here means bumping {@link Payloads#PROTOCOL}. */
final class Codecs {
	private static final int MAX_TEXT = 1024;

	private Codecs() {
	}

	static void writeWaypoint(FriendlyByteBuf buf, Waypoint w) {
		buf.writeVarInt(w.id());
		buf.writeUtf(w.name(), MAX_TEXT);
		buf.writeUtf(w.symbol(), MAX_TEXT);
		buf.writeUtf(w.dimension(), MAX_TEXT);
		buf.writeInt(w.x());
		writeOptionalInt(buf, w.y());
		buf.writeInt(w.z());
		writeOptionalInt(buf, w.yaw());
		buf.writeVarInt(w.color());
		buf.writeUtf(w.category(), MAX_TEXT);
		buf.writeVarInt(w.visibility().ordinal());
		buf.writeUtf(w.description(), MAX_TEXT);
		buf.writeUUID(w.owner());
		buf.writeUtf(w.ownerName(), MAX_TEXT);
		buf.writeLong(w.createdAt());
		buf.writeUtf(w.updatedBy(), MAX_TEXT);
		buf.writeLong(w.updatedAt());
	}

	static Waypoint readWaypoint(FriendlyByteBuf buf) {
		return new Waypoint(
			buf.readVarInt(),
			buf.readUtf(MAX_TEXT),
			buf.readUtf(MAX_TEXT),
			buf.readUtf(MAX_TEXT),
			buf.readInt(),
			readOptionalInt(buf),
			buf.readInt(),
			readOptionalInt(buf),
			buf.readVarInt(),
			buf.readUtf(MAX_TEXT),
			Visibility.byOrdinal(buf.readVarInt()),
			buf.readUtf(MAX_TEXT),
			buf.readUUID(),
			buf.readUtf(MAX_TEXT),
			buf.readLong(),
			buf.readUtf(MAX_TEXT),
			buf.readLong()
		);
	}

	static void writeDraft(FriendlyByteBuf buf, WaypointDraft d) {
		buf.writeUtf(d.name, MAX_TEXT);
		buf.writeUtf(d.symbol, MAX_TEXT);
		buf.writeUtf(d.dimension, MAX_TEXT);
		buf.writeInt(d.x);
		writeOptionalInt(buf, d.y);
		buf.writeInt(d.z);
		writeOptionalInt(buf, d.yaw);
		buf.writeVarInt(d.color);
		buf.writeUtf(d.category, MAX_TEXT);
		buf.writeVarInt(d.visibility == null ? -1 : d.visibility.ordinal());
		buf.writeUtf(d.description, MAX_TEXT);
	}

	static WaypointDraft readDraft(FriendlyByteBuf buf) {
		WaypointDraft d = new WaypointDraft();
		d.name = buf.readUtf(MAX_TEXT);
		d.symbol = buf.readUtf(MAX_TEXT);
		d.dimension = buf.readUtf(MAX_TEXT);
		d.x = buf.readInt();
		d.y = readOptionalInt(buf);
		d.z = buf.readInt();
		d.yaw = readOptionalInt(buf);
		d.color = buf.readVarInt();
		d.category = buf.readUtf(MAX_TEXT);
		int visibility = buf.readVarInt();
		d.visibility = visibility < 0 ? null : Visibility.byOrdinal(visibility);
		d.description = buf.readUtf(MAX_TEXT);
		return d;
	}

	static void writeCategory(FriendlyByteBuf buf, Category c) {
		buf.writeUtf(c.id(), MAX_TEXT);
		buf.writeUtf(c.name(), MAX_TEXT);
		buf.writeVarInt(c.color());
		buf.writeVarInt(c.visibility().ordinal());
	}

	static Category readCategory(FriendlyByteBuf buf) {
		return new Category(buf.readUtf(MAX_TEXT), buf.readUtf(MAX_TEXT), buf.readVarInt(), Visibility.byOrdinal(buf.readVarInt()));
	}

	static <T> void writeList(FriendlyByteBuf buf, List<T> list, BiConsumer<FriendlyByteBuf, T> writer) {
		buf.writeVarInt(list.size());
		for (T item : list) {
			writer.accept(buf, item);
		}
	}

	static <T> List<T> readList(FriendlyByteBuf buf, Function<FriendlyByteBuf, T> reader, int max) {
		int size = buf.readVarInt();
		if (size < 0 || size > max) {
			throw new IllegalArgumentException("List too long: " + size);
		}
		List<T> list = new ArrayList<>(size);
		for (int i = 0; i < size; i++) {
			list.add(reader.apply(buf));
		}
		return list;
	}

	private static void writeOptionalInt(FriendlyByteBuf buf, Integer value) {
		buf.writeBoolean(value != null);
		if (value != null) {
			buf.writeInt(value);
		}
	}

	private static Integer readOptionalInt(FriendlyByteBuf buf) {
		return buf.readBoolean() ? buf.readInt() : null;
	}
}
