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

import dev.aoqia.leaf.loader.api.LanguageAdapter;
import dev.aoqia.leaf.loader.api.LanguageAdapterException;
import dev.aoqia.leaf.loader.api.ModContainer;

/**
 * @deprecated Use {@link LanguageAdapter} instead.
 */
@Deprecated
public final class DefaultLanguageAdapter implements LanguageAdapter {
	public static final DefaultLanguageAdapter INSTANCE = new DefaultLanguageAdapter();

	@Override
	public <T> T create(ModContainer mod, String value, Class<T> type) throws LanguageAdapterException {
		return LanguageAdapter.getDefault().create(mod, value, type);
	}
}
