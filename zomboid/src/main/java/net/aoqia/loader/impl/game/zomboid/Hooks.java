/*
 * Copyright 2016 FabricMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.aoqia.loader.impl.game.zomboid;

import java.io.File;

import net.aoqia.api.ClientModInitializer;
import net.aoqia.api.DedicatedServerModInitializer;
import net.aoqia.api.ModInitializer;
import net.aoqia.loader.impl.util.log.Log;
import net.aoqia.loader.impl.util.log.LogCategory;

public final class Hooks {
	public static final String INTERNAL_NAME = Hooks.class.getName().replace('.', '/');

	public static final String LEAF = "leaf";
	public static final String VANILLA = "vanilla";

	public static String insertBranding(final String brand) {
		if (brand == null || brand.isEmpty()) {
			Log.warn(LogCategory.GAME_PROVIDER, "Null or empty branding found!", new IllegalStateException());
			return LEAF;
		}

		return VANILLA.equals(brand) ? LEAF : brand + ',' + LEAF;
	}

	public static void startClient(String runDir, Object gameInstance) {
        File runDirFile = new File(runDir);
        if (runDirFile.toString().equals(System.getProperty("user.home"))) {
            runDirFile = new File(".");
		}

		net.aoqia.loader.impl.LeafLoaderImpl loader = net.aoqia.loader.impl.LeafLoaderImpl.INSTANCE;
		loader.prepareModInit(runDirFile.toPath(), gameInstance);
		loader.invokeEntrypoints("main", ModInitializer.class, ModInitializer::onInitialize);
		loader.invokeEntrypoints("client", ClientModInitializer.class, ClientModInitializer::onInitializeClient);
	}

	public static void startServer(String runDir, Object gameInstance) {
        File runDirFile = new File(runDir);
        if (runDirFile.toString().equals(System.getProperty("user.home"))) {
            runDirFile = new File(".");
		}

		net.aoqia.loader.impl.LeafLoaderImpl loader = net.aoqia.loader.impl.LeafLoaderImpl.INSTANCE;
		loader.prepareModInit(runDirFile.toPath(), gameInstance);
		loader.invokeEntrypoints("main", ModInitializer.class, ModInitializer::onInitialize);
		loader.invokeEntrypoints("server", DedicatedServerModInitializer.class, DedicatedServerModInitializer::onInitializeServer);
	}

	public static void setGameInstance(Object gameInstance) {
		net.aoqia.loader.impl.LeafLoaderImpl.INSTANCE.setGameInstance(gameInstance);
	}
}
