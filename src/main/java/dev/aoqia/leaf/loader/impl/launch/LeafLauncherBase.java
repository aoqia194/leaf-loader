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
package dev.aoqia.leaf.loader.impl.launch;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.util.Map;

import dev.aoqia.leaf.loader.impl.FormattedException;
import dev.aoqia.leaf.loader.impl.LeafLoaderImpl;
import dev.aoqia.leaf.loader.impl.game.GameProvider;
import dev.aoqia.leaf.loader.impl.gui.LeafGuiEntry;
import dev.aoqia.leaf.loader.impl.util.SystemProperties;
import dev.aoqia.leaf.loader.impl.util.log.Log;
import dev.aoqia.leaf.loader.impl.util.log.LogCategory;

import org.jetbrains.annotations.VisibleForTesting;
import org.spongepowered.asm.mixin.MixinEnvironment;

public abstract class LeafLauncherBase implements LeafLauncher {
    protected static final boolean IS_DEVELOPMENT = SystemProperties.isSet(
        SystemProperties.DEVELOPMENT);
    private static final MappingConfiguration mappingConfiguration = new MappingConfiguration();
    private static boolean mixinReady;
    private static Map<String, Object> properties;
    private static LeafLauncher launcher;

    protected LeafLauncherBase() {
        setLauncher(this);
    }

    public static Class<?> getClass(String className) throws ClassNotFoundException {
        return Class.forName(className, true, getLauncher().getTargetClassLoader());
    }

    public static LeafLauncher getLauncher() {
        return launcher;
    }

    @VisibleForTesting
    public static void setLauncher(LeafLauncher launcherA) {
        if (launcher != null && launcher != launcherA) {
            throw new RuntimeException("Duplicate setLauncher call!");
        }

        launcher = launcherA;
    }

    public static Map<String, Object> getProperties() {
        return properties;
    }

    protected static void setProperties(Map<String, Object> propertiesA) {
        if (properties != null && properties != propertiesA) {
            throw new RuntimeException("Duplicate setProperties call!");
        }

        properties = propertiesA;
    }

    protected static void handleFormattedException(FormattedException exc) {
        Throwable actualExc = exc.getMessage() != null ? exc : exc.getCause();
        Log.error(LogCategory.GENERAL, exc.getMainText(), actualExc);

        GameProvider gameProvider = LeafLoaderImpl.INSTANCE.tryGetGameProvider();

        if (gameProvider == null || !gameProvider.displayCrash(actualExc, exc.getDisplayedText())) {
            LeafGuiEntry.displayError(exc.getDisplayedText(), actualExc, true);
        } else {
            System.exit(1);
        }

        throw new AssertionError("exited");
    }

    protected static void setupUncaughtExceptionHandler() {
        Thread mainThread = Thread.currentThread();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            try {
                if (e instanceof FormattedException) {
                    handleFormattedException((FormattedException) e);
                } else {
                    String mainText = String.format("Uncaught exception in thread \"%s\"",
                        t.getName());
                    Log.error(LogCategory.GENERAL, mainText, e);

                    GameProvider gameProvider = LeafLoaderImpl.INSTANCE.tryGetGameProvider();

                    if (Thread.currentThread() == mainThread
                        && (gameProvider == null || !gameProvider.displayCrash(e, mainText))) {
                        LeafGuiEntry.displayError(mainText, e, false);
                    }
                }
            } catch (Throwable e2) { // just in case
                e.addSuppressed(e2);

                try {
                    e.printStackTrace();
                } catch (Throwable e3) {
                    PrintWriter pw = new PrintWriter(new FileOutputStream(FileDescriptor.err));
                    e.printStackTrace(pw);
                    pw.flush();
                }
            }
        });
    }

    protected static void finishMixinBootstrapping() {
        if (mixinReady) {
            throw new RuntimeException(
                "Must not call LeafLauncherBase.finishMixinBootstrapping() twice!");
        }

        try {
            Method m = MixinEnvironment.class.getDeclaredMethod("gotoPhase",
                MixinEnvironment.Phase.class);
            m.setAccessible(true);
            m.invoke(null, MixinEnvironment.Phase.INIT);
            m.invoke(null, MixinEnvironment.Phase.DEFAULT);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        mixinReady = true;
    }

    public static boolean isMixinReady() {
        return mixinReady;
    }

    @Override
    public MappingConfiguration getMappingConfiguration() {
        return mappingConfiguration;
    }

    @Override
    public boolean isDevelopment() {
        return IS_DEVELOPMENT;
    }

    @Override
    public String getDefaultRuntimeNamespace() {
        String ret = System.getProperty(SystemProperties.RUNTIME_MAPPING_NAMESPACE);
        if (ret != null) {
            return ret;
        }

        return IS_DEVELOPMENT ? MappingConfiguration.NAMED_NAMESPACE
            : MappingConfiguration.OFFICIAL_NAMESPACE;
    }
}
