package com.domen.apollowaypoints.client.xaero;

import com.domen.apollowaypoints.ApolloWaypoints;
import com.domen.apollowaypoints.model.WaypointDraft;
import net.fabricmc.loader.api.FabricLoader;

import java.util.Collection;
import java.util.List;

/**
 * Everything the mod does with Xaero's Minimap. The implementation touches Xaero's internal classes, so it is loaded
 * only when Xaero is installed, and it switches itself off on the first linkage error (e.g. after a Xaero update).
 */
public interface XaeroBridge {
	/** A waypoint from the player's own Xaero sets, for "send from Xaero". */
	record LocalWaypoint(String set, WaypointDraft draft) {
	}

	boolean available();

	/** Rebuild Xaero's view of the server waypoints on the next tick. */
	void requestSync();

	/** Called every client tick. */
	void tick();

	/** Remove our waypoints from Xaero, e.g. when leaving the server. */
	void clear();

	/**
	 * Switch waypoints off or on for this player only. It is Xaero's own "disabled" flag: the waypoints are not drawn,
	 * stay greyed out in Xaero's list and can be switched back there or on World Map.
	 */
	void setHidden(Collection<Integer> waypointIds, boolean hidden);

	/** Copies the server waypoints into a regular Xaero set in every dimension and saves it. Returns a message. */
	String exportToSet(String setName);

	/** Waypoints of the player's current Xaero world (all sets), excluding deathpoints and temporary ones. */
	List<LocalWaypoint> readLocal();

	XaeroBridge NONE = new XaeroBridge() {
		@Override
		public boolean available() {
			return false;
		}

		@Override
		public void requestSync() {
		}

		@Override
		public void tick() {
		}

		@Override
		public void clear() {
		}

		@Override
		public void setHidden(Collection<Integer> waypointIds, boolean hidden) {
		}

		@Override
		public String exportToSet(String setName) {
			return "Xaero's Minimap не установлен";
		}

		@Override
		public List<LocalWaypoint> readLocal() {
			return List.of();
		}
	};

	static XaeroBridge create() {
		FabricLoader loader = FabricLoader.getInstance();
		if (!loader.isModLoaded("xaerominimap") && !loader.isModLoaded("xaerominimapfair")) {
			ApolloWaypoints.LOGGER.info("Xaero's Minimap is not installed; waypoints will only be listed, not drawn");
			return NONE;
		}
		try {
			return (XaeroBridge) Class.forName("com.domen.apollowaypoints.client.xaero.XaeroBridgeImpl").getDeclaredConstructor().newInstance();
		} catch (Throwable t) {
			ApolloWaypoints.LOGGER.error("Cannot hook into Xaero's Minimap; waypoints will only be listed", t);
			return NONE;
		}
	}
}
