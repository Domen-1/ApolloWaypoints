package com.domen.apollowaypoints.server;

import com.domen.apollowaypoints.model.Waypoint;

/** Outcome of a {@link WaypointService} call; {@code message} is a finished Russian sentence for the player. */
public record Result(boolean ok, String message, Waypoint waypoint) {
	public static Result ok(String message, Waypoint waypoint) {
		return new Result(true, message, waypoint);
	}

	public static Result error(String message) {
		return new Result(false, message, null);
	}
}
