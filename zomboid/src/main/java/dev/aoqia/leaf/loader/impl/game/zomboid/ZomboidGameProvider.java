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

import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import dev.aoqia.leaf.api.EnvType;
import dev.aoqia.leaf.loader.api.ObjectShare;
import dev.aoqia.leaf.loader.api.VersionParsingException;
import dev.aoqia.leaf.loader.api.metadata.ModDependency;
import dev.aoqia.leaf.loader.impl.FormattedException;
import dev.aoqia.leaf.loader.impl.LeafLoaderImpl;
import dev.aoqia.leaf.loader.impl.game.GameProvider;
import dev.aoqia.leaf.loader.impl.game.GameProviderHelper;
import dev.aoqia.leaf.loader.impl.game.LibClassifier;
import dev.aoqia.leaf.loader.impl.game.patch.GameTransformer;
import dev.aoqia.leaf.loader.impl.game.zomboid.patch.BrandingPatch;
import dev.aoqia.leaf.loader.impl.game.zomboid.patch.EntrypointPatch;
import dev.aoqia.leaf.loader.impl.game.zomboid.patch.LoggerPatch;
import dev.aoqia.leaf.loader.impl.launch.LeafLauncher;
import dev.aoqia.leaf.loader.impl.launch.MappingConfiguration;
import dev.aoqia.leaf.loader.impl.metadata.BuiltinModMetadata;
import dev.aoqia.leaf.loader.impl.metadata.ModDependencyImpl;
import dev.aoqia.leaf.loader.impl.util.Arguments;
import dev.aoqia.leaf.loader.impl.util.ExceptionUtil;
import dev.aoqia.leaf.loader.impl.util.LoaderUtil;
import dev.aoqia.leaf.loader.impl.util.SystemProperties;
import dev.aoqia.leaf.loader.impl.util.log.Log;
import dev.aoqia.leaf.loader.impl.util.log.LogCategory;

public class ZomboidGameProvider implements GameProvider {
    private static final String[] ALLOWED_EARLY_CLASS_PREFIXES = {};

    private static final Set<String> SENSITIVE_ARGS = new HashSet<>(Collections.emptyList());
    private static final Set<BuiltinTransform> TRANSFORM_WIDENALL_STRIPENV_CLASSTWEAKS = EnumSet.of(
        BuiltinTransform.WIDEN_ALL_PACKAGE_ACCESS, BuiltinTransform.STRIP_ENVIRONMENT,
        BuiltinTransform.CLASS_TWEAKS);
    private static final Set<BuiltinTransform> TRANSFORM_WIDENALL_CLASSTWEAKS = EnumSet.of(
        BuiltinTransform.WIDEN_ALL_PACKAGE_ACCESS, BuiltinTransform.CLASS_TWEAKS);
    private static final Set<BuiltinTransform> TRANSFORM_STRIPENV = EnumSet.of(
        BuiltinTransform.STRIP_ENVIRONMENT);
    private final List<Path> gameJars = new ArrayList<>(
        2); // env game jar and potentially common game jar
    private final Set<Path> logJars = new HashSet<>();
    private final List<Path> miscGameLibraries = new ArrayList<>(); // libraries not relevant for
    // loader's uses
    private final GameTransformer transformer = new GameTransformer(
        new LoggerPatch(),
        new EntrypointPatch(),
        new BrandingPatch());
    private EnvType envType;
    private String entrypoint;
    private Arguments arguments;
    private Collection<Path> validParentClassPath; // computed parent class path restriction
    // (loader+deps)
    private ZomboidVersion versionData;
    private boolean hasModLoader = false;

    private static void processArgumentMap(Arguments argMap, EnvType envType) {
        switch (envType) {
            case CLIENT:
            case SERVER:
                argMap.putIfNotExists("cachedir",
                    getLaunchDirectory(argMap).toAbsolutePath().normalize().toString());
                break;
        }
    }

    private static Path getLaunchDirectory(Arguments argMap) {
        return Paths.get(argMap.getOrDefault("cachedir", System.getProperty("leaf.runDir",
            Paths.get(System.getProperty("user.home")).resolve("Zomboid").toString())));
    }

