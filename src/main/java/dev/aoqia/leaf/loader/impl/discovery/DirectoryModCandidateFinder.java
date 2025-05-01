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

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.EnumSet;

import dev.aoqia.leaf.loader.impl.util.LoaderUtil;
import dev.aoqia.leaf.loader.impl.util.log.Log;
import dev.aoqia.leaf.loader.impl.util.log.LogCategory;

import org.jetbrains.annotations.NotNull;

public class DirectoryModCandidateFinder implements ModCandidateFinder {
    private final Path path;
    private final boolean requiresRemap;
    private final int depth;
    private final Path subpath;

    public DirectoryModCandidateFinder(Path path, boolean requiresRemap, int depth) {
        this.path = LoaderUtil.normalizePath(path);
        this.requiresRemap = requiresRemap;
        this.depth = depth;
        this.subpath = null;
    }

    public DirectoryModCandidateFinder(Path path, boolean requiresRemap, int depth, Path subpath) {
        this.path = LoaderUtil.normalizePath(path);
        this.requiresRemap = requiresRemap;
        this.depth = depth;
        this.subpath = subpath;
    }

    static boolean isValidFile(Path path) {
        /*
         * We only propose a file as a possible mod in the following scenarios:
         * General: Must be a jar file
         *
         * Some OSes Generate metadata so consider the following because of OSes:
         * UNIX: Exclude if file is hidden; this occurs when starting a file name with `.`
         * MacOS: Exclude hidden + startsWith "." since Mac OS names their metadata files in the
         * form of `.mod.jar`
         */

        if (!Files.isRegularFile(path)) {
            return false;
        }

        try {
            if (Files.isHidden(path)) {
                return false;
            }
        } catch (IOException e) {
            Log.warn(LogCategory.DISCOVERY, "Error checking if file %s is hidden", path, e);
            return false;
        }

        String fileName = path.getFileName().toString();

        return fileName.endsWith(".jar") && !fileName.startsWith(".");
    }

    @Override
    public void findCandidates(ModCandidateConsumer out) {
        if (!Files.exists(path)) {
            try {
                Files.createDirectories(path);
                return;
            } catch (IOException e) {
                throw new RuntimeException("Could not create directory " + path, e);
            }
        }

        if (!Files.isDirectory(path)) {
            throw new RuntimeException(path + " is not a directory!");
        }

        try {
            Files.walkFileTree(this.path, EnumSet.of(FileVisitOption.FOLLOW_LINKS),
                depth,
                new SimpleFileVisitor<Path>() {
                    @Override
                    public @NotNull FileVisitResult visitFile(@NotNull Path file,
                        @NotNull BasicFileAttributes attrs) {
                        if (isValidFile(file) && file.getParent().endsWith(subpath)) {
                            out.accept(file, requiresRemap);
                        }

                        return FileVisitResult.CONTINUE;
                    }
                });
        } catch (IOException e) {
            throw new RuntimeException("Exception while searching for mods in '" + path + "'!", e);
        }
    }
}
