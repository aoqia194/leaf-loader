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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import dev.aoqia.leaf.loader.impl.util.Localization;

public class LocalizationTests {
	@Test
	public void formatRoot() {
		Assertions.assertEquals("client", Localization.formatRoot("environment.client"));
		Assertions.assertEquals("Install A, B.", Localization.formatRoot("resolution.solution.addMod", "A", "B"));
	}
}
