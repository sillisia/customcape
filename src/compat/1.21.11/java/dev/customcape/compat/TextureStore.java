package dev.customcape.compat;

import dev.customcape.client.WebSync;
import dev.customcape.core.Raster;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerSkin;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Per-player cape/elytra textures (ours and other mod users') and the hook that swaps them into rendering. */
public final class TextureStore {
    private TextureStore() {}

    private record Entry(Compat.Tex cape, Compat.Tex elytra) {}

    /** Vanilla elytra texture (assets/minecraft/textures/entity/equipment/wings/elytra.png). */
    private static final Identifier DEFAULT_ELYTRA = Identifier.withDefaultNamespace("textures/entity/equipment/wings/elytra.png");

    private static final Map<UUID, Entry> MAP = new ConcurrentHashMap<>();

    /** Replaces the textures for a player. Pass null for a part to leave that part vanilla; null/null removes the entry. */
    public static void set(UUID id, Raster cape, Raster elytra) {
        Entry old = MAP.remove(id);
        if (old != null) { Compat.release(old.cape); Compat.release(old.elytra); }
        if (cape == null && elytra == null) return;
        MAP.put(id, new Entry(
            cape == null ? null : Compat.createTexture("cape_" + id, cape),
            elytra == null ? null : Compat.createTexture("elytra_" + id, elytra)));
    }

    /**
     * Like {@link #set} but always keeps an entry, even when both textures are null. Used for the local player so
     * that "custom elytra off" still pins the plain Minecraft elytra texture when the custom cape is off as well.
     */
    public static void setKeepingEntry(UUID id, Raster cape, Raster elytra) {
        Entry old = MAP.remove(id);
        if (old != null) { Compat.release(old.cape); Compat.release(old.elytra); }
        MAP.put(id, new Entry(
            cape == null ? null : Compat.createTexture("cape_" + id, cape),
            elytra == null ? null : Compat.createTexture("elytra_" + id, elytra)));
    }

    public static int size() { return MAP.size(); }

    public static void clearAllExcept(UUID keep) {
        for (UUID id : new ArrayList<>(MAP.keySet())) if (!id.equals(keep)) set(id, null, null);
    }

    /**
     * Called from MixinAvatarRenderer after the game has filled the render state. We only swap the
     * texture references on the state's PlayerSkin; the cape/elytra *layers and models* are untouched,
     * which is what keeps cape-physics mods working (they read the same skin textures).
     */
    public static void apply(UUID id, AvatarRenderState state) {
        WebSync.noteSeen(id); // tells the sync service poller this player is on screen
        Entry e = MAP.get(id);
        if (e == null || state.skin == null) return;
        PlayerSkin s = state.skin;

        ClientAsset.Texture cape = s.cape();     // vanilla cape unless we have a custom one
        ClientAsset.Texture elytra = s.elytra(); // vanilla elytra unless we have a custom one

        if (e.cape != null) cape = new ClientAsset.ResourceTexture(e.cape.id, e.cape.id);
        // The game's elytra layer falls back to the CAPE texture whenever the skin has no elytra texture, and many
        // capes have no wing area, which makes the wings invisible. So whenever the custom elytra is off, use the
        // plain Minecraft elytra texture instead.
        if (e.elytra == null && elytra == null)
            elytra = new ClientAsset.ResourceTexture(DEFAULT_ELYTRA, DEFAULT_ELYTRA);
        if (e.elytra != null) elytra = new ClientAsset.ResourceTexture(e.elytra.id, e.elytra.id);

        state.skin = new PlayerSkin(s.body(), cape, elytra, s.model(), s.secure());
    }
}
