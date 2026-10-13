package dev.customcape.core;

/** Rasterises a profile into 4 small PNGs for sending to other players. Order: capeBack, capeFront, elytraBack, elytraFront. */
public final class SyncCodec {
    public static final int MAX_TOTAL_BYTES = 150000; // the sync web service accepts up to 200 KB per design
    public static final int MAX_SCALE = 8;
    private SyncCodec() {}

    public static byte[][] empty() { return new byte[][]{new byte[0], new byte[0], new byte[0], new byte[0]}; }

    /**
     * @return the 4 PNGs (a disabled cape/elytra is sent as two empty entries), or null if even the
     *         smallest version does not fit
     */
    public static byte[][] encode(CapeProfile p, FaceRenderer.ImageLoader il, FaceRenderer.PatternSource ps) {
        boolean[] on = {p.capeEnabled, p.capeEnabled, p.elytraEnabled, p.elytraEnabled};
        Raster[] big = new Raster[4];
        if (p.capeEnabled) {
            big[0] = FaceRenderer.render(p.surface(false).back, 10 * MAX_SCALE, 16 * MAX_SCALE, il, ps);
            big[1] = FaceRenderer.render(p.surface(false).front, 10 * MAX_SCALE, 16 * MAX_SCALE, il, ps);
        }
        if (p.elytraEnabled) {
            big[2] = FaceRenderer.render(p.surface(true).back, 10 * MAX_SCALE, 20 * MAX_SCALE, il, ps);
            big[3] = FaceRenderer.render(p.surface(true).front, 10 * MAX_SCALE, 20 * MAX_SCALE, il, ps);
        }
        for (int s : new int[]{8, 6, 5, 4, 3, 2, 1}) {
            byte[][] out = new byte[4][];
            int total = 0;
            for (int i = 0; i < 4; i++) {
                if (!on[i]) { out[i] = new byte[0]; continue; }
                int hh = (i < 2 ? 16 : 20) * s;
                out[i] = (s == MAX_SCALE ? big[i] : big[i].resize(10 * s, hh)).toPng();
                total += out[i].length;
            }
            if (total <= MAX_TOTAL_BYTES) return out;
        }
        return null;
    }

    /** Validates dimensions and returns the face raster, or null if the data is unusable. */
    public static Raster decodeFace(byte[] png, int expectedScale, int unitsTall) {
        try {
            Raster r = Raster.decode(png, 20 * MAX_SCALE);
            if (r.w != 10 * expectedScale || r.h != unitsTall * expectedScale) return null;
            return r;
        } catch (Exception e) {
            return null;
        }
    }

    /** Scale (1..8) implied by the cape-back PNG width, or -1. */
    public static int scaleOf(byte[] capeBackPng) {
        if (capeBackPng == null || capeBackPng.length == 0) return -1;
        try {
            Raster r = Raster.decode(capeBackPng, 20 * MAX_SCALE);
            if (r.w % 10 != 0) return -1;
            int s = r.w / 10;
            return (s >= 1 && s <= MAX_SCALE) ? s : -1;
        } catch (Exception e) {
            return -1;
        }
    }
}
