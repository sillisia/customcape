package dev.customcape.compat;

import com.mojang.blaze3d.platform.NativeImage;
import dev.customcape.core.Raster;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * EVERYTHING in the mod that touches Minecraft APIs which change between versions lives in this
 * directory (src/compat/<version>/). To support a new Minecraft version, copy this folder and fix
 * the compile errors - see PORTING.md.
 */
public final class Compat {
    private Compat() {}

    /** A dynamically registered texture. */
    public static final class Tex {
        final Identifier id;
        DynamicTexture tex;
        public int w, h;
        Tex(Identifier id) { this.id = id; }
    }

    private static int counter = 0;

    public static Tex createTexture(String name, Raster r) {
        Identifier id = Identifier.fromNamespaceAndPath("customcape", "dyn/" + name.toLowerCase().replaceAll("[^a-z0-9_.-]", "_") + "_" + (counter++));
        Tex t = new Tex(id);
        upload(t, r);
        return t;
    }

    public static void update(Tex t, Raster r) {
        release(t);
        upload(t, r);
    }

    private static void upload(Tex t, Raster r) {
        NativeImage img = new NativeImage(r.w, r.h, false);
        for (int y = 0; y < r.h; y++)
            for (int x = 0; x < r.w; x++)
                img.setPixel(x, y, r.px[y * r.w + x]); // ARGB
        DynamicTexture dt = new DynamicTexture(() -> t.id.toString(), img);
        Minecraft.getInstance().getTextureManager().register(t.id, dt);
        t.tex = dt;
        t.w = r.w;
        t.h = r.h;
    }

    public static void release(Tex t) {
        if (t != null && t.tex != null) {
            Minecraft.getInstance().getTextureManager().release(t.id);
            t.tex = null;
        }
    }

    /** Draws the whole texture scaled into the given rectangle. */
    public static void blit(GuiGraphics g, Tex t, int x, int y, int w, int h) {
        if (t == null || t.tex == null) return;
        g.blit(RenderPipelines.GUI_TEXTURED, t.id, x, y, 0f, 0f, w, h, t.w, t.h, t.w, t.h);
    }

    public static UUID selfUuid() {
        return Minecraft.getInstance().getUser().getProfileId();
    }

    public static String selfName() {
        return Minecraft.getInstance().getUser().getName();
    }

    public static void runOnMain(Runnable r) {
        Minecraft.getInstance().execute(r);
    }

    /** Mojang "join server" handshake used to prove to the sync service that we own this account. Blocking; call off-thread. */
    public static void joinServer(String serverId) throws Exception {
        Minecraft mc = Minecraft.getInstance();
        net.minecraft.client.User u = mc.getUser();
        mc.services().sessionService().joinServer(u.getProfileId(), u.getAccessToken(), serverId);
    }

	public static void openFolder(Path p) {
	    net.minecraft.util.Util.getPlatform().openPath(p);
	}

    private static final Map<String, Raster> PATTERNS = new HashMap<>();

    /** The 20x40 front region of vanilla's textures/entity/banner/<id>.png, or null. */
    public static Raster bannerPattern(String id) {
        if (PATTERNS.containsKey(id)) return PATTERNS.get(id);
        Raster out = null;
        try {
            Optional<Resource> res = Minecraft.getInstance().getResourceManager()
                .getResource(Identifier.withDefaultNamespace("textures/entity/banner/" + id + ".png"));
            if (res.isPresent()) {
                try (InputStream in = res.get().open()) {
                    Raster full = Raster.decode(in.readAllBytes(), 1024);
                    if (full.w >= 21 && full.h >= 41) {
                        out = new Raster(20, 40);
                        out.copyRegion(full, 1, 1, 20, 40, 0, 0);
                    }
                }
            }
        } catch (Exception ignored) {}
        PATTERNS.put(id, out);
        return out;
    }
}
