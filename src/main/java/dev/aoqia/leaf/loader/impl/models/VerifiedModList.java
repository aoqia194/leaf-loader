package dev.aoqia.leaf.loader.impl.models;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import dev.aoqia.leaf.loader.impl.FormattedException;
import dev.aoqia.leaf.loader.impl.discovery.ModCandidateImpl;

public class VerifiedModList {
    public static final int VERSION = 1;

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
            Files.write(this.path, (VERSION + "\n").getBytes());
            return;
        }

        try (BufferedReader br = Files.newBufferedReader(this.path)) {
            int version = Character.getNumericValue(br.readLine().charAt(0));
            if (version != VERSION) {
                br.close();
                Files.write(this.path, (VERSION + "\n").getBytes());
                return;
            }

            String line;
            while ((line = br.readLine()) != null) {
                String[] components = line.split("\t");
                if (components.length != 4) {
                    throw new IllegalStateException("Parsed bad verified mod list file");
                }

                String workshopId = components[0];
                String gameModId = components[1];
                String leafModId = components[2];
                String jarHash = components[3];

                this.internal.putIfAbsent(workshopId, new ArrayList<>());
                this.internal.get(workshopId).add(new Mod(workshopId, leafModId, gameModId, jarHash));
            }
        }
    }

    public void write() throws IOException {
        try (BufferedWriter bw = Files.newBufferedWriter(this.path, StandardOpenOption.CREATE)) {
            bw.write(VERSION + "\n");
            for (String workshopId : this.internal.keySet()) {
                for (Mod mod : this.internal.get(workshopId)) {
                    bw.write(mod.getWorkshopId()
                        + "\t" + mod.getGameId()
                        + "\t" + mod.getId()
                        + "\t" + mod.getJarHash()
                        + "\n");
                }
            }
        }
    }

    public void add(ModCandidateImpl mod) {
        this.internal.putIfAbsent(mod.getGameId(), new ArrayList<>());
        this.internal.get(mod.getGameId()).add(new Mod(mod));
    }

    public boolean contains(ModCandidateImpl mod) {
        return this.internal.containsKey(mod.getWorkshopId()) && this.internal.get(mod.getWorkshopId())
            .stream()
            .anyMatch(m ->
                m.getGameId().equals(mod.getGameId())
                    && m.getWorkshopId().equals(mod.getWorkshopId())
                    && m.getId().equals(mod.getId())
                    && m.getJarHash().equals(mod.getJarHash()));
    }

    private boolean containsIgnoreHash(ModCandidateImpl mod) {
        return this.internal.containsKey(mod.getWorkshopId()) && this.internal.get(mod.getWorkshopId())
            .stream()
            .anyMatch(m ->
                m.getGameId().equals(mod.getGameId())
                    && m.getWorkshopId().equals(mod.getWorkshopId())
                    && m.getId().equals(mod.getId()));
    }

    private boolean containsWithBadHash(ModCandidateImpl mod) {
        return this.internal.containsKey(mod.getWorkshopId()) && this.internal.get(mod.getWorkshopId())
            .stream()
            .anyMatch(m ->
                m.getGameId().equals(mod.getGameId())
                    && m.getWorkshopId().equals(mod.getWorkshopId())
                    && m.getId().equals(mod.getId())
                    && !m.getJarHash().equals(mod.getJarHash()));
    }

    public boolean isVerified(ModCandidateImpl mod) {
        return contains(mod);
    }

    public boolean wasVerified(ModCandidateImpl mod) {
        return containsWithBadHash(mod);
    }

    public void updateMod(ModCandidateImpl mod) {
        Optional<Mod> stored = this.internal.get(mod.getGameId()).stream().findFirst();
        if (!stored.isPresent()) {
            throw new FormattedException("Failed to update mod %s (%s/%s) after verifying",
                mod.getId(), mod.getWorkshopId(), mod.getGameId());
        }

        stored.get().setWorkshopId(mod.getWorkshopId());
        stored.get().setId(mod.getId());
        stored.get().setGameId(mod.getGameId());
        stored.get().setJarHash(mod.getJarHash());
    }

    public Map<String, List<Mod>> getInternal() {
        return internal;
    }

    public static class Mod {
        private String workshopId;
        private String id;
        private String gameId;
        private String jarHash;

        public Mod(String workshopId, String id, String gameId, String jarHash) {
            this.workshopId = workshopId;
            this.id = id;
            this.gameId = gameId;
            this.jarHash = jarHash;
        }

        public Mod(ModCandidateImpl candidate) {
            this.workshopId = candidate.getWorkshopId();
            this.id = candidate.getId();
            this.gameId = candidate.getGameId();
            this.jarHash = candidate.getJarHash();
        }

        public String getWorkshopId() {
            return workshopId;
        }

        public void setWorkshopId(String workshopId) {
            this.workshopId = workshopId;
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
