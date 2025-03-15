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
package dev.aoqia.leaf.loader.impl;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import dev.aoqia.leaf.api.EnvType;
import dev.aoqia.leaf.loader.LeafLoader;
import dev.aoqia.leaf.loader.api.LanguageAdapter;
import dev.aoqia.leaf.loader.api.MappingResolver;
import dev.aoqia.leaf.loader.api.ModContainer;
import dev.aoqia.leaf.loader.api.ObjectShare;
import dev.aoqia.leaf.loader.api.entrypoint.EntrypointContainer;
import dev.aoqia.leaf.loader.impl.discovery.*;
import dev.aoqia.leaf.loader.impl.entrypoint.EntrypointStorage;
import dev.aoqia.leaf.loader.impl.game.GameProvider;
import dev.aoqia.leaf.loader.impl.launch.LeafLauncherBase;
import dev.aoqia.leaf.loader.impl.launch.knot.Knot;
import dev.aoqia.leaf.loader.impl.metadata.DependencyOverrides;
import dev.aoqia.leaf.loader.impl.metadata.EntrypointMetadata;
import dev.aoqia.leaf.loader.impl.metadata.LoaderModMetadata;
import dev.aoqia.leaf.loader.impl.metadata.VersionOverrides;
import dev.aoqia.leaf.loader.impl.util.DefaultLanguageAdapter;
import dev.aoqia.leaf.loader.impl.util.ExceptionUtil;
import dev.aoqia.leaf.loader.impl.util.LoaderUtil;
import dev.aoqia.leaf.loader.impl.util.SystemProperties;
import dev.aoqia.leaf.loader.impl.util.log.Log;
import dev.aoqia.leaf.loader.impl.util.log.LogCategory;
import net.fabricmc.accesswidener.AccessWidener;
import net.fabricmc.accesswidener.AccessWidenerReader;
import org.jetbrains.annotations.VisibleForTesting;
import org.objectweb.asm.Opcodes;

@SuppressWarnings("deprecation")
public final class LeafLoaderImpl extends LeafLoader {
    public static final LeafLoaderImpl INSTANCE = InitHelper.get();

    public static final int ASM_VERSION = Opcodes.ASM9;

    public static final String VERSION = "0.0.1";
    public static final String MOD_ID = "leafloader";

    public static final String CACHE_DIR_NAME = ".leaf"; // relative to game dir
    public static final String REMAPPED_JARS_DIR_NAME = "remappedJars"; // relative to cache dir
    private static final String PROCESSED_MODS_DIR_NAME = "processedMods"; // relative to cache dir
    private static final String TMP_DIR_NAME = "tmp"; // relative to cache dir

    static {
        LoaderUtil.verifyNotInTargetCl(LeafLoaderImpl.class);
    }

    private final Map<String, ModContainerImpl> modMap = new HashMap<>();
    private final Map<String, LanguageAdapter> adapterMap = new HashMap<>();
    private final EntrypointStorage entrypointStorage = new EntrypointStorage();
    private final AccessWidener accessWidener = new AccessWidener();
    private final ObjectShare objectShare = new ObjectShareImpl();
    private final List<ModContainerImpl> mods = new ArrayList<>();
    private List<ModCandidateImpl> modCandidates;
    private boolean frozen = false;
    private Object gameInstance;
    private MappingResolver mappingResolver;
    private GameProvider provider;
    private Path gameDir;
    private Path configDir;

    private LeafLoaderImpl() {
    }

    /**
     * Freeze the FabricLoader, preventing additional mods from being loaded.
     */
    public void freeze() {
        if (frozen) {
            throw new IllegalStateException("Already frozen!");
        }

        frozen = true;
        finishModLoading();
    }

    public GameProvider getGameProvider() {
        if (provider == null) {
            throw new IllegalStateException("game provider not set (yet)");
        }

        return provider;
    }

    public void setGameProvider(GameProvider provider) {
        this.provider = provider;
        setGameDir(provider.getLaunchDirectory());
    }

