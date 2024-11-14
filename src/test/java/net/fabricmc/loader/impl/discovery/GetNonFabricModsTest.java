/*
 * Copyright 2016 FabricMC
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

package net.fabricmc.loader.impl.discovery;

import net.aoqia.api.EnvType;
import net.aoqia.loader.impl.LeafLoaderImpl;
import net.aoqia.loader.impl.discovery.ModCandidateFinder;
import net.aoqia.loader.impl.discovery.ModCandidateImpl;
import net.aoqia.loader.impl.discovery.ModDiscoverer;
import net.aoqia.loader.impl.discovery.ModResolutionException;
import net.aoqia.loader.impl.game.GameProvider;
import net.aoqia.loader.impl.launch.FabricLauncher;
import net.aoqia.loader.impl.launch.LeafLauncherBase;
import net.aoqia.loader.impl.util.LoaderUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GetNonFabricModsTest {
    private LeafLoaderImpl loader;
    private ModDiscoverer discoverer;
    private MockedConstruction<LeafLoaderImpl> loaderConstruction;

    /*
     * Set up the mock loader and discoverer
     */
    @BeforeEach
    public void setUp() {
        GameProvider provider = mock();
        when(provider.getBuiltinMods()).thenReturn(Collections.emptyList());

        FabricLauncher launcher = mock();
        when(launcher.getEnvironmentType()).thenReturn(EnvType.CLIENT);
        when(launcher.isDevelopment()).thenReturn(false);
        LeafLauncherBase.setLauncher(launcher);
        loader = mock();
        when(loader.getGameProvider()).thenReturn(provider);
        when(loader.isDevelopmentEnvironment()).thenReturn(false);

        loaderConstruction = Mockito.mockConstructionWithAnswer(LeafLoaderImpl.class, invocation -> loader);

        discoverer = new ModDiscoverer(mock(), mock());
        discoverer.addCandidateFinder(new MockCandidateFinder());
    }

    @AfterEach
    public void tearDown() {
        loaderConstruction.close();
    }

    /*
     * Test that the discoverer can find non-fabric mods
     */
    @Test
    public void testGetNonFabricMods() throws ModResolutionException {
        List<ModCandidateImpl> acceptedMods = discoverer.discoverMods(loader,
                new HashMap<String, Set<ModCandidateImpl>>());

        boolean foundDummyFabricMod = false;

        for (ModCandidateImpl acceptedMod : acceptedMods) {
            if (acceptedMod.getId().equals("dummy")) {
                foundDummyFabricMod = true;
                break;
            }
        }

        Assertions.assertTrue(foundDummyFabricMod);

        List<Path> nonFabricMods = discoverer.getNonFabricMods();
        Assertions.assertEquals(1, nonFabricMods.size());
        Assertions.assertEquals(Paths.get("src/test/resources/testing/discovery/dummyNonFabricMod.jar")
                        .toAbsolutePath(),
                nonFabricMods.get(0));
    }

    /*
     * Mock candidate finder that returns two dummy mods (one fabric and one
     * non-fabric)
     */
    public static class MockCandidateFinder implements ModCandidateFinder {
        @Override
        public void findCandidates(ModCandidateConsumer out) {
            out.accept(
                    LoaderUtil.normalizePath(Paths.get("src/test/resources/testing/discovery/dummyFabricMod.jar")),
                    false);
            out.accept(
                    LoaderUtil.normalizePath(Paths.get("src/test/resources/testing/discovery/dummyNonFabricMod.jar")),
                    false);
        }
    }
}
