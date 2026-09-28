package com.domen.apollowaypoints.client;

import com.domen.apollowaypoints.ApolloWaypoints;
import com.domen.apollowaypoints.client.gui.WaypointsScreen;
import com.domen.apollowaypoints.client.xaero.XaeroBridge;
import com.domen.apollowaypoints.model.Colors;
import com.domen.apollowaypoints.model.Dimensions;
import com.domen.apollowaypoints.model.Waypoint;
import com.domen.apollowaypoints.net.Payloads;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class ApolloWaypointsClient implements ClientModInitializer {
	private static final SystemToast.SystemToastId TOAST = new SystemToast.SystemToastId();
	private static XaeroBridge xaero = XaeroBridge.NONE;
	private static KeyMapping openKey;
	/** Screens opened from a chat command are set on the next tick, after the chat screen has closed. */
	private static Screen pendingScreen;

	public static XaeroBridge xaero() {
		return xaero;
	}

	@Override
	public void onInitializeClient() {
		xaero = XaeroBridge.create();
		ClientSettings.load();

		KeyMapping.Category category = KeyMapping.Category.register(ApolloWaypoints.id("main"));
		openKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.apollowaypoints.open", InputConstants.KEY_J, category));

		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			ClientState.reset();
			Requests.reset();
			// canSend is false on servers without the mod, so we stay silent there.
			if (ClientPlayNetworking.canSend(Payloads.Hello.TYPE)) {
				ClientPlayNetworking.send(new Payloads.Hello(Payloads.PROTOCOL, ApolloWaypoints.version()));
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> {
			xaero.clear();
			ClientState.reset();
			Requests.reset();
		}));

		ClientPlayNetworking.registerGlobalReceiver(Payloads.Welcome.TYPE, (payload, context) -> ClientState.welcome(payload));
		ClientPlayNetworking.registerGlobalReceiver(Payloads.Snapshot.TYPE, (payload, context) -> {
			ClientState.snapshot(payload.waypoints());
			xaero.requestSync();
		});
		ClientPlayNetworking.registerGlobalReceiver(Payloads.Upserted.TYPE, (payload, context) -> {
			ClientState.upsert(payload.waypoint());
			xaero.requestSync();
			toast(context.client(), payload);
		});
		ClientPlayNetworking.registerGlobalReceiver(Payloads.Removed.TYPE, (payload, context) -> {
			ClientState.remove(payload.id());
			xaero.requestSync();
		});
		ClientPlayNetworking.registerGlobalReceiver(Payloads.Categories.TYPE, (payload, context) -> {
			ClientState.categories(payload.categories());
			xaero.requestSync();
		});
		ClientPlayNetworking.registerGlobalReceiver(Payloads.Response.TYPE, (payload, context) -> Requests.onResponse(payload));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (openKey.consumeClick()) {
				if (client.gui.screen() == null) {
					openList(client);
				}
			}
			if (pendingScreen != null) {
				client.gui.setScreen(pendingScreen);
				pendingScreen = null;
			}
			xaero.tick();
		});

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
			ClientCommands.literal("wpgui").executes(ctx -> {
				openList(ctx.getSource().getClient());
				return 1;
			})));
	}

	private static void openList(Minecraft client) {
		if (!ClientState.isSynced()) {
			if (client.player != null) {
				client.player.sendSystemMessage(Component.literal("На этом сервере нет Apollo Waypoints (или версии не совпадают)"));
			}
			return;
		}
		pendingScreen = new WaypointsScreen();
	}

	private static void toast(Minecraft client, Payloads.Upserted payload) {
		if (!ClientSettings.toasts() || client.player == null || payload.actor().equals(client.player.getName().getString())) {
			return;
		}
		Waypoint w = payload.waypoint();
		String verb = switch (payload.change()) {
			case CREATED -> " добавил точку";
			case RESTORED -> " вернул точку";
			default -> null;
		};
		if (verb == null) {
			return;
		}
		SystemToast.addOrUpdate(client.gui.toastManager(), TOAST,
			Component.literal(payload.actor() + verb),
			Component.literal("■ ").withColor(Colors.rgb(w.color())).append(Component.literal(w.name() + " · " + Dimensions.displayName(w.dimension()))));
	}
}