    public GameProvider tryGetGameProvider() {
        return provider;
    }

    public void load() {
        if (provider == null) {
            throw new IllegalStateException("game provider not set");
        }
        if (frozen) {
            throw new IllegalStateException("Frozen - cannot load additional mods!");
        }

        try {
            setup();
        } catch (ModResolutionException exception) {
            if (exception.getCause() == null) {
                throw FormattedException.ofLocalized("exception.incompatible", exception.getMessage());
            } else {
                throw FormattedException.ofLocalized("exception.incompatible", exception);
            }
        }
    }

    private void setup() throws ModResolutionException {
        //		boolean remapRegularMods = isDevelopmentEnvironment();
        // Never remap mods because we never need to.
        boolean remapRegularMods = false;
        VersionOverrides versionOverrides = new VersionOverrides();
        DependencyOverrides depOverrides = new DependencyOverrides(configDir);

        // discover mods

        ModDiscoverer discoverer = new ModDiscoverer(versionOverrides, depOverrides);
        discoverer.addCandidateFinder(new ClasspathModCandidateFinder());
        discoverer.addCandidateFinder(new DirectoryModCandidateFinder(getModsDirectory0(), remapRegularMods));
        discoverer.addCandidateFinder(new ArgumentModCandidateFinder(remapRegularMods));

        // Zomboid-specific directories to load mods from.
        discoverer.addCandidateFinder(new DirectoryModCandidateFinder(getZomboidWorkshopPath(), remapRegularMods));

        Map<String, Set<ModCandidateImpl>> envDisabledMods = new HashMap<>();
        modCandidates = discoverer.discoverMods(this, envDisabledMods);

        // dump version and dependency overrides info

        if (!versionOverrides.getAffectedModIds().isEmpty()) {
            Log.info(LogCategory.GENERAL,
                "Versions overridden for %s",
                String.join(", ", versionOverrides.getAffectedModIds()));
        }

        if (!depOverrides.getAffectedModIds().isEmpty()) {
            Log.info(LogCategory.GENERAL,
                "Dependencies overridden for %s",
                String.join(", ", depOverrides.getAffectedModIds()));
        }

        // resolve mods

        modCandidates = ModResolver.resolve(modCandidates, getEnvironmentType(), envDisabledMods);

        dumpModList(modCandidates);
        dumpNonLeafMods(discoverer.getNonLeafMods());

        Path cacheDir = gameDir.resolve(CACHE_DIR_NAME);
        Path outputdir = cacheDir.resolve(PROCESSED_MODS_DIR_NAME);

        // runtime mod remapping

        if (remapRegularMods) {
            if (System.getProperty(SystemProperties.REMAP_CLASSPATH_FILE) == null) {
                Log.warn(LogCategory.MOD_REMAP,
                    "Runtime mod remapping disabled due to no leaf.remapClasspathFile being specified. You may need " +
                    "to update loom.");
            } else {
                RuntimeModRemapper.remap(modCandidates, cacheDir.resolve(TMP_DIR_NAME), outputdir);
            }
        }

        // shuffle mods in-dev to reduce the risk of false order reliance, apply late load requests

        if (isDevelopmentEnvironment() && System.getProperty(SystemProperties.DEBUG_DISABLE_MOD_SHUFFLE) == null) {
            Collections.shuffle(modCandidates);
        }

        String modsToLoadLate = System.getProperty(SystemProperties.DEBUG_LOAD_LATE);

        if (modsToLoadLate != null) {
            for (String modId : modsToLoadLate.split(",")) {
                for (Iterator<ModCandidateImpl> it = modCandidates.iterator(); it.hasNext(); ) {
                    ModCandidateImpl mod = it.next();

                    if (mod.getId().equals(modId)) {
                        it.remove();
                        modCandidates.add(mod);
                        break;
                    }
                }
            }
        }

        // add mods

        for (ModCandidateImpl mod : modCandidates) {
            if (!mod.hasPath() && !mod.isBuiltin()) {
                try {
                    mod.setPaths(Collections.singletonList(mod.copyToDir(outputdir, false)));
                } catch (IOException e) {
                    throw new RuntimeException("Error extracting mod " + mod, e);
                }
            }

            addMod(mod);
        }

        modCandidates = null;
    }

