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

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

@FunctionalInterface
interface ModCandidateFinder {
	void findCandidates(ModCandidateConsumer out);

	interface ModCandidateConsumer {
		default void accept(Path path, boolean requiresRemap) {
			accept(Collections.singletonList(path), requiresRemap);
		}

		void accept(List<Path> paths, boolean requiresRemap);
	}
}
