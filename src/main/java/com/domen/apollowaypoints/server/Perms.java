package com.domen.apollowaypoints.server;

import com.domen.apollowaypoints.ApolloWaypoints;
import com.domen.apollowaypoints.net.PermissionFlags;
import net.fabricmc.fabric.api.permission.v1.PermissionContextOwner;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionLevel;

/**
 * Permission nodes, checked through Fabric's permission API: a permissions mod such as LuckPerms decides if installed,
 * otherwise the op level from the config.
 */
public final class Perms {
	public enum Perm {
		USE("use", PermissionLevel.ALL),
		ADD("add", PermissionLevel.ALL),
		EDIT_OWN("edit.own", PermissionLevel.ALL),
		EDIT_ANY("edit.any", PermissionLevel.ALL),
		REMOVE_OWN("remove.own", PermissionLevel.ALL),
		REMOVE_ANY("remove.any", PermissionLevel.ALL),
		TP("tp", PermissionLevel.GAMEMASTERS),
		ADMIN("admin", PermissionLevel.ADMINS);

		public final String key;
		public final Identifier node;
		public final PermissionLevel defaultLevel;

		Perm(String key, PermissionLevel defaultLevel) {
			this.key = key;
			this.node = ApolloWaypoints.id(key);
			this.defaultLevel = defaultLevel;
		}
	}

	private Perms() {
	}

	public static boolean check(PermissionContextOwner owner, Perm perm) {
		return owner.checkPermission(perm.node, WaypointServer.config().level(perm));
	}

	public static int flags(ServerPlayer player) {
		int flags = 0;
		if (check(player, Perm.ADD)) {
			flags |= PermissionFlags.ADD;
		}
		if (check(player, Perm.EDIT_OWN)) {
			flags |= PermissionFlags.EDIT_OWN;
		}
		if (check(player, Perm.EDIT_ANY)) {
			flags |= PermissionFlags.EDIT_ANY;
		}
		if (check(player, Perm.REMOVE_OWN)) {
			flags |= PermissionFlags.REMOVE_OWN;
		}
		if (check(player, Perm.REMOVE_ANY)) {
			flags |= PermissionFlags.REMOVE_ANY;
		}
		if (check(player, Perm.TP)) {
			flags |= PermissionFlags.TP;
		}
		if (WaypointServer.config().tpInSpectator) {
			flags |= PermissionFlags.TP_IN_SPECTATOR;
		}
		if (check(player, Perm.ADMIN)) {
			flags |= PermissionFlags.ADMIN;
		}
		return flags;
	}
}
