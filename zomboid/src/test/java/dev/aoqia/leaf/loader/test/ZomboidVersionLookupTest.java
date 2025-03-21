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

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import dev.aoqia.leaf.loader.impl.game.zomboid.ZomboidVersion;
import dev.aoqia.leaf.loader.impl.game.zomboid.ZomboidVersionLookup;

public final class ZomboidVersionLookupTest {
    public static void main(String[] args) throws IOException {
        if (args.length != 1) throw new RuntimeException("usage: <file/dir-to-try>");

        Path path = Paths.get(args[0]);
        List<String> invalid = new ArrayList<>();

        if (Files.isDirectory(path)) {
            Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (file.getFileName().toString().endsWith(".jar")) {
                        check(file, path.relativize(file).toString(), invalid);
                    }

                    return FileVisitResult.CONTINUE;
                }
            });
        } else {
            check(path, path.getFileName().toString(), invalid);
        }

        System.out.println();

        if (invalid.isEmpty()) {
            System.out.println("All passed!");
        } else {
            System.out.println("Invalid:");

            for (String s : invalid) {
                System.out.println(s);
            }

            System.out.printf("%d invalid results%n", invalid.size());
        }
    }

    private static void check(Path file, String name, List<String> invalid) {
        ZomboidVersion result = ZomboidVersionLookup.getVersionExceptClassVersion(file);
        String msg = String.format("%s: %s", name, result.getId());
        System.out.println(msg);

        if (!pattern.matcher(result.getId()).matches()) {
            System.out.println("** invalid!");
            invalid.add(msg);
        }
    }

    private static final Pattern pattern = Pattern.compile(
        "(0|[1-9]\\d*)" // major
        + "\\.(0|[1-9]\\d*)" // minor
        + "(\\.(0|[1-9]\\d*))?" // patch
        + "(-(unstable)" // pre-release branch name
        + ")?");
}
