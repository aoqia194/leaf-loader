package dev.aoqia.leaf.loader.zomboid.test;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.aoqia.leaf.api.ModInitializer;

import zombie.debug.DebugLog;
import zombie.debug.DebugLogStream;

public class TestEntrypoint implements ModInitializer {
	public static final DebugLogStream GAME_LOGGER = DebugLog.General;

	@Override
	public void onInitialize() {
		GAME_LOGGER.println("Hello leaf world from the in-game logger!");
	}
}