    @VisibleForTesting
    public void dumpNonLeafMods(List<Path> nonLeafMods) {
        if (nonLeafMods.isEmpty()) {
            return;
        }
        StringBuilder outputText = new StringBuilder();

        for (Path mod : nonLeafMods) {
            outputText.append("\n\t- ").append(mod.getFileName());
        }

        int modsCount = nonLeafMods.size();
        Log.warn(LogCategory.GENERAL, "Found %d non-leaf mod%s:%s", modsCount, modsCount != 1 ? "s" : "", outputText);
    }

    private void dumpModList(List<ModCandidateImpl> mods) {
        StringBuilder modListText = new StringBuilder();

        boolean[] lastItemOfNestLevel = new boolean[mods.size()];
        List<ModCandidateImpl> topLevelMods = mods.stream()
            .filter(mod -> mod.getParentMods().isEmpty())
            .collect(Collectors.toList());
        int topLevelModsCount = topLevelMods.size();

        for (int i = 0; i < topLevelModsCount; i++) {
            boolean lastItem = i == topLevelModsCount - 1;

            if (lastItem) {
                lastItemOfNestLevel[0] = true;
            }

            dumpModList0(topLevelMods.get(i), modListText, 0, lastItemOfNestLevel);
        }

        int modsCount = mods.size();
        Log.info(LogCategory.GENERAL, "Loading %d mod%s:%n%s", modsCount, modsCount != 1 ? "s" : "", modListText);
    }

    private void dumpModList0(ModCandidateImpl mod, StringBuilder log, int nestLevel, boolean[] lastItemOfNestLevel) {
        if (log.length() > 0) {
            log.append('\n');
        }

        for (int depth = 0; depth < nestLevel; depth++) {
            log.append(depth == 0 ? "\t" : lastItemOfNestLevel[depth] ? "     " : "   | ");
        }

        log.append(nestLevel == 0 ? "\t" : "  ");
        log.append(nestLevel == 0 ? "-" : lastItemOfNestLevel[nestLevel] ? " \\--" : " |--");
        log.append(' ');
        log.append(mod.getId());
        log.append(' ');
        log.append(mod.getVersion().getFriendlyString());

        List<ModCandidateImpl> nestedMods = new ArrayList<>(mod.getNestedMods());
        nestedMods.sort(Comparator.comparing(nestedMod -> nestedMod.getMetadata().getId()));

        if (!nestedMods.isEmpty()) {
            Iterator<ModCandidateImpl> iterator = nestedMods.iterator();
            ModCandidateImpl nestedMod;
            boolean lastItem;

            while (iterator.hasNext()) {
                nestedMod = iterator.next();
                lastItem = !iterator.hasNext();

                if (lastItem) {
                    lastItemOfNestLevel[nestLevel + 1] = true;
                }

                dumpModList0(nestedMod, log, nestLevel + 1, lastItemOfNestLevel);

                if (lastItem) {
                    lastItemOfNestLevel[nestLevel + 1] = false;
                }
            }
        }
    }

    private void finishModLoading() {
        // add mods to classpath
        // TODO: This can probably be made safer, but that's a long-term goal
        for (ModContainerImpl mod : mods) {
            if (!mod.getMetadata().getId().equals(MOD_ID) && !mod.getMetadata().getType().equals("builtin")) {
                for (Path path : mod.getCodeSourcePaths()) {
                    LeafLauncherBase.getLauncher().addToClassPath(path);
                }
            }
        }

        setupLanguageAdapters();
        setupMods();
    }

