package com.domen.apollowaypoints.test;

import com.domen.apollowaypoints.client.ApolloWaypointsClient;
import com.domen.apollowaypoints.client.ClientState;
import com.domen.apollowaypoints.client.Requests;
import com.domen.apollowaypoints.client.gui.EditScreen;
import com.domen.apollowaypoints.client.gui.WaypointsScreen;
import com.domen.apollowaypoints.client.gui.XaeroImportScreen;
import com.domen.apollowaypoints.format.XaeroShare;
import com.domen.apollowaypoints.model.Dimensions;
import com.domen.apollowaypoints.model.Waypoint;
import com.domen.apollowaypoints.model.WaypointDraft;
import com.domen.apollowaypoints.net.Payloads;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

/**
 * End-to-end check in a real client with Xaero's Minimap: waypoints added on the (integrated) server show up in Xaero's
 * third-party layer, disappear live, can be hidden and copied into a set. Screenshots go to build/run/clientGameTest/screenshots.
 */
public class ApolloClientGameTest implements FabricClientGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("apollowaypoints-test");

	@Override
	public void runTest(ClientGameTestContext context) {
		// The dedicated server goes first: closing the singleplayer world at the end hangs inside the test framework.
		dedicatedServer(context);
		singleplayer(context);
	}

	/**
	 * A real network connection to a dedicated server where the player is not an operator: sync on join, the Xaero
	 * "Share" message in chat turning into a server waypoint, client requests, and the teleport rule for spectators.
	 */
	private static void dedicatedServer(ClientGameTestContext context) {
		List<Component> received = new CopyOnWriteArrayList<>();
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> received.add(message));
		Properties properties = new Properties();
		properties.setProperty("level-type", "minecraft:flat");
		properties.setProperty("enforce-secure-profile", "false");
		try (TestDedicatedServerContext server = context.worldBuilder().createServer(properties);
			 TestDedicatedServerConnection connection = server.connect()) {
			connection.waitForChunksRender();
			context.waitFor(mc -> ClientState.isSynced(), 200);
			check(true, "synced with a dedicated server over the network");

			// Xaero's own Share button sends exactly this line to chat.
			String share = XaeroShare.build("Точка из чата", "Ч", 10, -60, 10, 11, null, Dimensions.OVERWORLD);
			context.runOnClient(mc -> mc.player.connection.sendChat(share));
			context.waitFor(mc -> confirmCommand(received) != null, 100);
			String confirm = confirmCommand(received);
			LOG.info("Share prompt offers: {}", confirm);
			context.runOnClient(mc -> mc.player.connection.sendCommand(confirm.substring(1)));
			context.waitFor(mc -> find("Точка из чата") != null, 100);
			check(find("Точка из чата").category().equals("farms"), "shared waypoint added to the chosen category");

			// The client screen's path: a request packet and its response.
			AtomicReference<Payloads.Response> response = new AtomicReference<>();
			context.runOnClient(mc -> {
				WaypointDraft draft = new WaypointDraft();
				draft.name = "Из экрана";
				draft.dimension = Dimensions.OVERWORLD;
				draft.x = 30;
				draft.y = -60;
				draft.z = 30;
				Requests.create(draft, response::set);
			});
			context.waitFor(mc -> response.get() != null, 100);
			check(response.get().ok() && find("Из экрана") != null, "created through a client request: " + response.get().message());
			awaitXaero(context, 2);

			int id = find("Из экрана").id();
			response.set(null);
			context.runOnClient(mc -> Requests.teleport(id, response::set));
			context.waitFor(mc -> response.get() != null, 100);
			check(!response.get().ok(), "non-op in survival may not teleport: " + response.get().message());

			String player = context.computeOnClient(mc -> mc.player.getName().getString());
			server.runCommand("gamemode spectator " + player);
			context.waitFor(mc -> mc.player.isSpectator(), 100);
			response.set(null);
			context.runOnClient(mc -> Requests.teleport(id, response::set));
			context.waitFor(mc -> response.get() != null, 100);
			check(response.get().ok(), "spectator may teleport: " + response.get().message());
			context.waitFor(mc -> Math.abs(mc.player.getX() - 30.5) < 0.01 && Math.abs(mc.player.getZ() - 30.5) < 0.01, 100);
			check(true, "player moved to the waypoint");

			// /wp get: with Xaero on the client the server sends only the share line, without the "no Xaero" hint.
			received.clear();
			context.runOnClient(mc -> mc.player.connection.sendCommand("wp get " + id));
			context.waitFor(mc -> received.stream().anyMatch(c -> c.getString().startsWith(XaeroShare.PREFIX)), 100);
			context.waitTicks(10);
			check(received.stream().noneMatch(c -> c.getString().startsWith("Строка выше")), "server detected Xaero on the client");
			context.takeScreenshot("apollo-0-dedicated");
		}
	}

	private static String confirmCommand(List<Component> messages) {
		for (Component message : messages) {
			for (Component part : message.toFlatList()) {
				if (part.getStyle().getClickEvent() instanceof ClickEvent.RunCommand run
					&& run.command().startsWith("/wp confirm ") && run.command().endsWith(" farms")) {
					return run.command();
				}
			}
		}
		return null;
	}

	private static Waypoint find(String name) {
		return ClientState.waypoints().stream().filter(w -> w.name().equals(name)).findFirst().orElse(null);
	}

	private static void singleplayer(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			context.waitFor(mc -> mc.player != null && mc.level != null);
			context.waitFor(mc -> ClientState.isSynced(), 200);
			check(context.computeOnClient(mc -> ApolloWaypointsClient.xaero().available()), "Xaero adapter is available");
			context.waitFor(mc -> XaeroProbe.total() == 0, 400);

			world.getServer().runCommand("wp add 5 -60 5 Ферма гастов");
			world.getServer().runCommand("wp set 1 category farms");
			world.getServer().runCommand("wp add 20 -60 -10 Портал:хаб");
			world.getServer().runCommand("wp set 2 category portals");
			world.getServer().runCommand("wp add 0 64 0 Хаб в незере");
			world.getServer().runCommand("wp set 3 dimension minecraft:the_nether");
			context.waitFor(mc -> ClientState.waypoints().size() == 3, 100);
			awaitXaero(context, 3);

			Map<String, Integer> counts = context.computeOnClient(mc -> XaeroProbe.countsByDimension());
			LOG.info("Xaero third-party waypoints by dimension: {}", counts);
			// Xaero names the dimension containers "overworld" and "the_nether" (on disk they are dim%0 and dim%-1).
			check(Integer.valueOf(2).equals(counts.get("overworld")) && Integer.valueOf(1).equals(counts.get("the_nether")), "2 in the overworld, 1 in the nether");

			List<String> rendered = context.computeOnClient(mc -> XaeroProbe.collectedForRendering());
			LOG.info("Collected by Xaero for rendering in the overworld: {}", rendered);
			check(rendered.containsAll(List.of("Ферма гастов", "Портал:хаб")) && !rendered.contains("Хаб в незере"), "Xaero renders our overworld waypoints");

			context.takeScreenshot("apollo-1-hud");

			// Live update without rejoining.
			world.getServer().runCommand("wp remove 2");
			awaitXaero(context, 2);
			world.getServer().runCommand("wp restore 2");
			awaitXaero(context, 3);
			world.getServer().runCommand("wp set 1 color red");
			context.waitFor(mc -> ClientState.get(1).color() == 12, 100);

			// Hide for this player only, survives the next resync.
			context.runOnClient(mc -> ApolloWaypointsClient.xaero().setHidden(1, true));
			check(Boolean.TRUE.equals(context.computeOnClient(mc -> XaeroProbe.isHidden(1))), "waypoint 1 hidden in Xaero");
			world.getServer().runCommand("wp set 2 color gold");
			context.waitTicks(10);
			check(Boolean.TRUE.equals(context.computeOnClient(mc -> XaeroProbe.isHidden(1))), "still hidden after a resync");
			context.runOnClient(mc -> ApolloWaypointsClient.xaero().setHidden(1, false));

			String exported = context.computeOnClient(mc -> ApolloWaypointsClient.xaero().exportToSet("Apollo"));
			LOG.info("Export: {}", exported);
			check(context.computeOnClient(mc -> XaeroProbe.setSize("Apollo")) == 2, "set Apollo has the 2 overworld waypoints");

			context.setScreen(WaypointsScreen::new);
			context.waitTicks(5);
			context.takeScreenshot("apollo-2-list");
			context.setScreen(() -> new EditScreen(null, null));
			context.waitTicks(5);
			context.takeScreenshot("apollo-3-edit");
			context.setScreen(() -> new EditScreen(null, ClientState.get(1)));
			context.waitTicks(5);
			context.takeScreenshot("apollo-4-edit-existing");
			context.setScreen(() -> new XaeroImportScreen(null));
			context.waitTicks(5);
			context.takeScreenshot("apollo-5-import");
			context.setScreen(() -> null);
			context.waitTicks(5);
			context.takeScreenshot("apollo-6-hud-after");
			LOG.info("Apollo Waypoints client test passed");
		}
	}

	/** Waits until Xaero holds {@code expected} of our waypoints; fails at once if the adapter switched itself off. */
	private static void awaitXaero(ClientGameTestContext context, int expected) {
		context.waitFor(mc -> !ApolloWaypointsClient.xaero().available() || XaeroProbe.total() == expected, 200);
		check(context.computeOnClient(mc -> ApolloWaypointsClient.xaero().available()), "Xaero adapter still works (see log for its error)");
		int total = context.computeOnClient(mc -> XaeroProbe.total());
		check(total == expected, "Xaero has " + expected + " of our waypoints (has " + total + ")");
	}

	private static void check(boolean condition, String what) {
		if (!condition) {
			// Logged here as well: the test framework does not always print the failure before exiting.
			LOG.error("Check failed: {}", what);
			throw new AssertionError("Check failed: " + what);
		}
		LOG.info("OK: {}", what);
	}
}
