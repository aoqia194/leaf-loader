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

import net.aoqia.api.EnvType;
import net.aoqia.loader.impl.FormattedException;
import net.aoqia.loader.impl.game.GameProvider;
import net.aoqia.loader.impl.game.LibClassifier;
import net.aoqia.loader.impl.game.patch.GameTransformer;
import net.aoqia.loader.impl.launch.FabricLauncher;
import net.aoqia.loader.impl.util.Arguments;
import net.aoqia.loader.impl.util.ExceptionUtil;
import net.aoqia.loader.impl.util.LoaderUtil;
import net.aoqia.loader.impl.util.SystemProperties;

import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class ZomboidGameProvider implements GameProvider {
    private static final Set<String> SENSITIVE_ARGS = new HashSet<>();
    private final GameTransformer transformer = new GameTransformer();
    private EnvType envType;
    private String entrypoint;
    private Arguments arguments;
    private Collection<Path> validParentClassPath; // computed parent class path restriction (loader+deps)
    private ZomboidVersion versionData;
    private boolean hasModLoader = false;

    @Override
    public String getGameId() {
        return "zomboid";
    }

    @Override
    public String getGameName() {
        return "Project Zomboid";
    }

    @Override
    public String getRawGameVersion() {
        return versionData.getBuildVersion();
    }

    @Override
    public String getNormalizedGameVersion() {
        return getRawGameVersion();
    }

    @Override
    public Collection<BuiltinMod> getBuiltinMods() {
        return Collections.emptyList();
    }

    @Override
    public String getEntrypoint() {
        return entrypoint;
    }

    @Override
    public Path getLaunchDirectory() {
        if (arguments == null) {
            return Paths.get(".");
        }

        return getLaunchDirectory(arguments);
    }

    private static Path getLaunchDirectory(Arguments argMap) {
        return Paths.get(argMap.getOrDefault("gameDir", "."));
    }

    @Override
    public boolean isObfuscated() {
        // FIXME: Game isn't technically obfuscated!
        return false;
    }

    @Override
    public boolean requiresUrlClassLoader() {
        return hasModLoader;
    }

    @Override
    public boolean isEnabled() {
        return System.getProperty(SystemProperties.SKIP_EMBEDDED_PROVIDER) == null;
    }

    @Override
    public boolean locateGame(FabricLauncher launcher, String[] args) {
        this.envType = launcher.getEnvironmentType();

        this.arguments = new Arguments();
        this.arguments.parse(args);

        try {
            LibClassifier<ZomboidLibrary> classifier = new LibClassifier<>(ZomboidLibrary.class, envType, this);
            classifier.process(launcher.getClassPath());

            this.entrypoint = classifier.getClassName(
                    (envType == EnvType.CLIENT) ? ZomboidLibrary.ZOMBOID_CLIENT : ZomboidLibrary.ZOMBOID_SERVER);
            this.hasModLoader = classifier.has(ZomboidLibrary.MODLOADER);

            this.validParentClassPath = classifier.getSystemLibraries();
        } catch (IOException e) {
            throw ExceptionUtil.wrap(e);
        }

        processArgumentMap(arguments, envType);
        return true;
    }

    private static void processArgumentMap(Arguments argMap, EnvType envType) {
        switch (envType) {
            case CLIENT:
                if (!argMap.containsKey("accessToken")) {
                    argMap.put("accessToken", "FabricMC");
                }

                if (!argMap.containsKey("version")) {
                    argMap.put("version", "Fabric");
                }

                String versionType = "";

                if (argMap.containsKey("versionType") && !argMap.get("versionType").equalsIgnoreCase("release")) {
                    versionType = argMap.get("versionType") + "/";
                }

                argMap.put("versionType", versionType + "Fabric");

                if (!argMap.containsKey("gameDir")) {
                    argMap.put("gameDir", getLaunchDirectory(argMap).toAbsolutePath().normalize().toString());
                }

                break;
            case SERVER:
                argMap.remove("version");
                argMap.remove("gameDir");
                argMap.remove("assetsDir");
                break;
        }
    }

    @Override
    public void initialize(FabricLauncher launcher) {
        launcher.setValidParentClassPath(validParentClassPath);
    }

    @Override
    public GameTransformer getEntrypointTransformer() {
        return transformer;
    }

    @Override
    public void unlockClassPath(FabricLauncher launcher) {
        launcher.addToClassPath(Paths.get("zombie/ZomboidGlobals.class"));
    }

    @Override
    public void launch(ClassLoader loader) {
        MethodHandle invoker;

        try {
            Class<?> c = loader.loadClass(this.entrypoint);
            invoker = MethodHandles.lookup().findStatic(c, "main", MethodType.methodType(void.class, String[].class));
        } catch (NoSuchMethodException | IllegalAccessException | ClassNotFoundException e) {
            throw FormattedException.ofLocalized("exception.zomboid.invokeFailure", e);
        }

        try {
            invoker.invokeExact(arguments.toArray());
        } catch (Throwable t) {
            throw FormattedException.ofLocalized("exception.zomboid.generic", t);
        }
    }

    @Override
    public Arguments getArguments() {
        return this.arguments;
    }

    @Override
    public String[] getLaunchArguments(boolean sanitize) {
        if (this.arguments == null) {
            return new String[0];
        }

        String[] ret = this.arguments.toArray();
        if (!sanitize) {
            return ret;
        }

        int writeIdx = 0;

        for (int i = 0; i < ret.length; i++) {
            String arg = ret[i];

            if (i + 1 < ret.length && arg.startsWith("--") &&
                SENSITIVE_ARGS.contains(arg.substring(2).toLowerCase(Locale.ENGLISH))) {
                i++; // skip value
            } else {
                ret[writeIdx++] = arg;
            }
        }

        if (writeIdx < ret.length) {
            ret = Arrays.copyOf(ret, writeIdx);
        }

        return ret;
    }

    @Override
    public boolean canOpenErrorGui() {
        if (arguments == null || envType == EnvType.CLIENT) {
            return true;
        }

        List<String> extras = arguments.getExtraArgs();
        return extras.contains("gui") && extras.contains("--gui");
    }

    @Override
    public boolean hasAwtSupport() {
        // MC always sets -XstartOnFirstThread for LWJGL
        return !LoaderUtil.hasMacOs();
    }
}
