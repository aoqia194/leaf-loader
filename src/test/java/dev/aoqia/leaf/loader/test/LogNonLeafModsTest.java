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

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import dev.aoqia.leaf.loader.impl.LeafLoaderImpl;
import dev.aoqia.leaf.loader.impl.util.log.Log;
import dev.aoqia.leaf.loader.impl.util.log.LogHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;

public class LogNonLeafModsTest {
    private LogHandler logHandler;

    /*
     * Setup log handler before each test.
     */
    @BeforeEach
    public void setUp() {
        logHandler = mock();
        Mockito.when(logHandler.shouldLog(Mockito.any(), Mockito.any())).thenReturn(true);
        Mockito.doNothing()
            .when(logHandler)
            .log(Mockito.anyLong(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(),
                Mockito.anyBoolean(), Mockito.anyBoolean());
        Log.init(logHandler);
    }

    /*
     * Test that the log handler is called with the correct message when there are
     * non-fabric mods found.
     */
    @Test
    public void testLogNonLeafMods() {
        List<Path> nonLeafMods = new ArrayList<>();
        nonLeafMods.add(Paths.get("mods/non_leaf_mod1.jar"));
        nonLeafMods.add(Paths.get("mods/non_leaf_mod2.jar"));
        nonLeafMods.add(Paths.get("mods/non_leaf_mod3.jar"));

        LeafLoaderImpl.INSTANCE.dumpNonLeafMods(nonLeafMods);

        String expectedLog = "Found 3 non-leaf mods:"
                             + "\n\t- non_leaf_mod1.jar"
                             + "\n\t- non_leaf_mod2.jar"
                             + "\n\t- non_leaf_mod3.jar";

        Mockito.verify(logHandler, Mockito.times(1))
            .log(Mockito.anyLong(), Mockito.any(), Mockito.any(), eq(expectedLog), Mockito.any(),
                Mockito.anyBoolean(), Mockito.anyBoolean());
    }
}
