package com.domen.apollowaypoints.net;

import com.domen.apollowaypoints.model.Category;
import com.domen.apollowaypoints.model.Visibility;
import com.domen.apollowaypoints.model.Waypoint;
import com.domen.apollowaypoints.model.WaypointDraft;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class PayloadCodecTest {
	private static final Waypoint FULL = new Waypoint(12, "Железная ферма", "Ж", "minecraft:overworld", -1234, 64, 98765, 90, 6,
		"farms", Visibility.GLOBAL, "AFK на плите", UUID.randomUUID(), "Obabok", 1000L, "Domen", 2000L);
	private static final Waypoint BARE = new Waypoint(1, "Hub", "H", "minecraft:the_nether", 0, null, 0, null, 15,
		"misc", Visibility.MAP, "", Waypoint.CONSOLE, "Сервер", 1L, "Сервер", 1L);

	private static <T> T roundTrip(StreamCodec<RegistryFriendlyByteBuf, T> codec, T value) {
		RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
		codec.encode(buf, value);
		T decoded = codec.decode(buf);
		assertFalse(buf.isReadable(), "codec left unread bytes");
		return decoded;
	}

	@Test
	void waypoints() {
		assertEquals(List.of(FULL, BARE), roundTrip(Payloads.Snapshot.CODEC, new Payloads.Snapshot(List.of(FULL, BARE))).waypoints());
		Payloads.Upserted upserted = roundTrip(Payloads.Upserted.CODEC, new Payloads.Upserted(FULL, Payloads.Change.RESTORED, "Domen"));
		assertEquals(FULL, upserted.waypoint());
		assertEquals(Payloads.Change.RESTORED, upserted.change());
		assertEquals(new Payloads.Removed(7, "Obabok"), roundTrip(Payloads.Removed.CODEC, new Payloads.Removed(7, "Obabok")));
	}

	@Test
	void welcomeAndCategories() {
		Payloads.Welcome welcome = new Payloads.Welcome(Payloads.PROTOCOL, UUID.randomUUID(), PermissionFlags.ADD | PermissionFlags.TP, Category.defaults());
		assertEquals(welcome, roundTrip(Payloads.Welcome.CODEC, welcome));
		assertEquals(Category.defaults(), roundTrip(Payloads.Categories.CODEC, new Payloads.Categories(Category.defaults())).categories());
	}

	@Test
	void drafts() {
		WaypointDraft d = FULL.draft();
		WaypointDraft decoded = roundTrip(Payloads.Create.CODEC, new Payloads.Create(3, d)).draft();
		assertEquals(FULL.name(), decoded.name);
		assertEquals(FULL.y(), decoded.y);
		assertEquals(FULL.yaw(), decoded.yaw);
		assertEquals(FULL.visibility(), decoded.visibility);
		assertEquals(FULL.description(), decoded.description);

		WaypointDraft empty = new WaypointDraft();
		WaypointDraft emptyDecoded = roundTrip(Payloads.Update.CODEC, new Payloads.Update(4, 9, empty)).draft();
		assertNull(emptyDecoded.y);
		assertNull(emptyDecoded.visibility);
		assertEquals(-1, emptyDecoded.color);

		List<WaypointDraft> chunk = roundTrip(Payloads.Import.CODEC, new Payloads.Import(5, List.of(d, empty))).drafts();
		assertEquals(2, chunk.size());
	}

	@Test
	void smallMessages() {
		assertEquals(new Payloads.Hello(Payloads.PROTOCOL, "0.1.0+26.3"), roundTrip(Payloads.Hello.CODEC, new Payloads.Hello(Payloads.PROTOCOL, "0.1.0+26.3")));
		assertEquals(new Payloads.Response(8, false, "Нет прав"), roundTrip(Payloads.Response.CODEC, new Payloads.Response(8, false, "Нет прав")));
		assertEquals(new Payloads.Remove(1, 2), roundTrip(Payloads.Remove.CODEC, new Payloads.Remove(1, 2)));
		assertEquals(new Payloads.Teleport(3, 4), roundTrip(Payloads.Teleport.CODEC, new Payloads.Teleport(3, 4)));
	}
}
