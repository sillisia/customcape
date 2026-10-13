package dev.customcape.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

/** Reads/writes everything under .minecraft/config/customcape/. */
public final class ProfileStore {
    private final Path root;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Map<String, Raster> imageCache = new HashMap<>();

    public ProfileStore(Path root) {
        this.root = root;
        try {
            Files.createDirectories(profilesDir());
            Files.createDirectories(imagesDir());
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create " + root, e);
        }
    }

    public Path imagesDir() { return root.resolve("images"); }
    private Path profilesDir() { return root.resolve("profiles"); }
    private Path settingsFile() { return root.resolve("settings.json"); }

    public static String safe(String name) {
        String s = name == null ? "" : name.trim().replaceAll("[^A-Za-z0-9 _.-]", "_");
        return s.isEmpty() ? "Default" : (s.length() > 32 ? s.substring(0, 32) : s);
    }

    public List<String> names() {
        List<String> out = new ArrayList<>();
        try (Stream<Path> s = Files.list(profilesDir())) {
            s.forEach(p -> {
                String f = p.getFileName().toString();
                if (f.endsWith(".json")) out.add(f.substring(0, f.length() - 5));
            });
        } catch (IOException ignored) {}
        Collections.sort(out, String.CASE_INSENSITIVE_ORDER);
        return out;
    }

    public CapeProfile load(String name) {
        Path f = profilesDir().resolve(safe(name) + ".json");
        try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
            CapeProfile p = gson.fromJson(r, CapeProfile.class);
            if (p == null) p = new CapeProfile(name);
            p.name = safe(name);
            return p.normalize();
        } catch (Exception e) {
            return new CapeProfile(safe(name)).normalize();
        }
    }

    public void save(CapeProfile p) {
        p.name = safe(p.name);
        Path f = profilesDir().resolve(p.name + ".json");
        try (Writer w = Files.newBufferedWriter(f, StandardCharsets.UTF_8)) {
            gson.toJson(p, w);
        } catch (IOException e) {
            System.err.println("[customcape] could not save " + f + ": " + e);
        }
    }

    public void delete(String name) {
        try { Files.deleteIfExists(profilesDir().resolve(safe(name) + ".json")); } catch (IOException ignored) {}
    }

    public boolean exists(String name) { return Files.exists(profilesDir().resolve(safe(name) + ".json")); }

    public String uniqueName(String base) {
        String b = safe(base), n = b;
        for (int i = 2; exists(n); i++) n = safe(b + " " + i);
        return n;
    }

    @SuppressWarnings("unchecked")
    private synchronized Map<String, Object> readSettings() {
        try (Reader r = Files.newBufferedReader(settingsFile(), StandardCharsets.UTF_8)) {
            Map<String, Object> m = gson.fromJson(r, Map.class);
            return m == null ? new HashMap<>() : m;
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    public synchronized String setting(String key) {
        Object o = readSettings().get(key);
        return o instanceof String s ? s : null;
    }

    public synchronized void putSetting(String key, String value) {
        Map<String, Object> m = readSettings();
        m.put(key, value);
        try (Writer w = Files.newBufferedWriter(settingsFile(), StandardCharsets.UTF_8)) {
            gson.toJson(m, w);
        } catch (IOException ignored) {}
    }

    public String activeName() {
        String s = setting("activeProfile");
        return (s != null && exists(s)) ? s : null;
    }

    public void setActive(String name) { putSetting("activeProfile", name); }

    public List<String> listImages() {
        List<String> out = new ArrayList<>();
        try (Stream<Path> s = Files.list(imagesDir())) {
            s.forEach(p -> {
                String f = p.getFileName().toString().toLowerCase();
                if (f.endsWith(".png") || f.endsWith(".jpg") || f.endsWith(".jpeg") || f.endsWith(".gif") || f.endsWith(".bmp"))
                    out.add(p.getFileName().toString());
            });
        } catch (IOException ignored) {}
        Collections.sort(out, String.CASE_INSENSITIVE_ORDER);
        return out;
    }

    /** Loads an image from the images folder (plain file names only), downscaled so the longest side is <= 2048. */
    public Raster loadImage(String file) {
        if (file == null || file.contains("/") || file.contains("\\") || file.contains("..")) return null;
        Path p = imagesDir().resolve(file);
        try {
            String key = file + "@" + Files.getLastModifiedTime(p).toMillis();
            Raster c = imageCache.get(key);
            if (c != null) return c;
            Raster r = Raster.decode(Files.readAllBytes(p), 16384);
            int m = Math.max(r.w, r.h);
            if (m > 2048) r = r.resize(Math.max(1, r.w * 2048 / m), Math.max(1, r.h * 2048 / m));
            imageCache.entrySet().removeIf(e -> e.getKey().startsWith(file + "@"));
            imageCache.put(key, r);
            return r;
        } catch (Exception e) {
            return null;
        }
    }
}
