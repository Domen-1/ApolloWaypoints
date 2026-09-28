package com.domen.apollowaypoints.test;

import com.domen.apollowaypoints.ApolloWaypoints;
import xaero.common.minimap.waypoints.Waypoint;
import xaero.hud.minimap.BuiltInHudModules;
import xaero.hud.minimap.module.MinimapSession;
import xaero.hud.minimap.waypoint.WaypointCollector;
import xaero.hud.minimap.waypoint.set.WaypointSet;
import xaero.hud.minimap.waypoint.thirdparty.ThirdPartyWaypoints;
import xaero.hud.minimap.world.MinimapWorld;
import xaero.hud.minimap.world.container.MinimapWorldContainer;
import xaero.hud.minimap.world.container.MinimapWorldRootContainer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Looks into Xaero's state from the test. Call on the client thread. */
final class XaeroProbe {
	private XaeroProbe() {
	}

	private static MinimapWorldRootContainer root() {
		MinimapSession session = BuiltInHudModules.MINIMAP.getCurrentSession();
		return session == null ? null : session.getWorldManager().getAutoRootContainer();
	}

	/** Our third-party waypoints per Xaero dimension directory, e.g. {dim%0=2, dim%-1=1}; null if Xaero is not ready. */
	static Map<String, Integer> countsByDimension() {
		MinimapWorldRootContainer root = root();
		if (root == null) {
			return null;
		}
		Map<String, Integer> counts = new TreeMap<>();
		for (MinimapWorldContainer container : root.getSubContainers()) {
			for (ThirdPartyWaypoints group : container.getThirdPartyWaypointManager().getAll()) {
				if (group.getOriginId().getNamespace().equals(ApolloWaypoints.MOD_ID) && !group.getWaypoints().isEmpty()) {
					counts.merge(container.getSubName(), group.getWaypoints().size(), Integer::sum);
				}
			}
		}
		return counts;
	}

	static int total() {
		Map<String, Integer> counts = countsByDimension();
		return counts == null ? -1 : counts.values().stream().mapToInt(Integer::intValue).sum();
	}

	static Boolean isHidden(int id) {
		MinimapWorldRootContainer root = root();
		if (root == null) {
			return null;
		}
		for (MinimapWorldContainer container : root.getSubContainers()) {
			for (ThirdPartyWaypoints group : container.getThirdPartyWaypointManager().getAll()) {
				Waypoint w = group.get(String.valueOf(id));
				if (group.getOriginId().getNamespace().equals(ApolloWaypoints.MOD_ID) && w != null) {
					return w.isThirdPartyDeleted();
				}
			}
		}
		return null;
	}

	/** What Xaero's own collector hands to its renderers in the current dimension: our waypoints among them. */
	static List<String> collectedForRendering() {
		MinimapSession session = BuiltInHudModules.MINIMAP.getCurrentSession();
		if (session == null) {
			return null;
		}
		List<Waypoint> collected = new ArrayList<>();
		new WaypointCollector(session).collect(collected);
		List<String> ours = new ArrayList<>();
		for (Waypoint w : collected) {
			if (w.isThirdParty() && w.getThirdPartyOrigin().getNamespace().equals(ApolloWaypoints.MOD_ID)) {
				ours.add(w.getName());
			}
		}
		return ours;
	}

	/** Size of a regular set in the current Xaero world, -1 if missing. */
	static int setSize(String name) {
		MinimapSession session = BuiltInHudModules.MINIMAP.getCurrentSession();
		MinimapWorld world = session == null ? null : session.getWorldManager().getCurrentWorld();
		WaypointSet set = world == null ? null : world.getWaypointSet(name);
		return set == null ? -1 : set.size();
	}
}
