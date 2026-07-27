package dev.aoqia.leaf.loader.impl.discovery;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;

import dev.aoqia.leaf.loader.api.SemanticVersion;
import dev.aoqia.leaf.loader.api.VersionParsingException;

public class WorkshopModCandidateFinder extends CachedirModCandidateFinder {
    private final Path workshopDir;

    public WorkshopModCandidateFinder(Path workshopDir, SemanticVersion gameVersion, boolean requiresRemap) {
        super(workshopDir, gameVersion, requiresRemap);

        this.workshopDir = workshopDir;
    }

    @Override
    public void findCandidates(ModCandidateConsumer out) {
        if (!Files.exists(this.workshopDir)) {
            return;
        }

        if (!Files.isDirectory(this.workshopDir)) {
            throw new RuntimeException(this.workshopDir + " is not a directory!");
        }

        try {
            // Workshop folder
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(this.workshopDir, Files::isDirectory)) {
                for (Path rootModFolder : stream) {
                    Path modsFolder = rootModFolder.resolve("mods");
                    if (!Files.exists(modsFolder) || !Files.isDirectory(modsFolder)) {
                        continue;
                    }

                    try (DirectoryStream<Path> stream2 = Files.newDirectoryStream(modsFolder, Files::isDirectory)) {
                        findModsInModRoot(out, stream2);
                    }
                }
            }
        } catch (IOException | VersionParsingException e) {
            throw new RuntimeException("Exception while searching for mods in workshopDir!", e);
        }
    }
}