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
package dev.aoqia.leaf.loader.impl.metadata;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import dev.aoqia.leaf.api.EnvType;
import dev.aoqia.leaf.loader.api.Version;
import dev.aoqia.leaf.loader.api.metadata.ModDependency;

/**
 * Internal variant of the ModMetadata interface.
 */
@SuppressWarnings("deprecation")
public interface LoaderModMetadata extends dev.aoqia.leaf.loader.metadata.LoaderModMetadata {
	int getSchemaVersion();

	default String getOldStyleLanguageAdapter() {
		return "dev.aoqia.leaf.loader.language.JavaLanguageAdapter";
	}

	Map<String, String> getLanguageAdapterDefinitions();
	Collection<NestedJarEntry> getJars();
	Collection<String> getMixinConfigs(EnvType type);
	/* @Nullable */
	String getAccessWidener();
	@Override
	boolean loadsInEnvironment(EnvType type);

	Collection<String> getOldInitializers();
	@Override
	List<EntrypointMetadata> getEntrypoints(String type);
	@Override
	Collection<String> getEntrypointKeys();

	void emitFormatWarnings();

	void setVersion(Version version);
	void setDependencies(Collection<ModDependency> dependencies);
}
