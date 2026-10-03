package com.domen.apollowaypoints.client.xaero;

import com.domen.apollowaypoints.ApolloWaypoints;
import com.domen.apollowaypoints.client.ClientSettings;
import com.domen.apollowaypoints.client.ClientState;
import com.domen.apollowaypoints.model.Dimensions;
import com.domen.apollowaypoints.model.Visibility;
import com.domen.apollowaypoints.model.Waypoint;
import com.domen.apollowaypoints.model.WaypointDraft;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import xaero.hud.minimap.BuiltInHudModules;
import xaero.hud.minimap.module.MinimapSession;
import xaero.hud.minimap.waypoint.WaypointColor;
import xaero.hud.minimap.waypoint.WaypointPurpose;
import xaero.hud.minimap.waypoint.WaypointRenderInfo;
import xaero.hud.minimap.waypoint.WaypointVisibilityType;
import xaero.hud.minimap.waypoint.set.WaypointSet;
import xaero.hud.minimap.waypoint.thirdparty.ThirdPartyWaypoints;
import xaero.hud.minimap.world.MinimapWorld;
import xaero.hud.minimap.world.container.MinimapWorldContainer;
import xaero.hud.minimap.world.container.MinimapWorldRootContainer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shows server waypoints through Xaero's third-party waypoint layer, the one its Waystones support uses
 * (checked against Minimap 26.5.3): root container of the current server -> dimension container ->
 * ThirdPartyWaypointManager -> one group per category. Xaero draws these regardless of the selected waypoint set,
 * lists them in its waypoint screen and on World Map, and never writes them to the player's files.
 */
public final class XaeroBridgeImpl implements XaeroBridge {
	private static final int CAPTURE_INTERVAL_TICKS = 40;

	private boolean broken;
	private boolean dirty = true;
	private MinimapWorldRootContainer syncedRoot;
	private int ticks;

	public XaeroBridgeImpl() {
		// Fail here, inside XaeroBridge.create(), if Xaero's classes moved.
		BuiltInHudModules.MINIMAP.getClass();
	}

	@Override
	public boolean available() {
		return !broken;
	}

	@Override
	public void requestSync() {
		dirty = true;
	}

	@Override
	public void tick() {
		if (broken) {
			return;
		}
		try {
			MinimapWorldRootContainer root = root();
			if (root == null) {
				return;
			}
			if (root != syncedRoot) {
				dirty = true;
			}
			if (++ticks % CAPTURE_INTERVAL_TICKS == 0) {
				captureHidden(root);
			}
			if (dirty && ClientState.isSynced()) {
				captureHidden(root);
				sync(root);
				syncedRoot = root;
				dirty = false;
			}
		} catch (LinkageError | RuntimeException e) {
			fail(e);
		}
	}

	@Override
	public void clear() {
		if (!broken) {
			try {
				MinimapWorldRootContainer root = root();
				if (root != null) {
					// A click in Xaero made right before leaving is not lost.
					captureHidden(root);
					ourGroups(root).forEach(ThirdPartyWaypoints::clear);
				}
			} catch (LinkageError | RuntimeException e) {
				fail(e);
			}
		}
		syncedRoot = null;
		dirty = true;
	}

	@Override
	public void setHidden(Collection<Integer> waypointIds, boolean hidden) {
		ClientSettings.setHidden(waypointIds, hidden);
		if (broken) {
			return;
		}
		try {
			MinimapWorldRootContainer root = root();
			if (root == null) {
				return;
			}
			boolean overridesChanged = false;
			for (ThirdPartyWaypoints group : ourGroups(root)) {
				for (int id : waypointIds) {
					xaero.common.minimap.waypoints.Waypoint xw = group.get(String.valueOf(id));
					if (xw != null) {
						overridesChanged |= setOff(xw, hidden);
					}
				}
			}
			if (overridesChanged) {
				saveXaeroConfig(root);
			}
		} catch (LinkageError | RuntimeException e) {
			fail(e);
		}
	}

