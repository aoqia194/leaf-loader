package dev.aoqia.leaf.loader.zomboid.test;

import dev.aoqia.leaf.api.ModInitializer;

import zombie.debug.DebugLog;

public class TestEntrypoint implements ModInitializer {
	@Override
	public void onInitialize() {
		DebugLog.log("Hello leaf world from the in-game logger!");
	}
}
