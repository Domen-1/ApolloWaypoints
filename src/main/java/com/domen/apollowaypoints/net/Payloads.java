package com.domen.apollowaypoints.net;

import com.domen.apollowaypoints.ApolloWaypoints;
import com.domen.apollowaypoints.model.Category;
import com.domen.apollowaypoints.model.Waypoint;
import com.domen.apollowaypoints.model.WaypointDraft;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;
import java.util.UUID;

/**
 * Packets between the server and clients that have the mod. Clients without it never send {@link Hello}
 * and never get anything from the server.
 */
public final class Payloads {
	/** Bump on any wire format change; the server refuses to sync clients with a different protocol. */
	public static final int PROTOCOL = 1;
	/** Client-to-server packets are capped at 32 KiB by Minecraft, so imports go in chunks of this size. */
	public static final int IMPORT_CHUNK = 64;
	private static final int MAX_WAYPOINTS = 100_000;
	private static final int MAX_SNAPSHOT_BYTES = 16 * 1024 * 1024;

	private Payloads() {
	}

	public static void register() {
		PayloadTypeRegistry.serverboundPlay().register(Hello.TYPE, Hello.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Create.TYPE, Create.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Update.TYPE, Update.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Remove.TYPE, Remove.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Teleport.TYPE, Teleport.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Import.TYPE, Import.CODEC);

		PayloadTypeRegistry.clientboundPlay().register(Welcome.TYPE, Welcome.CODEC);
		PayloadTypeRegistry.clientboundPlay().registerLarge(Snapshot.TYPE, Snapshot.CODEC, MAX_SNAPSHOT_BYTES);
		PayloadTypeRegistry.clientboundPlay().register(Upserted.TYPE, Upserted.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Removed.TYPE, Removed.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Categories.TYPE, Categories.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Response.TYPE, Response.CODEC);
	}

	private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> typeOf(String path) {
		return new CustomPacketPayload.Type<>(ApolloWaypoints.id(path));
	}

	// ---- client -> server

	public record Hello(int protocol, String modVersion) implements CustomPacketPayload {
		public static final Type<Hello> TYPE = typeOf("hello");
		public static final StreamCodec<RegistryFriendlyByteBuf, Hello> CODEC = CustomPacketPayload.codec(Hello::write, Hello::new);

		private Hello(FriendlyByteBuf buf) {
			this(buf.readVarInt(), buf.readUtf(64));
		}

		private void write(FriendlyByteBuf buf) {
			buf.writeVarInt(protocol);
			buf.writeUtf(modVersion, 64);
		}

		@Override
		public Type<Hello> type() {
			return TYPE;
		}
	}

	public record Create(int requestId, WaypointDraft draft) implements CustomPacketPayload {
		public static final Type<Create> TYPE = typeOf("create");
		public static final StreamCodec<RegistryFriendlyByteBuf, Create> CODEC = CustomPacketPayload.codec(Create::write, Create::new);

		private Create(FriendlyByteBuf buf) {
			this(buf.readVarInt(), Codecs.readDraft(buf));
		}

		private void write(FriendlyByteBuf buf) {
			buf.writeVarInt(requestId);
			Codecs.writeDraft(buf, draft);
		}

		@Override
		public Type<Create> type() {
			return TYPE;
		}
	}

	public record Update(int requestId, int id, WaypointDraft draft) implements CustomPacketPayload {
		public static final Type<Update> TYPE = typeOf("update");
		public static final StreamCodec<RegistryFriendlyByteBuf, Update> CODEC = CustomPacketPayload.codec(Update::write, Update::new);

		private Update(FriendlyByteBuf buf) {
			this(buf.readVarInt(), buf.readVarInt(), Codecs.readDraft(buf));
		}

		private void write(FriendlyByteBuf buf) {
			buf.writeVarInt(requestId);
			buf.writeVarInt(id);
			Codecs.writeDraft(buf, draft);
		}

		@Override
		public Type<Update> type() {
			return TYPE;
		}
	}

	public record Remove(int requestId, int id) implements CustomPacketPayload {
		public static final Type<Remove> TYPE = typeOf("remove");
		public static final StreamCodec<RegistryFriendlyByteBuf, Remove> CODEC = CustomPacketPayload.codec(Remove::write, Remove::new);

		private Remove(FriendlyByteBuf buf) {
			this(buf.readVarInt(), buf.readVarInt());
		}

		private void write(FriendlyByteBuf buf) {
			buf.writeVarInt(requestId);
			buf.writeVarInt(id);
		}

		@Override
		public Type<Remove> type() {
			return TYPE;
		}
	}

	public record Teleport(int requestId, int id) implements CustomPacketPayload {
		public static final Type<Teleport> TYPE = typeOf("teleport");
		public static final StreamCodec<RegistryFriendlyByteBuf, Teleport> CODEC = CustomPacketPayload.codec(Teleport::write, Teleport::new);

		private Teleport(FriendlyByteBuf buf) {
			this(buf.readVarInt(), buf.readVarInt());
		}

		private void write(FriendlyByteBuf buf) {
			buf.writeVarInt(requestId);
			buf.writeVarInt(id);
		}

		@Override
		public Type<Teleport> type() {
			return TYPE;
		}
	}

	/** One chunk of waypoints the player sends from their own Xaero sets. */
	public record Import(int requestId, List<WaypointDraft> drafts) implements CustomPacketPayload {
		public static final Type<Import> TYPE = typeOf("import");
		public static final StreamCodec<RegistryFriendlyByteBuf, Import> CODEC = CustomPacketPayload.codec(Import::write, Import::new);

