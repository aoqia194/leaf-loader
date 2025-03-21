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
package dev.aoqia.leaf.loader.impl.game;

import java.net.URL;
import java.nio.file.Path;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.util.CheckClassAdapter;
import org.sat4j.pb.SolverFactory;
import org.sat4j.specs.ContradictionException;

import net.fabricmc.accesswidener.AccessWidener;
import dev.aoqia.leaf.api.EnvType;
import dev.aoqia.leaf.loader.impl.util.UrlConversionException;
import dev.aoqia.leaf.loader.impl.util.UrlUtil;
import net.fabricmc.mappingio.tree.MappingTree;
import net.fabricmc.tinyremapper.TinyRemapper;
import org.spongepowered.asm.launch.MixinBootstrap;

enum LoaderLibrary {
	LEAF_LOADER(UrlUtil.LOADER_CODE_SOURCE),
	MAPPING_IO(MappingTree.class),
	SPONGE_MIXIN(MixinBootstrap.class),
	TINY_REMAPPER(TinyRemapper.class),
	ACCESS_WIDENER(AccessWidener.class),
	ASM(ClassReader.class),
	ASM_ANALYSIS(Analyzer.class),
	ASM_COMMONS(Remapper.class),
	ASM_TREE(ClassNode.class),
	ASM_UTIL(CheckClassAdapter.class),
	SAT4J_CORE(ContradictionException.class),
	SAT4J_PB(SolverFactory.class),
	SERVER_LAUNCH("leaf-server-launch.properties", EnvType.SERVER), // installer generated jar to run setup loader's class path
	SERVER_LAUNCHER("dev/aoqia/leaf/installer/ServerLauncher.class", EnvType.SERVER), // installer based launch-through method
	JUNIT_API("org/junit/jupiter/api/Test.class", null),
	JUNIT_PLATFORM_ENGINE("org/junit/platform/engine/TestEngine.class", null),
	JUNIT_PLATFORM_LAUNCHER("org/junit/platform/launcher/core/LauncherFactory.class", null),
	JUNIT_JUPITER("org/junit/jupiter/engine/JupiterTestEngine.class", null),
	LEAF_LOADER_JUNIT("dev/aoqia/leaf/loader/impl/junit/LeafLoaderLauncherSessionListener.class", null),

	// Logging libraries are only loaded from the platform CL when running as a unit test.
	LOG4J_API("org/apache/logging/log4j/LogManager.class", true),
	LOG4J_CORE("META-INF/services/org.apache.logging.log4j.spi.Provider", true),
	LOG4J_CONFIG("log4j2.xml", true),
	LOG4J_PLUGIN_3("net/minecrell/terminalconsole/util/LoggerNamePatternSelector.class", true),
	SLF4J_API("org/slf4j/Logger.class", true);

	final Path path;
	final EnvType env;
	final boolean junitRunOnly;

	LoaderLibrary(Class<?> cls) {
		this(UrlUtil.getCodeSource(cls));
	}

	LoaderLibrary(Path path) {
		if (path == null) throw new RuntimeException("missing loader library "+name());

		this.path = path;
		this.env = null;
		this.junitRunOnly = false;
	}

	LoaderLibrary(String file, EnvType env) {
		this(file, env, false);
	}

	LoaderLibrary(String file, EnvType env, boolean junitRunOnly) {
		URL url = LoaderLibrary.class.getClassLoader().getResource(file);

		try {
			this.path = url != null ? UrlUtil.getCodeSource(url, file) : null;
			this.env = env;
		} catch (UrlConversionException e) {
			throw new RuntimeException(e);
		}

		this.junitRunOnly = junitRunOnly;
	}

	LoaderLibrary(String path, boolean loggerLibrary) {
		this(path, null, loggerLibrary);
	}

	boolean isApplicable(EnvType env, boolean junitRun) {
		return (this.env == null || this.env == env)
				&& (!junitRunOnly || junitRun);
	}
}
