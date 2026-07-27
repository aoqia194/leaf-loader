package dev.aoqia.leaf.loader.impl.discovery;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import dev.aoqia.leaf.loader.api.Version;
import dev.aoqia.leaf.loader.api.VersionParsingException;

import dev.aoqia.leaf.loader.api.SemanticVersion;
import dev.aoqia.leaf.loader.impl.util.LoaderUtil;
import dev.aoqia.leaf.loader.impl.util.log.Log;
import dev.aoqia.leaf.loader.impl.util.log.LogCategory;

public class CachedirModCandidateFinder implements ModCandidateFinder {
    private final Path cacheDir;
    private final SemanticVersion gameVersion;
    private final boolean requiresRemap;

    public CachedirModCandidateFinder(Path cacheDir, SemanticVersion gameVersion, boolean requiresRemap) {
        this.cacheDir = LoaderUtil.normalizePath(cacheDir);
        this.gameVersion = gameVersion;
        this.requiresRemap = requiresRemap;
    }

    @Override
    public void findCandidates(ModCandidateConsumer out) {
        // Cachedir not existing means we can silently exit. Can guarantee no mods will exist here.
        if (!Files.exists(this.cacheDir)) {
            return;
        }

        if (!Files.isDirectory(this.cacheDir)) {
            throw new RuntimeException(this.cacheDir + " is not a directory!");
        }

        try {
            // mods folder
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(this.cacheDir.resolve("mods"), Files::isDirectory)) {
                findModsInModRoot(out, stream);
            }

            // Workshop folder
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(this.cacheDir.resolve("Workshop"), Files::isDirectory)) {
                for (Path rootModFolder : stream) {
                    Path modsFolder = rootModFolder.resolve("Contents/mods");
                    if (!Files.exists(modsFolder) || !Files.isDirectory(modsFolder)) {
                        continue;
                    }

                    try (DirectoryStream<Path> stream2 = Files.newDirectoryStream(modsFolder, Files::isDirectory)) {
                        findModsInModRoot(out, stream2);
                    }
                }
            }
        } catch (IOException | VersionParsingException e) {
            throw new RuntimeException("Exception while searching for mods in cachedir!", e);
        }
    }

    protected void findModsInModRoot(ModCandidateConsumer out, DirectoryStream<Path> stream)
        throws IOException, VersionParsingException
    {
        for (Path path : stream) {
            Path modPath = getVersionFolder(path);

            Path javaPath = modPath.resolve("media/java/");
            if (!Files.exists(javaPath) || !Files.isDirectory(javaPath)) {
                continue;
            }

            try (Stream<Path> stream2 = Files.list(javaPath)) {
                stream2.filter(CachedirModCandidateFinder::isValidFile)
                    .forEach(f -> out.accept(f, this.requiresRemap));
            }
        }
    }

    Path getVersionFolder(Path modPath) throws VersionParsingException {
        if (this.gameVersion.getVersionComponent(1) <= 41) {
            return modPath;
        }

        String[] versionFolders = modPath.toFile().list((file, name) ->
            file.isDirectory() && !name.equalsIgnoreCase("media"));

        if (versionFolders == null || versionFolders.length == 0) {
            return modPath;
        }

        SemanticVersion bestVersion = SemanticVersion.parse(this.gameVersion.getVersionComponent(1) + ".0.0");
        String bestFolder = null;
        for (String folder : versionFolders) {
            SemanticVersion version = SemanticVersion.parse(folder);
            if (version.compareTo((Version) bestVersion) >= 0 && version.compareTo((Version) this.gameVersion) <= 0) {
                bestFolder = folder;
                bestVersion = version;
            }
        }

        if (bestFolder == null) {
            throw new IllegalStateException("Failed to get best version folder for mod at path '" + modPath + "'!");
        }

        return modPath.resolve(bestFolder);
    }

    static boolean isValidFile(Path path) {
        /*
         * We only propose a file as a possible mod in the following scenarios:
         * General: Must be a jar file
         *
         * Some OSes Generate metadata so consider the following because of OSes:
         * UNIX: Exclude if file is hidden; this occurs when starting a file name with `.`
         * MacOS: Exclude hidden + startsWith "." since Mac OS names their metadata files in the form of `.mod.jar`
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
}
