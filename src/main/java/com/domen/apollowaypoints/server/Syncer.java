package com.domen.apollowaypoints.server;

import com.domen.apollowaypoints.ApolloWaypoints;
import com.domen.apollowaypoints.net.Payloads;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Keeps clients with the mod in sync. A player is synced after sending a {@link Payloads.Hello} with our protocol;
 * vanilla clients and Carpet bots never do, so they never get packets.
 */
public final class Syncer {
	private final MinecraftServer server;
	private final WaypointStore store;
	private final Set<UUID> synced = new HashSet<>();

	Syncer(MinecraftServer server, WaypointStore store) {
		this.server = server;
		this.store = store;
	}

	void onHello(ServerPlayer player, Payloads.Hello hello) {
		if (hello.protocol() != Payloads.PROTOCOL) {
			ApolloWaypoints.LOGGER.info("{} has Apollo Waypoints {} with protocol {}, server has {}; not syncing",
				player.getName().getString(), hello.modVersion(), hello.protocol(), Payloads.PROTOCOL);
			player.sendSystemMessage(Component.literal("Apollo Waypoints: версия мода у тебя (" + hello.modVersion()
				+ ") не совпадает с серверной (" + ApolloWaypoints.version() + "). Обнови мод, иначе точки не появятся в Xaero.")
				.withStyle(ChatFormatting.RED));
			return;
		}
		if (!Perms.check(player, Perms.Perm.USE)) {
			return;
		}
		synced.add(player.getUUID());
		sendWelcome(player);
		ServerPlayNetworking.send(player, new Payloads.Snapshot(new ArrayList<>(store.all())));
	}

	void onDisconnect(ServerPlayer player) {
		synced.remove(player.getUUID());
	}

	private void sendWelcome(ServerPlayer player) {
		ServerPlayNetworking.send(player, new Payloads.Welcome(Payloads.PROTOCOL, store.serverId(), Perms.flags(player),
			new ArrayList<>(store.categories())));
	}

	/** After a config reload the permissions may have changed. */
	void resendWelcome() {
		for (ServerPlayer player : syncedPlayers()) {
			sendWelcome(player);
		}
	}

	void broadcast(CustomPacketPayload payload) {
		for (ServerPlayer player : syncedPlayers()) {
			ServerPlayNetworking.send(player, payload);
		}
	}

	private Iterable<ServerPlayer> syncedPlayers() {
		return server.getPlayerList().getPlayers().stream().filter(p -> synced.contains(p.getUUID())).toList();
	}

	/**
	 * Whether the player's client runs Xaero's Minimap, judged by the network channels it declared.
	 * Used to send Xaero's share format only to players whose Xaero turns it into an [Add] button.
	 */
	static boolean hasXaero(ServerPlayer player) {
		for (Identifier channel : ServerPlayNetworking.getSendable(player)) {
			if (channel.getNamespace().startsWith("xaero")) {
				return true;
			}
		}
		return false;
	}
}