    public Path getGameJar() {
        return gameJars.get(0);
    }

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
        return versionData.getId();
    }

    @Override
    public String getNormalizedGameVersion() {
        return getRawGameVersion();
    }

    @Override
    public Collection<BuiltinMod> getBuiltinMods() {
        BuiltinModMetadata.Builder metadata = new BuiltinModMetadata.Builder(getGameId(),
            getNormalizedGameVersion()).setName(getGameName());

        if (versionData.getClassVersion().isPresent()) {
            int version = versionData.getClassVersion().getAsInt() - 44;

            try {
                metadata.addDependency(new ModDependencyImpl(ModDependency.Kind.DEPENDS,
                    "java",
                    Collections.singletonList(String.format(Locale.ENGLISH, ">=%d", version))));
            } catch (VersionParsingException e) {
                throw new RuntimeException(e);
            }
        }

        return Collections.singletonList(new BuiltinMod(gameJars, metadata.build()));
    }

    @Override
    public Set<BuiltinTransform> getBuiltinTransforms(String className) {
        final boolean isZomboidClass = className.startsWith("zomboid.");
        if (!isZomboidClass) {
            // mod class TODO: exclude game libs
            return TRANSFORM_STRIPENV;
        }

        // Combined client+server JAR, strip back down to production equivalent.
        if (LeafLoaderImpl.INSTANCE.isDevelopmentEnvironment()) {
            return TRANSFORM_WIDENALL_STRIPENV_CLASSTWEAKS;
        }

        // Environment-specific JAR, inherently env stripped.
        return TRANSFORM_WIDENALL_CLASSTWEAKS;
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

    @Override
    public boolean requiresUrlClassLoader() {
        return hasModLoader;
    }

    @Override
    public boolean isEnabled() {
        return !SystemProperties.isSet(SystemProperties.SKIP_ZOMBOID_PROVIDER);
    }

    @Override
    public boolean locateGame(LeafLauncher launcher, String[] args) {
        this.envType = launcher.getEnvironmentType();
        this.arguments = new Arguments();
        arguments.parse(args);

        try {
            LibClassifier<ZomboidLibrary> classifier = new LibClassifier<>(ZomboidLibrary.class,
                envType, this);
            ZomboidLibrary envGameLib = envType == EnvType.CLIENT
                ? ZomboidLibrary.ZOMBOID_CLIENT : ZomboidLibrary.ZOMBOID_SERVER;
            Path commonGameJar = GameProviderHelper.getCommonGameJar();
            Path envGameJar = GameProviderHelper.getEnvGameJar(envType);
            boolean commonGameJarDeclared = commonGameJar != null;

            if (commonGameJarDeclared) {
                if (envGameJar != null) {
                    classifier.process(envGameJar, ZomboidLibrary.ZOMBOID_COMMON);
                }

                classifier.process(commonGameJar);
            } else if (envGameJar != null) {
                classifier.process(envGameJar);
            }

            classifier.process(launcher.getClassPath());

            envGameJar = classifier.getOrigin(envGameLib);
            if (envGameJar == null) {
                return false;
            }

            commonGameJar = classifier.getOrigin(ZomboidLibrary.ZOMBOID_COMMON);

            if (commonGameJarDeclared && commonGameJar == null) {
                Log.warn(LogCategory.GAME_PROVIDER,
                    "The declared common game jar didn't contain any of the expected classes!");
            }

            gameJars.add(envGameJar);

            if (commonGameJar != null && !commonGameJar.equals(envGameJar)) {
                gameJars.add(commonGameJar);
            }

            entrypoint = classifier.getClassName(envGameLib);
            hasModLoader = classifier.has(ZomboidLibrary.MODLOADER);
            Log.configureBuiltin(true, false);

            miscGameLibraries.addAll(classifier.getUnmatchedOrigins());
            validParentClassPath = classifier.getSystemLibraries();
        } catch (IOException e) {
            throw ExceptionUtil.wrap(e);
        }

        // expose obfuscated jar locations for mods to more easily remap code from obfuscated to
        // intermediary
        ObjectShare share = LeafLoaderImpl.INSTANCE.getObjectShare();
        share.put("leaf-loader:inputGameJar", gameJars.get(0)); // deprecated
        share.put("leaf-loader:inputGameJars",
            Collections.unmodifiableList(
                new ArrayList<>(gameJars))); // need to make copy as gameJars is later

        String version = arguments.remove(Arguments.GAME_VERSION);
        if (version == null) {
            version = System.getProperty(SystemProperties.GAME_VERSION);
        }
        versionData = ZomboidVersionLookup.getVersion(gameJars, entrypoint, version);

        processArgumentMap(arguments, envType);
        return true;
    }

    @Override
    public void initialize(LeafLauncher launcher) {
        launcher.setValidParentClassPath(validParentClassPath);

        String gameNs = System.getProperty(SystemProperties.GAME_MAPPING_NAMESPACE);

        if (gameNs == null) {
            List<String> mappingNamespaces;

            if (launcher.isDevelopment()) {
                gameNs = MappingConfiguration.NAMED_NAMESPACE;
            } else if (
                (mappingNamespaces = launcher.getMappingConfiguration().getNamespaces()) == null
                || mappingNamespaces.contains(MappingConfiguration.OFFICIAL_NAMESPACE)) {
                gameNs = MappingConfiguration.OFFICIAL_NAMESPACE;
            } else {
                gameNs = envType == EnvType.CLIENT ? MappingConfiguration.CLIENT_OFFICIAL_NAMESPACE
                    : MappingConfiguration.SERVER_OFFICIAL_NAMESPACE;
            }
        }

        // Game is obfuscated or in another namespace, so we remap.
        // NOTE: False always here due to Project Zomboid not yet being obfuscated.
        if (false && !gameNs.equals(launcher.getMappingConfiguration().getRuntimeNamespace())) {
            Map<String, Path> obfJars = new HashMap<>(3);
            String[] names = new String[gameJars.size()];

            for (int i = 0; i < gameJars.size(); i++) {
                String name;

                if (i == 0) {
                    name = envType.name().toLowerCase(Locale.ENGLISH);
                } else if (i == 1) {
                    name = "common";
                } else {
                    name = String.format(Locale.ENGLISH, "extra-%d", i - 2);
                }

                obfJars.put(name, gameJars.get(i));
                names[i] = name;
            }

            obfJars = GameProviderHelper.deobfuscate(obfJars, gameNs, getGameId(),
                getNormalizedGameVersion(), getLaunchDirectory(), launcher);

            for (int i = 0; i < gameJars.size(); i++) {
                Path newJar = obfJars.get(names[i]);
                Path oldJar = gameJars.set(i, newJar);

                if (logJars.remove(oldJar)) {
                    logJars.add(newJar);
                }
            }
        }

        // Load the logger libraries on the platform CL when in a unit test
        if (!logJars.isEmpty() && !Boolean.getBoolean(SystemProperties.UNIT_TEST)) {
            for (Path jar : logJars) {
                if (gameJars.contains(jar)) {
                    launcher.addToClassPath(jar, ALLOWED_EARLY_CLASS_PREFIXES);
                } else {
                    launcher.addToClassPath(jar);
                }
            }
        }

        transformer.locateEntrypoints(launcher, gameJars);
    }

    @Override
    public GameTransformer getEntrypointTransformer() {
        return transformer;
    }

    @Override
    public void unlockClassPath(LeafLauncher launcher) {
        for (Path gameJar : gameJars) {
            if (logJars.contains(gameJar)) {
                launcher.setAllowedPrefixes(gameJar);
            } else {
                launcher.addToClassPath(gameJar);
            }
        }

        for (Path lib : miscGameLibraries) {
            launcher.addToClassPath(lib);
        }
    }

    @Override
    public void launch(ClassLoader loader) {
        String targetClass = entrypoint;
        MethodHandle invoker;

        try {
            Class<?> c = loader.loadClass(targetClass);
            invoker = MethodHandles.lookup().findStatic(c, "main",
                MethodType.methodType(void.class, String[].class));
        } catch (NoSuchMethodException | IllegalAccessException | ClassNotFoundException e) {
            throw FormattedException.ofLocalized("exception.zomboid.invokeFailure", e);
        }

        try {
            // noinspection ConfusingArgumentToVarargsMethod
            invoker.invokeExact(arguments.toArray());
        } catch (Throwable t) {
            throw FormattedException.ofLocalized("exception.zomboid.generic", t);
        }
    }

    @Override
    public Arguments getArguments() {
        return arguments;
    }

    @Override
    public String[] getLaunchArguments(boolean sanitize) {
        if (arguments == null) {
            return new String[0];
        }

        String[] ret = arguments.toArray();
        if (!sanitize) {
            return ret;
        }

        int writeIdx = 0;

        for (int i = 0; i < ret.length; i++) {
            String arg = ret[i];

            if (i + 1 < ret.length
                && arg.startsWith("-")
                && SENSITIVE_ARGS.contains(arg.substring(2).toLowerCase(Locale.ENGLISH))) {
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

        return arguments.contains("gui");
    }

    @Override
    public boolean hasAwtSupport() {
        // MC always sets -XstartOnFirstThread for LWJGL
        // TODO: Is this true also for Zomboid?
        return !LoaderUtil.hasMacOs();
    }
}
