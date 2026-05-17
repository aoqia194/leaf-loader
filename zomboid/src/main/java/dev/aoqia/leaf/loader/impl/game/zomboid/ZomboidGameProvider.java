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

package dev.aoqia.leaf.loader.impl.game.zomboid;

import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import dev.aoqia.leaf.api.EnvType;
import dev.aoqia.leaf.loader.api.ObjectShare;
import dev.aoqia.leaf.loader.api.VersionParsingException;
import dev.aoqia.leaf.loader.api.metadata.ModDependency;
import dev.aoqia.leaf.loader.impl.LeafLoaderImpl;
import dev.aoqia.leaf.loader.impl.FormattedException;
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
import dev.aoqia.leaf.loader.impl.util.log.LogHandler;

public class ZomboidGameProvider implements GameProvider {
	private static final String[] ALLOWED_EARLY_CLASS_PREFIXES = { "org.apache.logging.log4j." };

	private static final Set<String> SENSITIVE_ARGS = new HashSet<>();

	private EnvType envType;
	private String entrypoint;
	private Arguments arguments;
	private final List<Path> gameJars = new ArrayList<>(2); // env game jar and potentially common game jar
	private final Set<Path> logJars = new HashSet<>();
	private boolean log4jAvailable;
	private boolean slf4jAvailable;
	private final List<Path> miscGameLibraries = new ArrayList<>(); // libraries not relevant for loader's uses
	private Collection<Path> validParentClassPath; // computed parent class path restriction (loader+deps)
	private ZomboidVersion versionData;
	private boolean hasModLoader = false;

	private final GameTransformer transformer = new GameTransformer(
			new LoggerPatch(),
			new EntrypointPatch(),
			new BrandingPatch());

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
		BuiltinModMetadata.Builder metadata = new BuiltinModMetadata.Builder(getGameId(), getNormalizedGameVersion())
				.setName(getGameName());

		if (versionData.getClassVersion().isPresent()) {
			int version = versionData.getClassVersion().getAsInt() - 44;

			try {
				metadata.addDependency(new ModDependencyImpl(ModDependency.Kind.DEPENDS, "java", Collections.singletonList(String.format(Locale.ENGLISH, ">=%d", version))));
			} catch (VersionParsingException e) {
				throw new RuntimeException(e);
			}
		}

