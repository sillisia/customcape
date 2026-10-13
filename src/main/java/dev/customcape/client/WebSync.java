package dev.customcape.client;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.customcape.compat.Compat;
import dev.customcape.compat.TextureStore;
import dev.customcape.core.Raster;
import dev.customcape.core.SyncCodec;
import dev.customcape.core.TextureComposer;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Client-to-client sharing without any server mod: designs are stored on a small web service
 * (see server/capeserver.py), keyed by Minecraft UUID. Your client proves it owns the account with the
 * standard Mojang join-server handshake, uploads its design, and periodically downloads the designs of
 * players currently on screen. Servers you play on are not involved at all.
 *
 * Nothing here runs unless a sync server URL is set (Mod Menu -> Custom Cape -> "Sync server URL").
 */
public final class WebSync {
    private WebSync() {}

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    private static final Gson GSON = new Gson();
    private static final ScheduledExecutorService EX = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "customcape-sync");
        t.setDaemon(true);
        return t;
    });

    private static final Map<UUID, Long> seen = new ConcurrentHashMap<>();     // last time rendered
    private static final Map<UUID, Long> checked = new ConcurrentHashMap<>();  // last time we asked the service
    private static final Map<UUID, Long> versions = new ConcurrentHashMap<>(); // design version we already have

    private static volatile String token;
    private static volatile long tokenExpiry;
    private static volatile int lastUploadKey;

    public static void start() {
        EX.scheduleWithFixedDelay(() -> { try { poll(); } catch (Exception e) { log("poll failed: " + e); } }, 5, 5, TimeUnit.SECONDS);
        EX.schedule(() -> Compat.runOnMain(LocalState::publish), 10, TimeUnit.SECONDS); // announce ourselves after startup
    }

    public static void noteSeen(UUID id) { seen.put(id, System.currentTimeMillis()); }

    private static String base() {
        if (LocalState.store() == null) return null;
        String u = LocalState.store().setting("serverUrl");
        if (u == null || u.isBlank()) return null;
        u = u.trim();
        while (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        return (u.startsWith("http://") || u.startsWith("https://")) ? u : null;
    }

    private static void log(String s) { System.err.println("[customcape] " + s); }

    // ------------------------------------------------------------ upload

    public static void upload(byte[][] faces) {
        String base = base();
        if (base == null) return;
        int key = Objects.hash(base, Arrays.deepHashCode(faces));
        EX.execute(() -> {
            if (key == lastUploadKey) return;
            try {
                JsonObject body = new JsonObject();
                JsonArray arr = new JsonArray();
                for (byte[] f : faces) arr.add(Base64.getEncoder().encodeToString(f));
                body.add("png", arr);
                HttpRequest req = HttpRequest.newBuilder(URI.create(base + "/v1/design/" + Compat.selfUuid()))
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Bearer " + token(base))
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(GSON.toJson(body))).build();
                HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
                if (res.statusCode() == 401) token = null;
                if (res.statusCode() / 100 != 2) throw new java.io.IOException("HTTP " + res.statusCode() + " " + res.body());
                lastUploadKey = key;
            } catch (Exception e) {
                log("upload failed: " + e);
            }
        });
    }

    private static String token(String base) throws Exception {
        if (token != null && System.currentTimeMillis() < tokenExpiry) return token;
        UUID id = Compat.selfUuid();
        String name = Compat.selfName();
        JsonObject a = new JsonObject();
        a.addProperty("uuid", id.toString());
        a.addProperty("name", name);
        String serverId = postJson(base + "/v1/auth/start", a).get("serverId").getAsString();
        Compat.joinServer(serverId);
        a.addProperty("serverId", serverId);
        JsonObject fin = postJson(base + "/v1/auth/finish", a);
        long ttl = fin.has("ttl") ? fin.get("ttl").getAsLong() : 3600;
        tokenExpiry = System.currentTimeMillis() + Math.max(60, ttl - 60) * 1000L;
        return token = fin.get("token").getAsString();
    }

    private static JsonObject postJson(String url, JsonObject body) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body))).build();
        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() / 100 != 2) throw new java.io.IOException("HTTP " + res.statusCode() + " from " + url);
        return GSON.fromJson(res.body(), JsonObject.class);
    }

    // ------------------------------------------------------------ download

    private static void poll() throws Exception {
        String base = base();
        if (base == null) return;
        long now = System.currentTimeMillis();
        UUID self = Compat.selfUuid();
        List<UUID> ask = new ArrayList<>();
        for (Map.Entry<UUID, Long> e : seen.entrySet()) {
            if (now - e.getValue() > 15_000) { seen.remove(e.getKey()); continue; }
            if (e.getKey().equals(self)) continue;
            if (now - checked.getOrDefault(e.getKey(), 0L) > 60_000 && ask.size() < 64) ask.add(e.getKey());
        }
        if (ask.isEmpty()) return;

        StringBuilder q = new StringBuilder();
        for (UUID id : ask) {
            if (q.length() > 0) q.append(',');
            q.append(id).append(':').append(versions.getOrDefault(id, 0L));
            checked.put(id, now);
        }
        HttpRequest req = HttpRequest.newBuilder(URI.create(base + "/v1/designs?ids=" + q)).timeout(Duration.ofSeconds(15)).GET().build();
        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) return;
        JsonObject all = GSON.fromJson(res.body(), JsonObject.class);
        for (Map.Entry<String, JsonElement> e : all.entrySet()) {
            UUID id;
            try { id = UUID.fromString(e.getKey()); } catch (IllegalArgumentException bad) { continue; }
            if (!ask.contains(id)) continue; // never accept data we did not ask for
            JsonObject d = e.getValue().getAsJsonObject();
            long v = d.get("v").getAsLong();
            JsonArray png = d.getAsJsonArray("png");
            Raster cape = null, elytra = null;
            if (png != null && png.size() == 4) {
                byte[][] f = new byte[4][];
                for (int i = 0; i < 4; i++) f[i] = Base64.getDecoder().decode(png.get(i).getAsString());
                // Each of cape / elytra may be absent (empty entries) if that player disabled it.
                int s = SyncCodec.scaleOf(f[0].length > 0 ? f[0] : f[2]);
                if (s > 0) {
                    if (f[0].length > 0 && f[1].length > 0) {
                        Raster cb = SyncCodec.decodeFace(f[0], s, 16), cf = SyncCodec.decodeFace(f[1], s, 16);
                        if (cb != null && cf != null) cape = TextureComposer.composeCape(cb, cf);
                    }
                    if (f[2].length > 0 && f[3].length > 0) {
                        Raster eb = SyncCodec.decodeFace(f[2], s, 20), ef = SyncCodec.decodeFace(f[3], s, 20);
                        if (eb != null && ef != null) elytra = TextureComposer.composeElytra(eb, ef);
                    }
                }
            }
            versions.put(id, v);
            Raster c = cape, el = elytra;
            Compat.runOnMain(() -> {
                if (TextureStore.size() > 128) { TextureStore.clearAllExcept(Compat.selfUuid()); versions.clear(); }
                TextureStore.set(id, c, el);
            });
        }
    }
}
