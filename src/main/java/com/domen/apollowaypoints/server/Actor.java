package com.domen.apollowaypoints.server;

import com.domen.apollowaypoints.model.Waypoint;
import net.fabricmc.fabric.api.permission.v1.PermissionContextOwner;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/** Who is changing waypoints: a player (through a command or the client screen) or the console. */
public record Actor(UUID uuid, String name, PermissionContextOwner permissions, ServerPlayer player) {
	public static Actor of(CommandSourceStack source) {
		ServerPlayer player = source.getPlayer();
		if (player != null) {
			return new Actor(player.getUUID(), player.getName().getString(), source, player);
		}
		// The console (dedicated or singleplayer) is called "Server"; RCON and command blocks keep their own names.
		String name = source.getTextName();
		return new Actor(Waypoint.CONSOLE, "Server".equals(name) ? "Сервер" : name, source, null);
	}

	public static Actor of(ServerPlayer player) {
		return new Actor(player.getUUID(), player.getName().getString(), player, player);
	}
}
