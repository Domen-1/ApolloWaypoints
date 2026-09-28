package com.domen.apollowaypoints;

import com.domen.apollowaypoints.net.Payloads;
import com.domen.apollowaypoints.server.WaypointServer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Runs on both sides: registers packets and the server logic (which also serves singleplayer worlds). */
public class ApolloWaypoints implements ModInitializer {
	public static final String MOD_ID = "apollowaypoints";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	public static String version() {
		return FabricLoader.getInstance().getModContainer(MOD_ID)
			.map(mod -> mod.getMetadata().getVersion().getFriendlyString())
			.orElse("?");
	}

	@Override
	public void onInitialize() {
		Payloads.register();
		WaypointServer.init();
	}
}
