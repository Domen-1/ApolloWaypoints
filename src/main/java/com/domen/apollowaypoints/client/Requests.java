package com.domen.apollowaypoints.client;

import com.domen.apollowaypoints.model.WaypointDraft;
import com.domen.apollowaypoints.net.Payloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Sends change requests to the server and routes each {@link Payloads.Response} to whoever asked. */
public final class Requests {
	private static int nextId = 1;
	private static final Map<Integer, Consumer<Payloads.Response>> callbacks = new HashMap<>();

	private Requests() {
	}

	public static void create(WaypointDraft draft, Consumer<Payloads.Response> callback) {
		ClientPlayNetworking.send(new Payloads.Create(register(callback), draft));
	}

	public static void update(int id, WaypointDraft draft, Consumer<Payloads.Response> callback) {
		ClientPlayNetworking.send(new Payloads.Update(register(callback), id, draft));
	}

	public static void remove(int id, Consumer<Payloads.Response> callback) {
		ClientPlayNetworking.send(new Payloads.Remove(register(callback), id));
	}

	public static void teleport(int id, Consumer<Payloads.Response> callback) {
		ClientPlayNetworking.send(new Payloads.Teleport(register(callback), id));
	}

	/** Splits the list to stay under Minecraft's 32 KiB limit for client packets; one response per chunk. */
	public static int importAll(List<WaypointDraft> drafts, Consumer<Payloads.Response> callback) {
		int chunks = 0;
		for (int from = 0; from < drafts.size(); from += Payloads.IMPORT_CHUNK) {
			List<WaypointDraft> chunk = List.copyOf(drafts.subList(from, Math.min(drafts.size(), from + Payloads.IMPORT_CHUNK)));
			ClientPlayNetworking.send(new Payloads.Import(register(callback), chunk));
			chunks++;
		}
		return chunks;
	}

	static void onResponse(Payloads.Response response) {
		Consumer<Payloads.Response> callback = callbacks.remove(response.requestId());
		if (callback != null) {
			callback.accept(response);
		} else if (Minecraft.getInstance().player != null) {
			Minecraft.getInstance().player.sendSystemMessage(Component.literal(response.message()));
		}
	}

	static void reset() {
		callbacks.clear();
	}

	private static int register(Consumer<Payloads.Response> callback) {
		int id = nextId++;
		if (callback != null) {
			callbacks.put(id, callback);
		}
		return id;
	}
}
