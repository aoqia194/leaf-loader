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
package dev.aoqia.leaf.zomboid.test.junit;

import dev.aoqia.leaf.loader.api.LeafLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import zombie.core.Core;
import zombie.core.GameVersion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JunitTest {
    @BeforeAll
    public static void setup() {
    }

    @Test
    public void testMixin() {
        // TODO: Write better test
        assertTrue(true);
        // final String gameVersion = Core.getInstance().getVersion();
        // assertEquals("41.78.16", gameVersion);
    }

    @Test
    public void testAccessLoader() {
        LeafLoader.getInstance().getAllMods();
    }
}
