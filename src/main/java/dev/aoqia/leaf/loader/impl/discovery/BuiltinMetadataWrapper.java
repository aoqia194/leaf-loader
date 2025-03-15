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
package dev.aoqia.leaf.loader.impl.discovery;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import dev.aoqia.leaf.api.EnvType;
import dev.aoqia.leaf.loader.api.Version;
import dev.aoqia.leaf.loader.api.metadata.ContactInformation;
import dev.aoqia.leaf.loader.api.metadata.CustomValue;
import dev.aoqia.leaf.loader.api.metadata.ModDependency;
import dev.aoqia.leaf.loader.api.metadata.ModEnvironment;
import dev.aoqia.leaf.loader.api.metadata.ModMetadata;
import dev.aoqia.leaf.loader.api.metadata.Person;
import dev.aoqia.leaf.loader.impl.metadata.AbstractModMetadata;
import dev.aoqia.leaf.loader.impl.metadata.EntrypointMetadata;
import dev.aoqia.leaf.loader.impl.metadata.LoaderModMetadata;
import dev.aoqia.leaf.loader.impl.metadata.NestedJarEntry;

class BuiltinMetadataWrapper extends AbstractModMetadata implements LoaderModMetadata {
	private final ModMetadata parent;
	private Version version;
	private Collection<ModDependency> dependencies;

	BuiltinMetadataWrapper(ModMetadata parent) {
		this.parent = parent;

		version = parent.getVersion();
		dependencies = parent.getDependencies();
	}

	@Override
	public String getType() {
		return parent.getType();
	}

	@Override
	public String getId() {
		return parent.getId();
	}

	@Override
	public Collection<String> getProvides() {
		return parent.getProvides();
	}

	@Override
	public Version getVersion() {
		return version;
	}

	@Override
	public void setVersion(Version version) {
		this.version = version;
	}

	@Override
	public ModEnvironment getEnvironment() {
		return parent.getEnvironment();
	}

	@Override
	public Collection<ModDependency> getDependencies() {
		return dependencies;
	}

	@Override
	public void setDependencies(Collection<ModDependency> dependencies) {
		this.dependencies = Collections.unmodifiableCollection(dependencies);
	}

	@Override
	public String getName() {
		return parent.getName();
	}

	@Override
	public String getDescription() {
		return parent.getDescription();
	}

	@Override
	public Collection<Person> getAuthors() {
		return parent.getAuthors();
	}

	@Override
	public Collection<Person> getContributors() {
		return parent.getContributors();
	}

	@Override
	public ContactInformation getContact() {
		return parent.getContact();
	}

	@Override
	public Collection<String> getLicense() {
		return parent.getLicense();
	}

	@Override
	public Optional<String> getIconPath(int size) {
		return parent.getIconPath(size);
	}

	@Override
	public boolean containsCustomValue(String key) {
		return parent.containsCustomValue(key);
	}

	@Override
	public CustomValue getCustomValue(String key) {
		return parent.getCustomValue(key);
	}

	@Override
	public Map<String, CustomValue> getCustomValues() {
		return parent.getCustomValues();
	}

	@Override
	public int getSchemaVersion() {
		return Integer.MAX_VALUE;
	}

	@Override
	public Map<String, String> getLanguageAdapterDefinitions() {
		return Collections.emptyMap();
	}

	@Override
	public Collection<NestedJarEntry> getJars() {
		return Collections.emptyList();
	}

	@Override
	public Collection<String> getMixinConfigs(EnvType type) {
		return Collections.emptyList();
	}

	@Override
	public String getAccessWidener() {
		return null;
	}

	@Override
	public boolean loadsInEnvironment(EnvType type) {
		return true;
	}

	@Override
	public Collection<String> getOldInitializers() {
		return Collections.emptyList();
	}

	@Override
	public List<EntrypointMetadata> getEntrypoints(String type) {
		return Collections.emptyList();
	}

	@Override
	public Collection<String> getEntrypointKeys() {
		return Collections.emptyList();
	}

	@Override
	public void emitFormatWarnings() { }
}
