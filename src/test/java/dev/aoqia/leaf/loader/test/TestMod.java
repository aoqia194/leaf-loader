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
package dev.aoqia.leaf.loader.test;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

import dev.aoqia.leaf.api.ModInitializer;
import dev.aoqia.leaf.loader.api.LeafLoader;
import dev.aoqia.leaf.loader.api.entrypoint.PreLaunchEntrypoint;
import dev.aoqia.leaf.loader.impl.launch.LeafLauncherBase;
import dev.aoqia.leaf.loader.impl.util.log.Log;
import dev.aoqia.leaf.loader.impl.util.log.LogCategory;

public class TestMod implements PreLaunchEntrypoint, ModInitializer {
	/**
	 * Entrypoint implementation for preLaunch.
	 *
	 * <p>Warning: This should normally be in a separate class from later entrypoints to avoid accidentally loading
	 * and/or initializing game classes. This is just trivial test code not meant for production use.
	 */
	@Override
	public void onPreLaunch() {
		if (TestMod.class.getClassLoader() != LeafLauncherBase.getLauncher().getTargetClassLoader()) {
			throw new IllegalStateException("invalid class loader: "+TestMod.class.getClassLoader());
		}

		Log.info(LogCategory.TEST, "In preLaunch (cl %s)", TestMod.class.getClassLoader());
	}

	@Override
	public void onInitialize() {
		if (TestMod.class.getClassLoader() != LeafLauncherBase.getLauncher().getTargetClassLoader()) {
			throw new IllegalStateException("invalid class loader: "+TestMod.class.getClassLoader());
		}

		Log.info(LogCategory.TEST, "**************************");
		Log.info(LogCategory.TEST, "Hello from Fabric");
		Log.info(LogCategory.TEST, "**************************");

		Set<CustomEntry> testingInits = new LinkedHashSet<>(
            LeafLoader.getInstance().getEntrypoints("test:testing", CustomEntry.class));
		Log.info(LogCategory.TEST, "Found %d testing inits", testingInits.size());
		Log.info(LogCategory.TEST, testingInits.stream().map(CustomEntry::describe).collect(Collectors.joining(", ")));
	}
}
