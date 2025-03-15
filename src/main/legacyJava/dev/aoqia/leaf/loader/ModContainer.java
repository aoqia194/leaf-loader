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

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.util.List;

import dev.aoqia.leaf.loader.impl.util.UrlUtil;
import dev.aoqia.leaf.loader.metadata.LoaderModMetadata;

/**
 * @deprecated Use {@link dev.aoqia.leaf.loader.api.ModContainer} instead
 */
@Deprecated
public abstract class ModContainer implements dev.aoqia.leaf.loader.api.ModContainer {
	public abstract LoaderModMetadata getInfo();
	protected abstract List<Path> getCodeSourcePaths();

	public URL getOriginUrl() {
		try {
			return UrlUtil.asUrl(getCodeSourcePaths().get(0));
		} catch (MalformedURLException e) {
			throw new RuntimeException(e);
		}
	}
}
