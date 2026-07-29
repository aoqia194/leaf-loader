package dev.aoqia.leaf.loader.impl.models;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.jetbrains.annotations.Nullable;

import dev.aoqia.leaf.loader.impl.FormattedException;

public class ModInfo {
    private String name;
    private String id;

    public static @Nullable ModInfo parse(Path modInfo) {
        if (!Files.exists(modInfo)) {
            return null;
        }

        ModInfo inst = new ModInfo();

        try (BufferedReader br = Files.newBufferedReader(modInfo)) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.startsWith("name=")) {
                    inst.name = line.replace("name=", "");
                } else if (line.startsWith("id=")) {
                    inst.id = line.replace("id=", "");
                }
            }
        } catch (IOException e) {
            throw new FormattedException("Failed to parse mod.info for mod at path: %s", modInfo.toString());
        }

        return inst;
    }

    public String getName() {
        return name;
    }

    public String getId() {
        return id;
    }
}
