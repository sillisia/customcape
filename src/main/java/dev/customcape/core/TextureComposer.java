package dev.customcape.core;

/**
 * Packs finished face rasters into the vanilla 64x32 (times scale) cape / elytra texture layouts.
 * Faces must be 10*s wide; capes 16*s tall, elytra wings 20*s tall.
 *
 * Layout constants live here so that porting to a version with a different model layout is a one-file change.
 */
public final class TextureComposer {
    private TextureComposer() {}

    public static Raster composeCape(Raster back, Raster front) {
        int s = back.w / 10;
        Raster t = new Raster(64 * s, 32 * s);
        t.copyRegion(back, 0, 0, 10 * s, 16 * s, s, s);          // outside, seen from behind
        t.copyRegion(front, 0, 0, 10 * s, 16 * s, 12 * s, s);    // inside, against the player's back
        t.copyRegion(back, 0, 0, 10 * s, s, s, 0);               // top edge
        t.copyRegion(back, 0, 15 * s, 10 * s, s, 11 * s, 0);     // bottom edge
        t.copyRegion(back, 0, 0, s, 16 * s, 0, s);               // left edge
        t.copyRegion(back, 9 * s, 0, s, 16 * s, 11 * s, s);     // right edge
        return t;
    }

    // Elytra wing box: texOffs(22,0), size 10x20x2.
    public static final int ELYTRA_OUTER_X = 36, ELYTRA_INNER_X = 24, ELYTRA_FACE_Y = 2;

    /** Brightness of the wing's solid side color relative to the average color of the wing's outside face. */
    public static final float ELYTRA_EDGE_SHADE = 0.35f;

    public static Raster composeElytra(Raster back, Raster front) {
        int s = back.w / 10;
        Raster t = new Raster(64 * s, 32 * s);
        t.copyRegion(back, 0, 0, 10 * s, 20 * s, ELYTRA_OUTER_X * s, ELYTRA_FACE_Y * s);
        t.copyRegion(front, 0, 0, 10 * s, 20 * s, ELYTRA_INNER_X * s, ELYTRA_FACE_Y * s);

        // Wing sides. The wing box is drawn inflated, so its 2-texel-deep sides are about 4 units thick and show up
        // clearly from the side. Anything detailed on them (the picture, or streaks of it) reads as a thick slab.
        // Fill them with ONE solid dark color derived from the face instead, like the plain rim of a vanilla wing,
        // so they read as a thin edge rather than as extra picture.
        int edge = edgeColor(back);
        fill(t, edge, 24 * s, 0, 10 * s, 2 * s);                       // top
        fill(t, edge, 34 * s, 0, 10 * s, 2 * s);                       // bottom
        fill(t, edge, 22 * s, ELYTRA_FACE_Y * s, 2 * s, 20 * s);       // left
        fill(t, edge, 34 * s, ELYTRA_FACE_Y * s, 2 * s, 20 * s);       // right
        return t;
    }

    /** Alpha-weighted average color of the face, darkened by ELYTRA_EDGE_SHADE, fully opaque. */
    private static int edgeColor(Raster face) {
        long r = 0, g = 0, b = 0, w = 0;
        for (int p : face.px) {
            int a = p >>> 24;
            if (a == 0) continue;
            r += (long) ((p >> 16) & 255) * a;
            g += (long) ((p >> 8) & 255) * a;
            b += (long) (p & 255) * a;
            w += a;
        }
        if (w == 0) return 0xFF101010;
        int rr = Math.round(r / (float) w * ELYTRA_EDGE_SHADE);
        int gg = Math.round(g / (float) w * ELYTRA_EDGE_SHADE);
        int bb = Math.round(b / (float) w * ELYTRA_EDGE_SHADE);
        return 0xFF000000 | (rr << 16) | (gg << 8) | bb;
    }

    private static void fill(Raster dst, int argb, int x0, int y0, int w, int h) {
        for (int y = y0; y < y0 + h; y++)
            for (int x = x0; x < x0 + w; x++)
                dst.px[y * dst.w + x] = argb;
    }
}