		private Import(FriendlyByteBuf buf) {
			this(buf.readVarInt(), Codecs.readList(buf, Codecs::readDraft, IMPORT_CHUNK));
		}

		private void write(FriendlyByteBuf buf) {
			buf.writeVarInt(requestId);
			Codecs.writeList(buf, drafts, Codecs::writeDraft);
		}

		@Override
		public Type<Import> type() {
			return TYPE;
		}
	}

	// ---- server -> client

	/** Sent after a matching {@link Hello}; {@code permissions} is a bit set of {@link PermissionFlags}. */
	public record Welcome(int protocol, UUID serverId, int permissions, List<Category> categories) implements CustomPacketPayload {
		public static final Type<Welcome> TYPE = typeOf("welcome");
		public static final StreamCodec<RegistryFriendlyByteBuf, Welcome> CODEC = CustomPacketPayload.codec(Welcome::write, Welcome::new);

		private Welcome(FriendlyByteBuf buf) {
			this(buf.readVarInt(), buf.readUUID(), buf.readVarInt(), Codecs.readList(buf, Codecs::readCategory, 1000));
		}

		private void write(FriendlyByteBuf buf) {
			buf.writeVarInt(protocol);
			buf.writeUUID(serverId);
			buf.writeVarInt(permissions);
			Codecs.writeList(buf, categories, Codecs::writeCategory);
		}

		@Override
		public Type<Welcome> type() {
			return TYPE;
		}
	}

	public record Snapshot(List<Waypoint> waypoints) implements CustomPacketPayload {
		public static final Type<Snapshot> TYPE = typeOf("snapshot");
		public static final StreamCodec<RegistryFriendlyByteBuf, Snapshot> CODEC = CustomPacketPayload.codec(Snapshot::write, Snapshot::new);

		private Snapshot(FriendlyByteBuf buf) {
			this(Codecs.readList(buf, Codecs::readWaypoint, MAX_WAYPOINTS));
		}

		private void write(FriendlyByteBuf buf) {
			Codecs.writeList(buf, waypoints, Codecs::writeWaypoint);
		}

		@Override
		public Type<Snapshot> type() {
			return TYPE;
		}
	}

	/** Clients show a toast for CREATED and RESTORED; IMPORTED comes in bulk and stays quiet. */
	public enum Change {
		CREATED, UPDATED, RESTORED, IMPORTED;

		static Change byOrdinal(int ordinal) {
			Change[] values = values();
			return ordinal >= 0 && ordinal < values.length ? values[ordinal] : UPDATED;
		}
	}

	/** A waypoint was created, edited or restored; {@code actor} is who did it, for the client's toast. */
	public record Upserted(Waypoint waypoint, Change change, String actor) implements CustomPacketPayload {
		public static final Type<Upserted> TYPE = typeOf("upserted");
		public static final StreamCodec<RegistryFriendlyByteBuf, Upserted> CODEC = CustomPacketPayload.codec(Upserted::write, Upserted::new);

		private Upserted(FriendlyByteBuf buf) {
			this(Codecs.readWaypoint(buf), Change.byOrdinal(buf.readVarInt()), buf.readUtf(64));
		}

		private void write(FriendlyByteBuf buf) {
			Codecs.writeWaypoint(buf, waypoint);
			buf.writeVarInt(change.ordinal());
			buf.writeUtf(actor, 64);
		}

		@Override
		public Type<Upserted> type() {
			return TYPE;
		}
	}

	public record Removed(int id, String actor) implements CustomPacketPayload {
		public static final Type<Removed> TYPE = typeOf("removed");
		public static final StreamCodec<RegistryFriendlyByteBuf, Removed> CODEC = CustomPacketPayload.codec(Removed::write, Removed::new);

		private Removed(FriendlyByteBuf buf) {
			this(buf.readVarInt(), buf.readUtf(64));
		}

		private void write(FriendlyByteBuf buf) {
			buf.writeVarInt(id);
			buf.writeUtf(actor, 64);
		}

		@Override
		public Type<Removed> type() {
			return TYPE;
		}
	}

	public record Categories(List<Category> categories) implements CustomPacketPayload {
		public static final Type<Categories> TYPE = typeOf("categories");
		public static final StreamCodec<RegistryFriendlyByteBuf, Categories> CODEC = CustomPacketPayload.codec(Categories::write, Categories::new);

		private Categories(FriendlyByteBuf buf) {
			this(Codecs.readList(buf, Codecs::readCategory, 1000));
		}

		private void write(FriendlyByteBuf buf) {
			Codecs.writeList(buf, categories, Codecs::writeCategory);
		}

		@Override
		public Type<Categories> type() {
			return TYPE;
		}
	}

	/** Answer to a client request; {@code message} is shown to the player as is. */
	public record Response(int requestId, boolean ok, String message) implements CustomPacketPayload {
		public static final Type<Response> TYPE = typeOf("response");
		public static final StreamCodec<RegistryFriendlyByteBuf, Response> CODEC = CustomPacketPayload.codec(Response::write, Response::new);

		private Response(FriendlyByteBuf buf) {
			this(buf.readVarInt(), buf.readBoolean(), buf.readUtf(1024));
		}

		private void write(FriendlyByteBuf buf) {
			buf.writeVarInt(requestId);
			buf.writeBoolean(ok);
			buf.writeUtf(message, 1024);
		}

		@Override
		public Type<Response> type() {
			return TYPE;
		}
	}
}
