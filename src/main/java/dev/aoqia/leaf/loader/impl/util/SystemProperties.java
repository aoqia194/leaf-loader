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
package dev.aoqia.leaf.loader.impl.util;

public final class SystemProperties {
    // whether fabric loader is running in a development environment / mode, affects class path
    // mod discovery, remapping, logging, ...
    public static final String DEVELOPMENT = "leaf.development";
    // Should leaf use a class loader that is an instance of URLClassLoader?
    public static final String USE_COMPAT_CL = "leaf.loader.useCompatibilityClassLoader";
    public static final String SIDE = "leaf.side";
    // File to source mappings from, defaults to mappings/mappings.tiny on the classpath.
    public static final String MAPPING_PATH = "leaf.mappingPath";
    // Mapping namespace used by the game, defaults to named if DEVELOPMENT is set or official
    // otherwise.
    public static final String GAME_MAPPING_NAMESPACE = "leaf.gameMappingNamespace";
    // Mapping namespace to use at runtime, defaults to named if DEVELOPMENT is set or official
    // otherwise.
    public static final String RUNTIME_MAPPING_NAMESPACE = "leaf.runtimeMappingNamespace";
    // skips the embedded Zbomoid game provider, letting ServiceLoader-provided ones take over
    public static final String SKIP_ZOMBOID_PROVIDER = "leaf.skipZomboidProvider";
    // game jar paths for common/client/server, replaces lookup from class path if present, env
    // specific takes precedence
    public static final String GAME_JAR_PATH = "leaf.gameJarPath";
    public static final String GAME_JAR_PATH_CLIENT = "leaf.gameJarPath.client";
    public static final String GAME_JAR_PATH_SERVER = "leaf.gameJarPath.server";
    // Game library paths, replaces lookup from classpath if present.
    public static final String GAME_LIBRARIES = "leaf.gameLibraries";
    // set the game version for the builtin game mod/dependencies, bypassing auto-detection
    public static final String GAME_VERSION = "leaf.gameVersion";
    // fallback log file for the builtin log handler (dumped on exit if not replaced with another
    // handler)
    public static final String LOG_FILE = "leaf.log.file";
    // minimum log level for builtin log handler
    public static final String LOG_LEVEL = "leaf.log.level";
    // a path to a directory to replace the default mod search directory
    public static final String MODS_FOLDER = "leaf.modsFolder";
    // a path to a directory to replace the default Project Zomboid Steam search directory
    public static final String GAME_INSTALL_PATH = "leaf.gameInstallPath";
    // a path to the Steam workshop path for Zomboid (appid 108600).
    public static final String GAME_WORKSHOP_PATH = "leaf.gameWorkshopPath";
    // Disables workshop mod loading for developer environments.
    public static final String DISABLE_WORKSHOP_MODS = "leaf.disableWorkshopMods";
    // Does a dry run of the mod init stage. Stops the loader after mods are discovered.
    // The mods are not and should not be loaded at all.
    public static final String DRY_RUN_MOD_DISCOVERY = "leaf.dryRunModDiscovery";
    // additional mods to load (path separator separated paths, @ prefix for meta-file with each
    // line referencing an actual file)
    public static final String ADD_MODS = "leaf.addMods";
    // a comma-separated list of mod ids to disable, even if they're discovered. mostly useful
    // for unit testing.
    public static final String DISABLE_MOD_IDS = "leaf.debug.disableModIds";
    // file containing the class path for in-dev runtime mod remapping
    public static final String REMAP_CLASSPATH_FILE = "leaf.remapClasspathFile";
    // class path groups to map multiple class path entries to a mod (paths separated by path
    // separator, groups by double path separator)
    public static final String PATH_GROUPS = "leaf.classPathGroups";
    // enable the fixing of package access errors in the game jar(s)
    public static final String FIX_PACKAGE_ACCESS = "leaf.fixPackageAccess";
    // system level libraries, matching code sources will not be assumed to be part of the game
    // or mods and remain on the system class path, paths separated by path separator
    // @ prefix for meta-file with each line referencing an actual file
    public static final String SYSTEM_LIBRARIES = "leaf.systemLibraries";
    // throw exceptions from entrypoints, discovery etc. directly instead of gathering and
    // attaching as suppressed
    public static final String DEBUG_THROW_DIRECTLY = "leaf.debug.throwDirectly";
    // logs library classification activity
    public static final String DEBUG_LOG_LIB_CLASSIFICATION = "leaf.debug.logLibClassification";
    // logs class loading
    public static final String DEBUG_LOG_CLASS_LOAD = "leaf.debug.logClassLoad";
    // logs class loading errors to uncover caught exceptions without adequate logging
    public static final String DEBUG_LOG_CLASS_LOAD_ERRORS = "leaf.debug.logClassLoadErrors";
    // logs class transformation errors to uncover caught exceptions without adequate logging
    public static final String DEBUG_LOG_TRANSFORM_ERRORS = "leaf.debug.logTransformErrors";
    // disables system class path isolation, allowing bogus lib accesses (too early, transient jars)
    public static final String DEBUG_DISABLE_CLASS_PATH_ISOLATION = "leaf.debug" +
                                                                    ".disableClassPathIsolation";
    // disables mod load order shuffling to be the same in-dev as in production
    public static final String DEBUG_DISABLE_MOD_SHUFFLE = "leaf.debug.disableModShuffle";
    // workaround for bad load order dependencies
    public static final String DEBUG_LOAD_LATE = "leaf.debug.loadLate";
    // override the mod discovery timeout, unit in seconds, <= 0 to disable
    public static final String DEBUG_DISCOVERY_TIMEOUT = "leaf.debug.discoveryTimeout";
    // override the mod resolution timeout, unit in seconds, <= 0 to disable
    public static final String DEBUG_RESOLUTION_TIMEOUT = "leaf.debug.resolutionTimeout";
    // replace mod versions (modA:versionA,modB:versionB,...)
    public static final String DEBUG_REPLACE_VERSION = "leaf.debug.replaceVersion";
    // deobfuscate the game jar with the classpath
    public static final String DEBUG_DEOBFUSCATE_WITH_CLASSPATH = "leaf.debug" +
                                                                  ".deobfuscateWithClasspath";
    // whether fabric loader is running in a unit test, this affects logging classpath setup
    public static final String UNIT_TEST = "leaf.unitTest";

    // Zomboid-specific props
    public static final String ZOMBOID_STEAM = "zomboid.steam";

    public static boolean isSet(String property) {
        final String val = System.getProperty(property);
        return val != null && !val.equalsIgnoreCase("false");
    }
}
