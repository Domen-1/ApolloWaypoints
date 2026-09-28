package com.domen.apollowaypoints.server;

import com.domen.apollowaypoints.ApolloWaypoints;
import com.domen.apollowaypoints.net.Payloads;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Path;
import java.util.function.Consumer;

/** Server side wiring. One {@link Running} exists while a server (dedicated or singleplayer) is up. */
public final class WaypointServer {
	private static final int PURGE_INTERVAL_TICKS = 20 * 60 * 60;

	public record Running(MinecraftServer server, WaypointStore store, HistoryLog history, Syncer syncer,
		WaypointService service, ShareListener shares) {
	}

	private static ServerConfig config;
	private static Running running;
	private static int ticks;

	private WaypointServer() {
	}

	public static void init() {
		config = ServerConfig.load();

		ServerLifecycleEvents.SERVER_STARTING.register(WaypointServer::start);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> ifRunning(r -> r.store().save()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> running = null);
		ServerTickEvents.END_SERVER_TICK.register(server -> ifRunning(WaypointServer::tick));
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> WpCommand.register(dispatcher));

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> ifRunning(r -> {
			if (r.store().isReadOnly() && Perms.check(handler.player, Perms.Perm.ADMIN)) {
				handler.player.sendSystemMessage(Component.literal("Apollo Waypoints: файл точек не прочитался ("
					+ r.store().loadError() + "), изменения отключены. Смотри лог сервера.").withStyle(ChatFormatting.RED));
			}
		}));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> ifRunning(r -> r.syncer().onDisconnect(handler.player)));
		ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) -> running == null || running.shares().onChat(message, sender));

		ServerPlayNetworking.registerGlobalReceiver(Payloads.Hello.TYPE, (payload, context) ->
			onServerThread(context.server(), r -> r.syncer().onHello(context.player(), payload)));
		ServerPlayNetworking.registerGlobalReceiver(Payloads.Create.TYPE, (payload, context) ->
			onServerThread(context.server(), r -> respond(context.player(), payload.requestId(),
				r.service().create(Actor.of(context.player()), payload.draft()))));
		ServerPlayNetworking.registerGlobalReceiver(Payloads.Update.TYPE, (payload, context) ->
			onServerThread(context.server(), r -> respond(context.player(), payload.requestId(),
				r.service().update(Actor.of(context.player()), payload.id(), payload.draft()))));
		ServerPlayNetworking.registerGlobalReceiver(Payloads.Remove.TYPE, (payload, context) ->
			onServerThread(context.server(), r -> respond(context.player(), payload.requestId(),
				r.service().remove(Actor.of(context.player()), payload.id()))));
		ServerPlayNetworking.registerGlobalReceiver(Payloads.Teleport.TYPE, (payload, context) ->
			onServerThread(context.server(), r -> respond(context.player(), payload.requestId(),
				r.service().teleport(Actor.of(context.player()), payload.id()))));
		ServerPlayNetworking.registerGlobalReceiver(Payloads.Import.TYPE, (payload, context) ->
			onServerThread(context.server(), r -> {
				WaypointService.ImportSummary summary = r.service().importDrafts(Actor.of(context.player()), payload.drafts());
				String message = "Добавлено " + summary.added() + ", пропущено " + summary.skipped() + " (такие имена уже есть)"
					+ (summary.errors().isEmpty() ? "" : ", ошибок " + summary.errors().size() + ": " + summary.errors().getFirst());
				ServerPlayNetworking.send(context.player(), new Payloads.Response(payload.requestId(), summary.errors().isEmpty(), message));
			}));
	}

	private static void start(MinecraftServer server) {
		Path dir = server.getWorldPath(LevelResource.ROOT).resolve(ApolloWaypoints.MOD_ID).normalize();
		WaypointStore store = WaypointStore.load(dir);
		store.purgeTrash(config.trashDays);
		HistoryLog history = new HistoryLog(dir);
		Syncer syncer = new Syncer(server, store);
		WaypointService service = new WaypointService(server, store, history, syncer);
		running = new Running(server, store, history, syncer, service, new ShareListener(service));
		ticks = 0;
	}

	private static void tick(Running r) {
		r.store().tick();
		if (++ticks % PURGE_INTERVAL_TICKS == 0) {
			r.store().purgeTrash(config.trashDays);
		}
		if (ticks % 1200 == 0) {
			r.shares().tick();
		}
	}

	public static void reloadConfig() {
		config = ServerConfig.load();
		ifRunning(r -> {
			// Permission levels may have changed: resend command trees and client permissions.
			for (ServerPlayer player : r.server().getPlayerList().getPlayers()) {
				r.server().getCommands().sendCommands(player);
			}
			r.syncer().resendWelcome();
		});
	}

	public static ServerConfig config() {
		return config;
	}

	public static boolean isRunning() {
		return running != null;
	}

	public static Running running() {
		if (running == null) {
			throw new IllegalStateException("Apollo Waypoints: server is not running");
		}
		return running;
	}

	private static void ifRunning(Consumer<Running> action) {
		if (running != null) {
			action.accept(running);
		}
	}

	private static void onServerThread(MinecraftServer server, Consumer<Running> action) {
		server.execute(() -> ifRunning(action));
	}

	private static void respond(ServerPlayer player, int requestId, Result result) {
		ServerPlayNetworking.send(player, new Payloads.Response(requestId, result.ok(), result.message()));
	}
}
