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
package dev.aoqia.leaf.loader.impl.game.zomboid;

import dev.aoqia.leaf.api.EnvType;
import dev.aoqia.leaf.loader.impl.game.LibClassifier.LibraryType;

enum ZomboidLibrary implements LibraryType {
	ZOMBOID_CLIENT(EnvType.CLIENT, "zombie/gameStates/MainScreenState.class"),
	ZOMBOID_SERVER(EnvType.SERVER, "zombie/network/GameServer.class"),
	ZOMBOID_COMMON("zombie/gameStates/MainScreenState.class"),
	MODLOADER("ModLoader");

	static final ZomboidLibrary[] GAME = { ZOMBOID_CLIENT, ZOMBOID_SERVER };

	private final EnvType env;
	private final String[] paths;

	ZomboidLibrary(String path) {
		this(null, new String[] { path });
	}

	ZomboidLibrary(String... paths) {
		this(null, paths);
	}

	ZomboidLibrary(EnvType env, String... paths) {
		this.paths = paths;
		this.env = env;
	}

	@Override
	public boolean isApplicable(EnvType env) {
		return this.env == null || this.env == env;
	}

	@Override
	public String[] getPaths() {
		return paths;
	}
}
