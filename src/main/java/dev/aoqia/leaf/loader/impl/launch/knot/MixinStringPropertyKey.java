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

import java.util.Objects;

import org.spongepowered.asm.service.IPropertyKey;

public class MixinStringPropertyKey implements IPropertyKey {
	public final String key;

	public MixinStringPropertyKey(String key) {
		this.key = key;
	}

	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof MixinStringPropertyKey)) {
			return false;
		} else {
			return Objects.equals(this.key, ((MixinStringPropertyKey) obj).key);
		}
	}

	@Override
	public int hashCode() {
		return this.key.hashCode();
	}

	@Override
	public String toString() {
		return this.key;
	}
}
