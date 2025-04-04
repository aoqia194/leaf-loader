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
package dev.aoqia.leaf.loader.util;

import java.net.URL;
import java.nio.file.Path;

import dev.aoqia.leaf.loader.impl.util.ExceptionUtil.WrappedException;

/**
 * @deprecated Internal API, do not use
 */
@Deprecated
public final class UrlUtil {
	private UrlUtil() { }

	public static Path asPath(URL url) throws UrlConversionException {
		try {
			return dev.aoqia.leaf.loader.impl.util.UrlUtil.asPath(url);
		} catch (WrappedException e) {
			throw new UrlConversionException(e.getCause());
		}
	}
}