    public boolean hasEntrypoints(String key) {
        return entrypointStorage.hasEntrypoints(key);
    }

    @Override
    public <T> List<T> getEntrypoints(String key, Class<T> type) {
        return entrypointStorage.getEntrypoints(key, type);
    }

    @Override
    protected Path getModsDirectory0() {
        String directory = System.getProperty(SystemProperties.MODS_FOLDER);
        return directory != null ? Paths.get(directory) : gameDir.resolve("mods");
    }

    private Path getZomboidGamePath() {
        String dir = System.getProperty(SystemProperties.GAME_INSTALL_PATH);
        if (dir != null) {
            return Paths.get(dir);
        }

        try {
            return Paths.get("C:\\Program Files (x86)\\Steam\\steamapps\\common\\ProjectZomboid");
        } catch (InvalidPathException e) {
            throw new RuntimeException(
                "Failed to find the game directory for Project Zomboid. This could happen if Steam is not installed " +
                "at the default location, or if the game is in a different Steam library from the default library. " +
                "Please manually set it via the launch option 'leaf.gameInstallPath'.");
        }
    }

    private Path getZomboidWorkshopPath() {
        String dir = System.getProperty(SystemProperties.GAME_WORKSHOP_PATH);
        if (dir != null) {
            return Paths.get(dir);
        }

        try {
            return getZomboidGamePath().getParent().getParent().resolve("workshop/content/108600");
        } catch (InvalidPathException e) {
            throw new RuntimeException(
                "Failed to find Steam workshop directory for Project Zomboid. This could happen if your workshop " +
                "folder is separate from the Steam library folder. Please manually specify the workshop path " +
                "(workshop/content/108600) via the 'leaf.workshopFolder' system property.");
        }
    }

    @Override
    public <T> List<EntrypointContainer<T>> getEntrypointContainers(String key, Class<T> type) {
        return entrypointStorage.getEntrypointContainers(key, type);
    }

    @Override
    public <T> void invokeEntrypoints(String key, Class<T> type, Consumer<? super T> invoker) {
        if (!hasEntrypoints(key)) {
            Log.debug(LogCategory.ENTRYPOINT, "No subscribers for entrypoint '%s'", key);
            return;
        }

        RuntimeException exception = null;
        Collection<EntrypointContainer<T>> entrypoints =
            LeafLoaderImpl.INSTANCE.getEntrypointContainers(
                key,
                type);

        Log.debug(LogCategory.ENTRYPOINT, "Iterating over entrypoint '%s'", key);

        for (EntrypointContainer<T> container : entrypoints) {
            try {
                invoker.accept(container.getEntrypoint());
            } catch (Throwable t) {
                exception = ExceptionUtil.gatherExceptions(t,
                    exception,
                    exc -> new RuntimeException(String.format(
                        "Could not execute entrypoint stage '%s' due to errors, provided by '%s' at '%s'!",
                        key,
                        container.getProvider().getMetadata().getId(),
                        container.getDefinition()),
                        exc));
            }
        }

        if (exception != null) {
            throw exception;
        }
    }

    @Override
    public ObjectShare getObjectShare() {
        return objectShare;
    }

    @Override
    public MappingResolver getMappingResolver() {
        if (mappingResolver == null) {
            final String targetNamespace = LeafLauncherBase.getLauncher().getTargetNamespace();

            mappingResolver = new LazyMappingResolver(() -> new MappingResolverImpl(
                LeafLauncherBase.getLauncher().getMappingConfiguration().getMappings(),
                targetNamespace
            ), targetNamespace);
        }

        return mappingResolver;
    }

    @Override
    public Optional<ModContainer> getModContainer(String id) {
        return Optional.ofNullable(modMap.get(id));
    }

    @Override
    public Collection<ModContainer> getAllMods() {
        return Collections.unmodifiableList(mods);
    }

    @Override
    public boolean isModLoaded(String id) {
        return modMap.containsKey(id);
    }