		return Collections.singletonList(new BuiltinMod(gameJars, metadata.build()));
	}

	public Path getGameJar() {
		return gameJars.get(0);
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
	public Set<BuiltinTransform> getBuiltinTransforms(String className) {
		final boolean isZomboidClass = className.startsWith("zombie.");
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

	private static final Set<BuiltinTransform> TRANSFORM_WIDENALL_STRIPENV_CLASSTWEAKS = EnumSet.of(BuiltinTransform.WIDEN_ALL_PACKAGE_ACCESS, BuiltinTransform.STRIP_ENVIRONMENT, BuiltinTransform.CLASS_TWEAKS);
	private static final Set<BuiltinTransform> TRANSFORM_WIDENALL_CLASSTWEAKS = EnumSet.of(BuiltinTransform.WIDEN_ALL_PACKAGE_ACCESS, BuiltinTransform.CLASS_TWEAKS);
	private static final Set<BuiltinTransform> TRANSFORM_STRIPENV = EnumSet.of(BuiltinTransform.STRIP_ENVIRONMENT);

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
			LibClassifier<ZomboidLibrary> classifier = new LibClassifier<>(ZomboidLibrary.class, envType, this);
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

			if (classifier.has(ZomboidLibrary.MC_BUNDLER)) {
				BundlerProcessor.process(classifier);
			}

			envGameJar = classifier.getOrigin(envGameLib);
			if (envGameJar == null) return false;

            commonGameJar = classifier.getOrigin(ZomboidLibrary.ZOMBOID_COMMON);

			if (commonGameJarDeclared && commonGameJar == null) {
				Log.warn(LogCategory.GAME_PROVIDER, "The declared common game jar didn't contain any of the expected classes!");
			}

			gameJars.add(envGameJar);

			if (commonGameJar != null && !commonGameJar.equals(envGameJar)) {
				gameJars.add(commonGameJar);
			}

			Path assetsJar = classifier.getOrigin(ZomboidLibrary.MC_ASSETS_ROOT);

			if (assetsJar != null && !assetsJar.equals(commonGameJar) && !assetsJar.equals(envGameJar)) {
				gameJars.add(assetsJar);
			}

			entrypoint = classifier.getClassName(envGameLib);
			hasModLoader = classifier.has(ZomboidLibrary.MODLOADER);
			log4jAvailable = classifier.has(ZomboidLibrary.LOG4J_API) && classifier.has(ZomboidLibrary.LOG4J_CORE);
			slf4jAvailable = classifier.has(ZomboidLibrary.SLF4J_API) && classifier.has(ZomboidLibrary.SLF4J_CORE);
			boolean hasLogLib = log4jAvailable || slf4jAvailable;
            Log.configureBuiltin(true, false);

			for (ZomboidLibrary lib : ZomboidLibrary.LOGGING) {
				Path path = classifier.getOrigin(lib);

				if (path != null) {
					if (hasLogLib) {
						logJars.add(path);
					} else if (!gameJars.contains(path)) {
						miscGameLibraries.add(path);
					}
				}
			}

			miscGameLibraries.addAll(classifier.getUnmatchedOrigins());
			validParentClassPath = classifier.getSystemLibraries();
		} catch (IOException e) {
			throw ExceptionUtil.wrap(e);
		}

		// expose obfuscated jar locations for mods to more easily remap code from obfuscated to intermediary
		ObjectShare share = LeafLoaderImpl.INSTANCE.getObjectShare();
		share.put("leaf-loader:inputGameJar", gameJars.get(0)); // deprecated
		// need to make copy as gameJars is later mutated to hold the remapped jars
		share.put("leaf-loader:inputGameJars", Collections.unmodifiableList(new ArrayList<>(gameJars)));

		String version = arguments.remove(Arguments.GAME_VERSION);
		if (version == null) version = System.getProperty(SystemProperties.GAME_VERSION);
		versionData = ZomboidVersionLookup.getVersion(gameJars, entrypoint, version);

		processArgumentMap(arguments, envType);

		return true;
	}

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

	@Override
	public void initialize(LeafLauncher launcher) {
		launcher.setValidParentClassPath(validParentClassPath);

		MappingConfiguration config = launcher.getMappingConfiguration();
		String runtimeNs = config.getRuntimeNamespace();
		String gameNs = System.getProperty(SystemProperties.GAME_MAPPING_NAMESPACE);

		if (gameNs == null) {
			gameNs = MappingConfiguration.OFFICIAL_NAMESPACE; // default

			if (config.hasAnyMappings()) {
				List<String> mappingNamespaces = config.getNamespaces();

				if (mappingNamespaces != null) {
					if (launcher.isDevelopment()
							&& mappingNamespaces.contains(MappingConfiguration.NAMED_NAMESPACE)) { // dev with named (e.g. yarn)
						gameNs = MappingConfiguration.NAMED_NAMESPACE;
					} else if (!mappingNamespaces.contains(MappingConfiguration.OFFICIAL_NAMESPACE)) { // prod with old mc that didn't use the same mappings for client and server jars
						gameNs = envType == EnvType.CLIENT ? MappingConfiguration.CLIENT_OFFICIAL_NAMESPACE : MappingConfiguration.SERVER_OFFICIAL_NAMESPACE;
					}
				}
			}
		}

		Log.debug(LogCategory.GAME_PROVIDER, "namespace detection result: game=%s runtime=%s mod-default=%s", gameNs, runtimeNs, config.getDefaultModDistributionNamespace());

		if (!gameNs.equals(runtimeNs)) { // game is obfuscated / in another namespace -> remap
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

			obfJars = GameProviderHelper.deobfuscate(obfJars,
					gameNs,
					getGameId(), getNormalizedGameVersion(),
					getLaunchDirectory(),
					launcher);

			for (int i = 0; i < gameJars.size(); i++) {
				Path newJar = obfJars.get(names[i]);
				Path oldJar = gameJars.set(i, newJar);

				if (logJars.remove(oldJar)) logJars.add(newJar);
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

		setupLogHandler(launcher, true);

		transformer.locateEntrypoints(launcher, gameJars);
	}

	/**
	 * Sets up the logging for the loader, and the rest of the game if slf4j/log4j is present.
	 *
	 * @param launcher The LeafLauncher object instance
	 * @param useTargetCl Use the launcher's target class loader
	 */
	public void setupLogHandler(LeafLauncher launcher, boolean useTargetCl) {
		System.setProperty("log4j2.formatMsgNoLookups", "true");

		String logHandlerClsName;

		if (getSemverGameVersion().getVersionComponent(0) <= 41) {
			logHandlerClsName = "dev.aoqia.leaf.loader.impl.game.zomboid.OldZomboidLogHandler";
		} else {
			logHandlerClsName = "dev.aoqia.leaf.loader.impl.game.zomboid.NewZomboidLogHandler";
		}

		if (!SystemProperties.isSet(SystemProperties.DISABLE_LOG4J)) {
			if (log4jAvailable) {
				logHandlerClsName = "dev.aoqia.leaf.loader.impl.game.zomboid.Log4jLogHandler";
			} else if (slf4jAvailable) {
				logHandlerClsName = "dev.aoqia.leaf.loader.impl.game.zomboid.Slf4jLogHandler";
			}
		}

		try {
			ClassLoader prevCl = Thread.currentThread().getContextClassLoader();
			Class<?> logHandlerCls;

			if (useTargetCl) {
				Thread.currentThread().setContextClassLoader(launcher.getTargetClassLoader());
				logHandlerCls = launcher.loadIntoTarget(logHandlerClsName);
			} else {
				logHandlerCls = Class.forName(logHandlerClsName);
			}

			if (logHandlerCls != null) {
				Log.init((LogHandler) logHandlerCls.getConstructor().newInstance());
			}

			Thread.currentThread().setContextClassLoader(prevCl);
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public Arguments getArguments() {
		return arguments;
	}

	@Override
	public String[] getLaunchArguments(boolean sanitize) {
		if (arguments == null) return new String[0];

		String[] ret = arguments.toArray();
		if (!sanitize) return ret;

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

		if (writeIdx < ret.length) ret = Arrays.copyOf(ret, writeIdx);

		return ret;
	}

	@Override
	public GameTransformer getEntrypointTransformer() {
		return transformer;
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
        // TODO(leaf): Is this true also for Zomboid?
		return !LoaderUtil.hasMacOs();
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
			invoker = MethodHandles.lookup().findStatic(c, "main", MethodType.methodType(void.class, String[].class));
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
}
