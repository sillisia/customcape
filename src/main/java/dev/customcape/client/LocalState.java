package dev.customcape.client;

import dev.customcape.compat.Compat;
import dev.customcape.compat.TextureStore;
import dev.customcape.core.*;

import java.nio.file.Path;
import java.util.List;

/** The player's own profiles: which one is active, saving, rebuilding the in-game textures, syncing. */
public final class LocalState {
    private LocalState() {}

    private static ProfileStore store;
    private static CapeProfile active;
    private static boolean dirty;

    public static void init(Path root) {
        store = new ProfileStore(root);
        List<String> names = store.names();
        if (names.isEmpty()) {
            store.save(new CapeProfile("Default").normalize());
            names = store.names();
        }
        String a = store.activeName();
        active = store.load(a != null ? a : names.get(0));
	store.setActive(active.name);
    }

    public static ProfileStore store() { return store; }
    public static CapeProfile profile() { return active; }

    public static FaceRenderer.ImageLoader imageLoader() { return store::loadImage; }
    public static FaceRenderer.PatternSource patterns() { return Compat::bannerPattern; }

    public static Raster face(CapeProfile p, boolean elytra, boolean front, int scale) {
        SurfaceSpec s = p.surface(elytra);
        return FaceRenderer.render(front ? s.front : s.back, 10 * scale, (elytra ? 20 : 16) * scale, imageLoader(), patterns());
    }

    /** Re-renders the local player's textures at full quality (scale 8 => 512x256). */
    public static void rebuildTextures() {
        if (active == null) return;
        Raster cape = null, elytra = null;
        if (active.capeEnabled)
            cape = TextureComposer.composeCape(face(active, false, false, 8), face(active, false, true, 8));
        if (active.elytraEnabled)
            elytra = TextureComposer.composeElytra(face(active, true, false, 8), face(active, true, true, 8));
        TextureStore.setKeepingEntry(Compat.selfUuid(), cape, elytra);
    }

    /** Call after every edit: updates the in-game cape immediately, saves to disk shortly after. */
    public static void changed() {
        dirty = true;
        rebuildTextures();
    }

    public static void flush() {
        if (dirty && active != null) { store.save(active); dirty = false; }
    }

    /** Replaces the active profile's contents (used by the editor's Undo). The name is kept. */
    public static void restore(CapeProfile snapshot) {
        String keep = active.name;
        active = snapshot.normalize();
        active.name = keep;
        dirty = true;
        rebuildTextures();
    }

    public static void select(String name) {
        flush();
        active = store.load(name);
        store.setActive(active.name);
        rebuildTextures();
    }

    public static void create(String base) {
        flush();
        CapeProfile p = new CapeProfile(store.uniqueName(base)).normalize();
        store.save(p);
        select(p.name);
    }

    public static void duplicate() {
        flush();
        CapeProfile copy = store.load(active.name);
        copy.name = store.uniqueName(active.name + " copy");
        store.save(copy);
        select(copy.name);
    }

    public static void deleteActive() {
        List<String> names = store.names();
        if (names.size() <= 1) return;
        String old = active.name;
        dirty = false;
        store.delete(old);
        select(store.names().get(0));
    }

    /** @return false if the name is taken or invalid */
    public static boolean rename(String newName) {
        String n = ProfileStore.safe(newName);
        if (n.equals(active.name)) return true;
        if (store.exists(n)) return false;
        String old = active.name;
        active.name = n;
        store.save(active);
        store.delete(old);
        store.setActive(n);
        dirty = false;
        return true;
    }

    /** Rasterises the active design and uploads it to the sync service (no-op if no service URL is set). */
    public static void publish() {
        if (active == null) return;
        byte[][] data = (active.anyEnabled() && active.share) ? SyncCodec.encode(active, imageLoader(), patterns()) : SyncCodec.empty();
        if (data == null) data = SyncCodec.empty();
        WebSync.upload(data);
    }
}
