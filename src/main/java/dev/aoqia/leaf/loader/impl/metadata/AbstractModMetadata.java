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

import dev.aoqia.leaf.loader.api.metadata.CustomValue;
import dev.aoqia.leaf.loader.api.metadata.ModMetadata;

public abstract class AbstractModMetadata implements ModMetadata {
	public static final String TYPE_BUILTIN = "builtin";
	public static final String TYPE_FABRIC_MOD = "fabric";

	@Override
	public boolean containsCustomElement(String key) {
		return containsCustomValue(key);
	}

	@Override
	public boolean containsCustomValue(String key) {
		return getCustomValues().containsKey(key);
	}

	@Override
	public CustomValue getCustomValue(String key) {
		return getCustomValues().get(key);
	}
}
