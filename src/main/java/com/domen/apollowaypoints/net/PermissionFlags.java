package com.domen.apollowaypoints.net;

/** What the player may do, sent in {@link Payloads.Welcome} so the client can grey out buttons. The server still checks. */
public final class PermissionFlags {
	public static final int ADD = 1;
	public static final int EDIT_OWN = 1 << 1;
	public static final int EDIT_ANY = 1 << 2;
	public static final int REMOVE_OWN = 1 << 3;
	public static final int REMOVE_ANY = 1 << 4;
	public static final int TP = 1 << 5;
	public static final int TP_IN_SPECTATOR = 1 << 6;
	public static final int ADMIN = 1 << 7;

	private PermissionFlags() {
	}

	public static boolean has(int flags, int flag) {
		return (flags & flag) != 0;
	}
}
