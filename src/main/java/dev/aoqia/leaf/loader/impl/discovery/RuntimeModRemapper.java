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

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.stream.Collectors;

import dev.aoqia.leaf.loader.impl.FormattedException;
import dev.aoqia.leaf.loader.impl.LeafLoaderImpl;
import dev.aoqia.leaf.loader.impl.launch.LeafLauncher;
import dev.aoqia.leaf.loader.impl.launch.LeafLauncherBase;
import dev.aoqia.leaf.loader.impl.launch.MappingConfiguration;
import dev.aoqia.leaf.loader.impl.util.FileSystemUtil;
import dev.aoqia.leaf.loader.impl.util.ManifestUtil;
import dev.aoqia.leaf.loader.impl.util.SystemProperties;
import dev.aoqia.leaf.loader.impl.util.log.Log;
import dev.aoqia.leaf.loader.impl.util.log.LogCategory;
import dev.aoqia.leaf.loader.impl.util.log.TinyRemapperLoggerAdapter;

import net.fabricmc.accesswidener.*;
import net.fabricmc.tinyremapper.*;
import net.fabricmc.tinyremapper.extension.mixin.MixinExtension;
import org.objectweb.asm.commons.Remapper;

public final class RuntimeModRemapper {
    private static final String REMAP_TYPE_MANIFEST_KEY = "Leaf-Loom-Mixin-Remap-Type";
    private static final String REMAP_TYPE_STATIC = "static";

    public static void remap(Collection<ModCandidateImpl> modCandidates, Path tmpDir,
        Path outputDir) {
        List<ModCandidateImpl> modsToRemap = new ArrayList<>();
        Set<InputTag> remapMixins = new HashSet<>();

        for (ModCandidateImpl mod : modCandidates) {
            if (mod.getRequiresRemap()) {
                modsToRemap.add(mod);
            }
        }

        if (modsToRemap.isEmpty()) {
            return;
        }

        MappingConfiguration config = LeafLauncherBase.getLauncher().getMappingConfiguration();
        String modNs = MappingConfiguration.OFFICIAL_NAMESPACE;
        String runtimeNs = config.getRuntimeNamespace();
        if (modNs.equals(runtimeNs)) {
            return;
        }

        Map<ModCandidateImpl, RemapInfo> infoMap = new HashMap<>();
        TinyRemapper remapper = null;

        try {
            LeafLauncher launcher = LeafLauncherBase.getLauncher();

            AccessWidener mergedAccessWidener = new AccessWidener();
            mergedAccessWidener.visitHeader(modNs);

            for (ModCandidateImpl mod : modsToRemap) {
                RemapInfo info = new RemapInfo();
                infoMap.put(mod, info);

                if (mod.hasPath()) {
                    List<Path> paths = mod.getPaths();
                    if (paths.size() != 1) {
                        throw new UnsupportedOperationException("multiple path for " + mod);
                    }

                    info.inputPath = paths.get(0);
                } else {
                    info.inputPath = mod.copyToDir(tmpDir, true);
                    info.inputIsTemp = true;
                }

                info.outputPath = outputDir.resolve(mod.getDefaultFileName());
                Files.deleteIfExists(info.outputPath);

                String accessWidener = mod.getMetadata().getAccessWidener();

                if (accessWidener != null) {
                    info.accessWidenerPath = accessWidener;

                    try (FileSystemUtil.FileSystemDelegate jarFs = FileSystemUtil.getJarFileSystem(
                        info.inputPath, false)) {
                        FileSystem fs = jarFs.get();
                        info.accessWidener = Files.readAllBytes(fs.getPath(accessWidener));
                    } catch (Throwable t) {
                        throw new RuntimeException(
                            "Error reading access widener for mod '" + mod.getId() + "'!", t);
                    }

                    new AccessWidenerReader(mergedAccessWidener).read(info.accessWidener);
                }
            }

            remapper = TinyRemapper.newRemapper(
                    new TinyRemapperLoggerAdapter(LogCategory.MOD_REMAP))
                .withMappings(TinyUtils.createMappingProvider(
                    launcher.getMappingConfiguration().getMappings(), modNs, runtimeNs))
                .renameInvalidLocals(false)
                .extension(new MixinExtension(remapMixins::contains))
                .extraAnalyzeVisitor((mrjVersion, className, next) ->
                    AccessWidenerClassVisitor.createClassVisitor(LeafLoaderImpl.ASM_VERSION, next,
                        mergedAccessWidener))
                .build();

            try {
                remapper.readClassPathAsync(getRemapClasspath().toArray(new Path[0]));
            } catch (IOException e) {
                throw new RuntimeException("Failed to populate remap classpath", e);
            }

            for (ModCandidateImpl mod : modsToRemap) {
                RemapInfo info = infoMap.get(mod);

                InputTag tag = remapper.createInputTag();
                info.tag = tag;

                if (requiresMixinRemap(info.inputPath)) {
                    remapMixins.add(tag);
                }

                remapper.readInputsAsync(tag, info.inputPath);
            }

            // Done in a 2nd loop as we need to make sure all the inputs are present before
            // remapping
            for (ModCandidateImpl mod : modsToRemap) {
                RemapInfo info = infoMap.get(mod);
                OutputConsumerPath outputConsumer = new OutputConsumerPath.Builder(
                    info.outputPath).build();

                FileSystemUtil.FileSystemDelegate delegate = FileSystemUtil.getJarFileSystem(
                    info.inputPath, false);

                if (delegate.get() == null) {
                    throw new RuntimeException(
                        "Could not open JAR file " + info.inputPath.getFileName() +
                        " for NIO reading!");
                }

                Path inputJar = delegate.get().getRootDirectories().iterator().next();
                outputConsumer.addNonClassFiles(inputJar, NonClassCopyMode.FIX_META_INF, remapper);

                info.outputConsumerPath = outputConsumer;

                remapper.apply(outputConsumer, info.tag);
            }

            // Done in a 3rd loop as this can happen when the remapper is doing its thing.
            for (ModCandidateImpl mod : modsToRemap) {
                RemapInfo info = infoMap.get(mod);

                if (info.accessWidener != null) {
                    info.accessWidener = remapAccessWidener(info.accessWidener,
                        remapper.getEnvironment().getRemapper(), modNs, runtimeNs);
                }
            }

            remapper.finish();

            for (ModCandidateImpl mod : modsToRemap) {
                RemapInfo info = infoMap.get(mod);

                info.outputConsumerPath.close();

                if (info.accessWidenerPath != null) {
                    try (FileSystemUtil.FileSystemDelegate jarFs = FileSystemUtil.getJarFileSystem(
                        info.outputPath, false)) {
                        FileSystem fs = jarFs.get();

                        Files.delete(fs.getPath(info.accessWidenerPath));
                        Files.write(fs.getPath(info.accessWidenerPath), info.accessWidener);
                    }
                }

                mod.setPaths(Collections.singletonList(info.outputPath));
            }
        } catch (Throwable t) {
            if (remapper != null) {
                remapper.finish();
            }

            for (RemapInfo info : infoMap.values()) {
                if (info.outputPath == null) {
                    continue;
                }

                try {
                    Files.deleteIfExists(info.outputPath);
                } catch (IOException e) {
                    Log.warn(LogCategory.MOD_REMAP, "Error deleting failed output jar %s",
                        info.outputPath, e);
                }
            }

            throw new FormattedException("Failed to remap mods!", t);
        } finally {
            for (RemapInfo info : infoMap.values()) {
                try {
                    if (info.inputIsTemp) {
                        Files.deleteIfExists(info.inputPath);
                    }
                } catch (IOException e) {
                    Log.warn(LogCategory.MOD_REMAP, "Error deleting temporary input jar %s",
                        info.inputIsTemp, e);
                }
            }
        }
    }

