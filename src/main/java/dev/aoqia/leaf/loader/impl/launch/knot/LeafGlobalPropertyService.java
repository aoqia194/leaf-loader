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
package dev.aoqia.leaf.loader.impl.launch.knot;

import org.spongepowered.asm.service.IGlobalPropertyService;
import org.spongepowered.asm.service.IPropertyKey;

import dev.aoqia.leaf.loader.impl.launch.LeafLauncherBase;

public class LeafGlobalPropertyService implements IGlobalPropertyService {
	@Override
	public IPropertyKey resolveKey(String name) {
		return new MixinStringPropertyKey(name);
	}

	private String keyString(IPropertyKey key) {
		return ((MixinStringPropertyKey) key).key;
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> T getProperty(IPropertyKey key) {
		return (T) LeafLauncherBase.getProperties().get(keyString(key));
	}

	@Override
	public void setProperty(IPropertyKey key, Object value) {
		LeafLauncherBase.getProperties().put(keyString(key), value);
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> T getProperty(IPropertyKey key, T defaultValue) {
		return (T) LeafLauncherBase.getProperties().getOrDefault(keyString(key), defaultValue);
	}

	@Override
	public String getPropertyString(IPropertyKey key, String defaultValue) {
		Object o = LeafLauncherBase.getProperties().get(keyString(key));
		return o != null ? o.toString() : defaultValue;
	}
}