    @Override
    public boolean isDevelopmentEnvironment() {
        return LeafLauncherBase.getLauncher().isDevelopment();
    }

    @Override
    public EnvType getEnvironmentType() {
        return LeafLauncherBase.getLauncher().getEnvironmentType();
    }

    @Override
    public Object getGameInstance() {
        return gameInstance;
    }

    /**
     * @return The game instance's root directory.
     */
    @Override
    public Path getGameDir() {
        if (gameDir == null) {
            throw new IllegalStateException("invoked too early?");
        }

        return gameDir;
    }

    private void setGameDir(Path gameDir) {
        this.gameDir = gameDir;
        this.configDir = gameDir.resolve("config");
    }

    @Override
    @Deprecated
    public File getGameDirectory() {
        return getGameDir().toFile();
    }

    /**
     * @return The game instance's configuration directory.
     */
    @Override
    public Path getConfigDir() {
        if (!Files.exists(configDir)) {
            try {
                Files.createDirectories(configDir);
            } catch (IOException e) {
                throw new RuntimeException("Creating config directory", e);
            }
        }

        return configDir;
    }

    @Override
    @Deprecated
    public File getConfigDirectory() {
        return getConfigDir().toFile();
    }

    @Override
    public String[] getLaunchArguments(boolean sanitize) {
        return getGameProvider().getLaunchArguments(sanitize);
    }

    /**
     * Sets the game instance. This is only used in 20w22a+ by the dedicated server and should not be called by anything
     * else.
     */
    public void setGameInstance(Object gameInstance) {
        if (getEnvironmentType() != EnvType.SERVER) {
            throw new UnsupportedOperationException("Cannot set game instance on a client!");
        }

        if (this.gameInstance != null) {
            throw new UnsupportedOperationException("Cannot overwrite current game instance!");
        }

        this.gameInstance = gameInstance;
    }

    public ModCandidateImpl getModCandidate(String id) {
        if (modCandidates == null) {
            return null;
        }

        for (ModCandidateImpl mod : modCandidates) {
            if (mod.getId().equals(id)) {
                return mod;
            }
        }

        return null;
    }

    public List<ModContainerImpl> getModsInternal() {
        return mods;
    }

    private void addMod(ModCandidateImpl candidate) throws ModResolutionException {
        ModContainerImpl container = new ModContainerImpl(candidate);
        mods.add(container);
        modMap.put(candidate.getId(), container);

        for (String provides : candidate.getProvides()) {
            modMap.put(provides, container);
        }
    }

    private void setupLanguageAdapters() {
        adapterMap.put("default", DefaultLanguageAdapter.INSTANCE);

        for (ModContainerImpl mod : mods) {
            // add language adapters
            for (Map.Entry<String, String> laEntry : mod.getInfo().getLanguageAdapterDefinitions().entrySet()) {
                if (adapterMap.containsKey(laEntry.getKey())) {
                    throw new RuntimeException(
                        "Duplicate language adapter key: " + laEntry.getKey() + "! (" + laEntry.getValue() + ", " +
                        adapterMap.get(laEntry.getKey()).getClass().getName() + ")");
                }

                try {
                    adapterMap.put(laEntry.getKey(),
                        (LanguageAdapter) Class.forName(laEntry.getValue(),
                                true,
                                LeafLauncherBase.getLauncher().getTargetClassLoader())
                            .getDeclaredConstructor()
                            .newInstance());
                } catch (Exception e) {
                    throw new RuntimeException("Failed to instantiate language adapter: " + laEntry.getKey(), e);
                }
            }
        }
    }

