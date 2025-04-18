/*
 * Copyright 2025 aoqia, FabricMC
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
package dev.aoqia.leaf.loader.impl.game.zomboid;

import java.io.File;

import dev.aoqia.leaf.api.ClientModInitializer;
import dev.aoqia.leaf.api.DedicatedServerModInitializer;
import dev.aoqia.leaf.api.ModInitializer;
import dev.aoqia.leaf.loader.impl.LeafLoaderImpl;
import dev.aoqia.leaf.loader.impl.launch.LeafLauncher;
import dev.aoqia.leaf.loader.impl.util.log.Log;
import dev.aoqia.leaf.loader.impl.util.log.LogCategory;
import dev.aoqia.leaf.loader.impl.util.log.LogHandler;

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

    /**
     * Sets up the Log class for leaf. Moved from ZomboidGameProvider to here so that the
     * EntrypointPatch can access it easily.
     *
     * @param launcher The LeafLauncher object instance
     * @param useTargetCl Use the target class loader of the launcher
     */
    public static void setupLogHandler(LeafLauncher launcher, boolean useTargetCl) {
        System.setProperty("log4j2.formatMsgNoLookups", "true");

        try {
            final String logHandlerClsName = "dev.aoqia.leaf.loader.impl.game.zomboid" +
                                             ".ZomboidLogHandler";
            ClassLoader prevCl = Thread.currentThread().getContextClassLoader();
            Class<?> logHandlerCls;

            if (useTargetCl) {
                Thread.currentThread().setContextClassLoader(launcher.getTargetClassLoader());
                logHandlerCls = launcher.loadIntoTarget(logHandlerClsName);
            } else {
                logHandlerCls = Class.forName(logHandlerClsName);
            }

            Log.init((LogHandler) logHandlerCls.getConstructor().newInstance());
            Thread.currentThread().setContextClassLoader(prevCl);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Sets up the Log class for leaf. Moved from ZomboidGameProvider to here so that the
     * EntrypointPatch can access it easily.
     */
    public static void setupLogHandler() {
        System.setProperty("log4j2.formatMsgNoLookups", "true");

        try {
            Class<?> logHandlerCls = Class.forName(
                "dev.aoqia.leaf.loader.impl.game.zomboid.ZomboidLogHandler");
            Log.init((LogHandler) logHandlerCls.getConstructor().newInstance());
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
