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
package dev.aoqia.leaf.loader;

import java.io.File;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

import dev.aoqia.leaf.loader.impl.LeafLoaderImpl;

/**
 * The main class for mod loading operations.
 *
 * @deprecated Use {@link dev.aoqia.leaf.loader.api.LeafLoader}
 */
@Deprecated
public abstract class LeafLoader implements dev.aoqia.leaf.loader.api.LeafLoader {
	/**
	 * @deprecated Use {@link dev.aoqia.leaf.loader.api.LeafLoader#getInstance()} where possible,
	 * report missing areas as an issue.
	 */
	@Deprecated
	public static final LeafLoader INSTANCE = LeafLoaderImpl.InitHelper.get();

	public File getModsDirectory() {
		return getModsDirectory0().toFile();
	}

	@Override
	public abstract <T> List<T> getEntrypoints(String key, Class<T> type);

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public Collection<ModContainer> getModContainers() {
		return (Collection) getAllMods();
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public List<ModContainer> getMods() {
		return (List) getAllMods();
	}

	protected abstract Path getModsDirectory0();
}
