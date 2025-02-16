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

package dev.aoqia.loader.impl.game.zomboid;

import java.io.File;

import dev.aoqia.api.ClientModInitializer;
import dev.aoqia.api.DedicatedServerModInitializer;
import dev.aoqia.api.ModInitializer;
import dev.aoqia.loader.impl.LeafLoaderImpl;
import dev.aoqia.loader.impl.util.log.Log;
import dev.aoqia.loader.impl.util.log.LogCategory;

public final class Hooks {
    public static final String INTERNAL_NAME = Hooks.class.getName().replace('.', '/');
    public static final String LEAF = "leaf";

    public static String setWindowTitle(final String title) {
        if (title == null || title.isEmpty()) {
            Log.warn(LogCategory.GAME_PROVIDER,
                "Null or empty window title string found!", new IllegalStateException());
            return LEAF;
        }

        return title + " (" + LEAF + ")";
    }

    public static void startClient(String runDir, Class<?> gameInstance) {
        File runDirFile = new File(runDir);
        if (runDirFile.toString().equals(System.getProperty("user.home"))) {
            runDirFile = new File(".");
        }

        LeafLoaderImpl loader = LeafLoaderImpl.INSTANCE;
        loader.prepareModInit(runDirFile.toPath(), gameInstance);
        loader.invokeEntrypoints("main", ModInitializer.class,
            ModInitializer::onInitialize);
        loader.invokeEntrypoints("client", ClientModInitializer.class,
            ClientModInitializer::onInitializeClient);
    }

    public static void startServer(String runDir, Class<?> gameInstance) {
        File runDirFile = new File(runDir);
        if (runDirFile.toString().equals(System.getProperty("user.home"))) {
            runDirFile = new File(".");
        }

        LeafLoaderImpl loader = LeafLoaderImpl.INSTANCE;
        loader.prepareModInit(runDirFile.toPath(), gameInstance);
        loader.invokeEntrypoints("main", ModInitializer.class,
            ModInitializer::onInitialize);
        loader.invokeEntrypoints("server",
            DedicatedServerModInitializer.class,
            DedicatedServerModInitializer::onInitializeServer);
    }

    public static void setGameInstance(Object gameInstance) {
        LeafLoaderImpl.INSTANCE.setGameInstance(gameInstance);
    }
}