    private void setupMods() {
        for (ModContainerImpl mod : mods) {
            try {
                for (String in : mod.getInfo().getOldInitializers()) {
                    String adapter = mod.getInfo().getOldStyleLanguageAdapter();
                    entrypointStorage.addDeprecated(mod, adapter, in);
                }

                for (String key : mod.getInfo().getEntrypointKeys()) {
                    for (EntrypointMetadata in : mod.getInfo().getEntrypoints(key)) {
                        entrypointStorage.add(mod, key, in, adapterMap);
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException(String.format("Failed to setup mod %s (%s)",
                    mod.getInfo().getName(),
                    mod.getOrigin()), e);
            }
        }
    }

    public void loadAccessWideners() {
        AccessWidenerReader accessWidenerReader = new AccessWidenerReader(accessWidener);

        for (ModContainer modContainer : getAllMods()) {
            LoaderModMetadata modMetadata = (LoaderModMetadata) modContainer.getMetadata();
            String accessWidener = modMetadata.getAccessWidener();
            if (accessWidener == null) {
                continue;
            }

            Path path = modContainer.findPath(accessWidener).orElse(null);
            if (path == null) {
                throw new RuntimeException(String.format("Missing accessWidener file %s from mod %s",
                    accessWidener,
                    modContainer.getMetadata().getId()));
            }

            try (BufferedReader reader = Files.newBufferedReader(path)) {
                accessWidenerReader.read(reader, LeafLauncherBase.getLauncher().getTargetNamespace());
            } catch (Exception e) {
                throw new RuntimeException("Failed to read accessWidener file from mod " + modMetadata.getId(), e);
            }
        }
    }

    public void prepareModInit(Path newRunDir, Class<?> gameInstance) {
        if (!frozen) {
            throw new RuntimeException("Cannot instantiate mods when not frozen!");
        }

        if (gameInstance != null && LeafLauncherBase.getLauncher() instanceof Knot) {
            // I removed the getClass() call for gameInstance because we already pass the class node.
            ClassLoader gameClassLoader = gameInstance.getClassLoader();
            ClassLoader targetClassLoader = LeafLauncherBase.getLauncher().getTargetClassLoader();
            boolean matchesKnot = (gameClassLoader == targetClassLoader);
            boolean containsKnot = false;

            if (matchesKnot) {
                containsKnot = true;
            } else {
                gameClassLoader = gameClassLoader.getParent();

                while (gameClassLoader != null && gameClassLoader.getParent() != gameClassLoader) {
                    if (gameClassLoader == targetClassLoader) {
                        containsKnot = true;
                    }

                    gameClassLoader = gameClassLoader.getParent();
                }
            }

            if (!matchesKnot) {
                if (containsKnot) {
                    Log.info(LogCategory.KNOT, "Environment: Target class loader is parent of game class loader.");
                } else {
                    Log.warn(LogCategory.KNOT,
                        "\n\n* CLASS LOADER MISMATCH! THIS IS VERY BAD AND WILL PROBABLY CAUSE WEIRD ISSUES! *\n"
                        + " - Expected game class loader: %s\n"
                        + " - Actual game class loader: %s\n"
                        + "Could not find the expected class loader in game class loader parents!\n",
                        LeafLauncherBase.getLauncher().getTargetClassLoader(),
                        gameClassLoader);
                }
            }
        }

        this.gameInstance = gameInstance;

        if (gameDir != null) {
            try {
                if (!gameDir.toRealPath().equals(newRunDir.toRealPath())) {
                    Log.warn(LogCategory.GENERAL,
                        "Inconsistent game execution directories: engine says %s, while initializer says %s...",
                        newRunDir.toRealPath(),
                        gameDir.toRealPath());
                    setGameDir(newRunDir);
                }
            } catch (IOException e) {
                Log.warn(LogCategory.GENERAL, "Exception while checking game execution directory consistency!", e);
            }
        } else {
            setGameDir(newRunDir);
        }
    }

    public AccessWidener getAccessWidener() {
        return accessWidener;
    }

    /**
     * Provides singleton for static init assignment regardless of load order.
     */
    public static class InitHelper {
        private static LeafLoaderImpl instance;

        public static LeafLoaderImpl get() {
            if (instance == null) {
                instance = new LeafLoaderImpl();
            }

            return instance;
        }
    }
}
