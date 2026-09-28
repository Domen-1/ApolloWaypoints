package com.domen.apollowaypoints.server;

import com.domen.apollowaypoints.format.XaeroShare;
import com.domen.apollowaypoints.model.WaypointDraft;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.level.ServerPlayer;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Notices Xaero's "Share" messages in chat and offers the sender to add the waypoint to the server.
 * The offer is a chat button running /wp confirm with a one-time token, so nothing is encoded in the command.
 */
public final class ShareListener {
	private static final long TTL_MS = TimeUnit.MINUTES.toMillis(10);
	private static final SecureRandom RANDOM = new SecureRandom();

	private record Pending(UUID player, XaeroShare.Shared share, long expiresAt) {
	}

	private final WaypointService service;
	private final Map<String, Pending> pending = new HashMap<>();

	ShareListener(WaypointService service) {
		this.service = service;
	}

	/** Returns false to keep the message out of public chat. Runs on the server thread. */
	boolean onChat(PlayerChatMessage message, ServerPlayer sender) {
		ServerConfig.ShareMode mode = WaypointServer.config().shareMode;
		if (mode == ServerConfig.ShareMode.OFF) {
			return true;
		}
		XaeroShare.Shared share = XaeroShare.parse(message.signedContent());
		// A null dimension means the waypoint was shared from another server or world.
		if (share == null || share.dimension() == null || !service.isKnownDimension(share.dimension())) {
			return true;
		}
		if (service.store().isReadOnly() || !Perms.check(sender, Perms.Perm.ADD)) {
			return true;
		}
		String token = Long.toString(RANDOM.nextLong() & Long.MAX_VALUE, 36);
		pending.put(token, new Pending(sender.getUUID(), share, System.currentTimeMillis() + TTL_MS));
		sender.sendSystemMessage(ChatUi.sharePrompt(share, token, service.store().categories(),
			service.findByName(share.dimension(), share.name())));
		return mode != ServerConfig.ShareMode.PROMPT_AND_HIDE;
	}

	Result confirm(Actor actor, String token, String category) {
		Pending p = pending.get(token);
		if (p == null || p.expiresAt() < System.currentTimeMillis()) {
			pending.remove(token);
			return Result.error("Предложение устарело — поделись точкой в Xaero ещё раз");
		}
		if (!p.player().equals(actor.uuid())) {
			return Result.error("Это предложение для другого игрока");
		}
		XaeroShare.Shared share = p.share();
		WaypointDraft draft = new WaypointDraft();
		draft.name = service.uniqueName(share.dimension(), share.name().trim());
		draft.symbol = share.symbol();
		draft.dimension = share.dimension();
		draft.x = share.x();
		draft.y = share.y();
		draft.z = share.z();
		draft.yaw = share.yaw();
		draft.color = share.color();
		draft.category = category;
		Result result = service.create(actor, draft);
		if (result.ok()) {
			pending.remove(token);
		}
		return result;
	}

	void tick() {
		long now = System.currentTimeMillis();
		pending.values().removeIf(p -> p.expiresAt() < now);
	}
}