	@Override
	public String exportToSet(String setName) {
		if (broken) {
			return "Связь с Xaero отключена, подробности в логе";
		}
		try {
			MinimapWorldRootContainer root = root();
			if (root == null) {
				return "Xaero ещё не готов, попробуй через пару секунд";
			}
			MinimapSession session = root.getSession();
			MinimapWorld current = session.getWorldManager().getCurrentWorld();
			Map<String, List<Waypoint>> byDimension = new LinkedHashMap<>();
			for (Waypoint w : ClientState.waypoints()) {
				byDimension.computeIfAbsent(w.dimension(), d -> new ArrayList<>()).add(w);
			}
			int copied = 0;
			List<String> skipped = new ArrayList<>();
			for (Map.Entry<String, List<Waypoint>> entry : byDimension.entrySet()) {
				MinimapWorldContainer container = container(root, session, entry.getKey());
				MinimapWorld world = null;
				if (container != null) {
					world = current != null && current.getContainer() == container ? current : container.getFirstWorld();
				}
				if (world == null) {
					skipped.add(Dimensions.displayName(entry.getKey()));
					continue;
				}
				WaypointSet set = world.getWaypointSet(setName);
				if (set == null) {
					set = WaypointSet.Builder.begin().setName(setName).build();
					world.addWaypointSet(set);
				}
				set.clear();
				for (Waypoint w : entry.getValue()) {
					set.add(toXaero(w));
				}
				session.getWorldManagerIO().saveWorld(world);
				copied += entry.getValue().size();
			}
			String message = "Скопировано " + copied + " точек в набор Xaero «" + setName + "»";
			if (!skipped.isEmpty()) {
				message += ". Не скопированы: " + String.join(", ", skipped) + " — Xaero ещё не видел эти измерения, зайди туда хоть раз";
			}
			return message;
		} catch (IOException e) {
			return "Xaero не смог сохранить набор: " + e.getMessage();
		} catch (LinkageError | RuntimeException e) {
			fail(e);
			return "Связь с Xaero сломалась, подробности в логе";
		}
	}

	@Override
	public List<LocalWaypoint> readLocal() {
		if (broken) {
			return List.of();
		}
		try {
			MinimapSession session = BuiltInHudModules.MINIMAP.getCurrentSession();
			MinimapWorld world = session == null ? null : session.getWorldManager().getCurrentWorld();
			if (world == null) {
				return List.of();
			}
			ResourceKey<Level> dimension = world.getDimId();
			if (dimension == null && Minecraft.getInstance().level != null) {
				dimension = Minecraft.getInstance().level.dimension();
			}
			if (dimension == null) {
				return List.of();
			}
			List<LocalWaypoint> result = new ArrayList<>();
			for (WaypointSet set : world.getIterableWaypointSets()) {
				String setName = I18n.get(set.getName());
				for (xaero.common.minimap.waypoints.Waypoint xw : set.getWaypoints()) {
					if (xw.isTemporary() || xw.getPurpose() != WaypointPurpose.NORMAL || xw.isThirdParty()) {
						continue;
					}
					WaypointDraft d = new WaypointDraft();
					d.name = xw.getName();
					d.symbol = xw.getInitials();
					d.dimension = dimension.identifier().toString();
					d.x = xw.getX();
					d.y = xw.isYIncluded() ? xw.getY() : null;
					d.z = xw.getZ();
					d.yaw = xw.isRotation() ? xw.getYaw() : null;
					d.color = xw.getWaypointColor().ordinal();
					// WaypointVisibilityType order: LOCAL, GLOBAL, WORLD_MAP_LOCAL, WORLD_MAP_GLOBAL.
					d.visibility = Visibility.fromXaeroType(xw.getVisibility().ordinal());
					result.add(new LocalWaypoint(setName, d));
				}
			}
			return result;
		} catch (LinkageError | RuntimeException e) {
			fail(e);
			return List.of();
		}
	}

	private static MinimapWorldRootContainer root() {
		MinimapSession session = BuiltInHudModules.MINIMAP.getCurrentSession();
		return session == null ? null : session.getWorldManager().getAutoRootContainer();
	}

	private void sync(MinimapWorldRootContainer root) {
		MinimapSession session = root.getSession();
		ourGroups(root).forEach(ThirdPartyWaypoints::clear);
		List<Integer> off = new ArrayList<>();
		List<Integer> on = new ArrayList<>();
		boolean overridesChanged = false;
		for (Waypoint w : ClientState.waypoints()) {
			MinimapWorldContainer container = container(root, session, w.dimension());
			if (container == null) {
				continue;
			}
			xaero.common.minimap.waypoints.Waypoint xw = toXaero(w);
			// add() attaches the render override Xaero keeps per id and reads from its config; setting flags earlier throws.
			group(container, w.category()).add(String.valueOf(w.id()), xw);
			// Within a session captureHidden() has already emptied the override, so a "disabled" in it was loaded from
			// Xaero's config: a click in Xaero's waypoint screen we did not see, e.g. the game closed right after it.
			// Its "deleted" is not trusted: 0.1.0 set it from our settings without saving that config.
			Boolean clicked = xw.getThirdPartyRenderOverride().getDisabled();
			boolean hidden = clicked != null ? clicked : ClientSettings.isHidden(w.id());
			(hidden ? off : on).add(w.id());
			overridesChanged |= setOff(xw, hidden);
		}
		ClientSettings.setHidden(off, true);
		ClientSettings.setHidden(on, false);
		if (overridesChanged) {
			saveXaeroConfig(root);
		}
	}

