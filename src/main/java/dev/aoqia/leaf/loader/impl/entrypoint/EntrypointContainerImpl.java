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
package dev.aoqia.leaf.loader.impl.entrypoint;

import dev.aoqia.leaf.loader.api.EntrypointException;
import dev.aoqia.leaf.loader.api.ModContainer;
import dev.aoqia.leaf.loader.api.entrypoint.EntrypointContainer;

public final class EntrypointContainerImpl<T> implements EntrypointContainer<T> {
	private final String key;
	private final Class<T> type;
	private final EntrypointStorage.Entry entry;
	private T instance;

	/**
	 * Create EntrypointContainer with lazy init.
	 */
	public EntrypointContainerImpl(String key, Class<T> type, EntrypointStorage.Entry entry) {
		this.key = key;
		this.type = type;
		this.entry = entry;
	}

	/**
	 * Create EntrypointContainer without lazy init.
	 */
	public EntrypointContainerImpl(EntrypointStorage.Entry entry, T instance) {
		this.key = null;
		this.type = null;
		this.entry = entry;
		this.instance = instance;
	}

	@SuppressWarnings("deprecation")
	@Override
	public synchronized T getEntrypoint() {
		if (instance == null) {
			try {
				instance = entry.getOrCreate(type);
				assert instance != null;
			} catch (Exception ex) {
				throw new EntrypointException(key, getProvider().getMetadata().getId(), ex);
			}
		}

		return instance;
	}

	@Override
	public ModContainer getProvider() {
		return entry.getModContainer();
	}

	@Override
	public String getDefinition() {
		return entry.getDefinition();
	}
}
