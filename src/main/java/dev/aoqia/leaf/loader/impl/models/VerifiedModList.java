package dev.aoqia.leaf.loader.impl.models;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.aoqia.leaf.loader.impl.discovery.ModCandidateImpl;

public class VerifiedModList {
    private final Path path;
    private final Map<String, List<Mod>> internal;

    public VerifiedModList(Path path) {
        this.path = path;
        this.internal = new HashMap<>();
    }

    public void readOrCreate() throws IOException {
        // If the mod list data file doesn't exist, initialise it and return empty.
        if (!Files.exists(this.path)) {
            Files.createDirectory(this.path.getParent());
            Files.write(this.path, new byte[]{});
            return;
        }

        for (String line : Files.readAllLines(this.path)) {
            String[] components = line.split("\t");
            if (components.length != 3) {
                throw new IllegalStateException("Parsed bad verified mod list file");
            }

            String gameModId = components[0];
            String leafModId = components[1];
            String jarHash = components[2];

            this.internal.putIfAbsent(gameModId, new ArrayList<>());
            this.internal.get(gameModId).add(new Mod(leafModId, gameModId, jarHash));
        }
    }

    public void write() throws IOException {
        try (BufferedWriter bw = Files.newBufferedWriter(this.path, StandardOpenOption.CREATE)) {
            for (String gameModId : this.internal.keySet()) {
                for (Mod mod : this.internal.get(gameModId)) {
                    bw.write(mod.getGameId() + "\t" + mod.getId() + "\t" + mod.getJarHash() + "\n");
                }
            }
        }
    }

    public void add(ModCandidateImpl mod) {
        this.internal.putIfAbsent(mod.getGameId(), new ArrayList<>());
        this.internal.get(mod.getGameId()).add(new Mod(mod));
    }

    public boolean contains(Mod mod) {
        return this.internal.containsKey(mod.getGameId()) && this.internal.get(mod.getGameId()).contains(mod);
    }

    public boolean contains(ModCandidateImpl mod) {
        return this.internal.containsKey(mod.getGameId()) && this.internal.get(mod.getGameId()).stream().anyMatch(m ->
            m.getGameId().equals(mod.getGameId())
                && m.getId().equals(mod.getId())
                && m.getJarHash().equals(mod.getJarHash())
        );
    }

    public boolean isVerified(ModCandidateImpl mod) {
        return contains(mod);
    }

    public Map<String, List<Mod>> getInternal() {
        return internal;
    }

    public static class Mod {
        private String id;
        private String gameId;
        private String jarHash;

        public Mod(String id, String gameId, String jarHash) {
            this.id = id;
            this.gameId = gameId;
            this.jarHash = jarHash;
        }

        public Mod(ModCandidateImpl candidate) {
            this.id = candidate.getId();
            this.gameId = candidate.getGameId();
            this.jarHash = candidate.getJarHash();
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getGameId() {
            return gameId;
        }

        public void setGameId(String gameId) {
            this.gameId = gameId;
        }

        public String getJarHash() {
            return jarHash;
        }

        public void setJarHash(String jarHash) {
            this.jarHash = jarHash;
        }
    }
}
