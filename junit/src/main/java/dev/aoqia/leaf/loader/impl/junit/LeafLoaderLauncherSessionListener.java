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
package dev.aoqia.leaf.loader.impl.junit;

import java.util.Locale;

import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.LauncherSessionListener;

import dev.aoqia.leaf.api.EnvType;
import dev.aoqia.leaf.loader.impl.launch.knot.Knot;
import dev.aoqia.leaf.loader.impl.util.SystemProperties;

public class LeafLoaderLauncherSessionListener implements LauncherSessionListener {
	static {
		System.setProperty(SystemProperties.DEVELOPMENT, "true");
		System.setProperty(SystemProperties.UNIT_TEST, "true");
	}
    
    private final Knot knot;
	private final ClassLoader classLoader;

	private ClassLoader launcherSessionClassLoader;

	public LeafLoaderLauncherSessionListener() {
		final Thread currentThread = Thread.currentThread();
		final ClassLoader originalClassLoader = currentThread.getContextClassLoader();

		// parse the test environment type, defaults to client
		final EnvType envType = EnvType.valueOf(System.getProperty(SystemProperties.SIDE, EnvType.CLIENT.name()).toUpperCase(Locale.ROOT));

		try {
			knot = new Knot(envType);
			classLoader = knot.init(new String[]{});
		} finally {
			// Knot.init sets the context class loader, revert it back for now.
			currentThread.setContextClassLoader(originalClassLoader);
		}
	}

	@Override
	public void launcherSessionOpened(LauncherSession session) {
		final Thread currentThread = Thread.currentThread();
		launcherSessionClassLoader = currentThread.getContextClassLoader();
		currentThread.setContextClassLoader(classLoader);
	}

	@Override
	public void launcherSessionClosed(LauncherSession session) {
		final Thread currentThread = Thread.currentThread();
		currentThread.setContextClassLoader(launcherSessionClassLoader);
	}
}