	/**
	 * "Off for me" is Xaero's "disabled" flag on the waypoint itself. Xaero's waypoint screen writes it into the per-id
	 * override, and World Map flips the waypoint's own flag, which a set override would mask. So the override is
	 * emptied, and both keep working. Xaero's "delete" becomes "off" too: a server waypoint cannot be deleted locally.
	 * Returns whether the override had something set, i.e. the copy in Xaero's config is now out of date.
	 */
	private static boolean setOff(xaero.common.minimap.waypoints.Waypoint xw, boolean off) {
		WaypointRenderInfo override = xw.getThirdPartyRenderOverride();
		boolean changed = override.getDisabled() != null || override.isThirdPartyDeleted();
		override.setDisabled(null);
		override.setThirdPartyDeleted(false);
		xw.setDisabled(off);
		return changed;
	}

	/** Saves Xaero's per-server config, where it keeps the overrides, as its own screens do; never before it was read. */
	private static void saveXaeroConfig(MinimapWorldRootContainer root) {
		if (root.getConfig().isLoaded()) {
			root.getSession().getWorldManagerIO().getRootConfigIO().save(root);
		}
	}

	private static MinimapWorldContainer container(MinimapWorldRootContainer root, MinimapSession session, String dimension) {
		Identifier id = Identifier.tryParse(dimension);
		if (id == null) {
			return null;
		}
		String directory = session.getDimensionHelper().getDimensionDirectoryName(ResourceKey.create(Registries.DIMENSION, id));
		return root.addSubContainer(root.getPath().resolve(directory));
	}

	/** One group per category, so a hidden category is switched off as a whole. */
	private static ThirdPartyWaypoints group(MinimapWorldContainer container, String category) {
		ThirdPartyWaypoints group = container.getThirdPartyWaypointManager().get(ApolloWaypoints.id(category));
		if (!group.hasEnabledStateGetter()) {
			group.setEnabledStateGetter(() -> !ClientSettings.isCategoryHidden(category));
		}
		return group;
	}

	private static List<ThirdPartyWaypoints> ourGroups(MinimapWorldRootContainer root) {
		List<ThirdPartyWaypoints> groups = new ArrayList<>();
		for (MinimapWorldContainer container : root.getSubContainers()) {
			for (ThirdPartyWaypoints group : container.getThirdPartyWaypointManager().getAll()) {
				if (group.getOriginId().getNamespace().equals(ApolloWaypoints.MOD_ID)) {
					groups.add(group);
				}
			}
		}
		return groups;
	}

	/**
	 * Picks up waypoints switched off or on in Xaero's waypoint screen or on World Map. Xaero's flags live on objects
	 * that sync() rebuilds, so without this the choice is lost on the next change on the server or on rejoin.
	 */
	private static void captureHidden(MinimapWorldRootContainer root) {
		if (ClientState.serverId() == null) {
			return;
		}
		List<Integer> off = new ArrayList<>();
		List<Integer> on = new ArrayList<>();
		boolean overridesChanged = false;
		for (ThirdPartyWaypoints group : ourGroups(root)) {
			for (Map.Entry<String, xaero.common.minimap.waypoints.Waypoint> e : group.getWaypoints().entrySet()) {
				xaero.common.minimap.waypoints.Waypoint xw = e.getValue();
				boolean hidden = xw.isDisabled() || xw.isThirdPartyDeleted();
				(hidden ? off : on).add(Integer.parseInt(e.getKey()));
				overridesChanged |= setOff(xw, hidden);
			}
		}
		ClientSettings.setHidden(off, true);
		ClientSettings.setHidden(on, false);
		if (overridesChanged) {
			saveXaeroConfig(root);
		}
	}

	private static xaero.common.minimap.waypoints.Waypoint toXaero(Waypoint w) {
		xaero.common.minimap.waypoints.Waypoint xw = new xaero.common.minimap.waypoints.Waypoint(w.x(), w.y() == null ? 64 : w.y(), w.z(),
			w.name(), w.symbol(), WaypointColor.fromIndex(w.color()), WaypointPurpose.NORMAL, false, w.y() != null);
		if (w.yaw() != null) {
			xw.setRotation(true);
			xw.setYaw(w.yaw());
		}
		xw.setVisibility(switch (w.visibility()) {
			case LOCAL -> WaypointVisibilityType.LOCAL;
			case GLOBAL -> WaypointVisibilityType.GLOBAL;
			case MAP -> WaypointVisibilityType.WORLD_MAP_GLOBAL;
		});
		return xw;
	}

	private void fail(Throwable t) {
		broken = true;
		ApolloWaypoints.LOGGER.error("Lost the connection to Xaero's Minimap (incompatible version?); server waypoints will not be drawn", t);
		LocalPlayer player = Minecraft.getInstance().player;
		if (player != null) {
			player.sendSystemMessage(Component.literal("Apollo Waypoints: не получилось показать точки в Xaero — похоже, версия Xaero несовместима. "
				+ "Список точек работает (/wpgui), подробности в логе.").withStyle(ChatFormatting.RED));
		}
	}
}