    private static byte[] remapAccessWidener(byte[] input, Remapper remapper,
        String modNs, String runtimeNs) {
        AccessWidenerWriter writer = new AccessWidenerWriter();
        AccessWidenerRemapper remappingDecorator = new AccessWidenerRemapper(writer, remapper,
            modNs, runtimeNs);
        AccessWidenerReader accessWidenerReader = new AccessWidenerReader(remappingDecorator);
        accessWidenerReader.read(input, modNs);
        return writer.write();
    }

    private static List<Path> getRemapClasspath() throws IOException {
        String remapClasspathFile = System.getProperty(SystemProperties.REMAP_CLASSPATH_FILE);

        if (remapClasspathFile == null) {
            throw new RuntimeException("No remapClasspathFile provided");
        }

        String content = new String(Files.readAllBytes(Paths.get(remapClasspathFile)),
            StandardCharsets.UTF_8);

        return Arrays.stream(content.split(File.pathSeparator))
            .map(Paths::get)
            .collect(Collectors.toList());
    }

    /**
     * Determine whether a jar requires Mixin remapping with tiny remapper.
     *
     * <p>This is typically the case when a mod was built without the Mixin annotation processor
     * generating refmaps.
     */
    private static boolean requiresMixinRemap(Path inputPath) throws IOException {
        final Manifest manifest = ManifestUtil.readManifest(inputPath);
        if (manifest == null) {
            return false;
        }

        final Attributes mainAttributes = manifest.getMainAttributes();

        return REMAP_TYPE_STATIC.equalsIgnoreCase(mainAttributes.getValue(REMAP_TYPE_MANIFEST_KEY));
    }

    private static class RemapInfo {
        InputTag tag;
        Path inputPath;
        Path outputPath;
        boolean inputIsTemp;
        OutputConsumerPath outputConsumerPath;
        String accessWidenerPath;
        byte[] accessWidener;
    }
}
