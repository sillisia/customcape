package dev.customcape.core;

/** Turns a FaceSpec into pixels. Pure Java so it can be unit-tested outside Minecraft. */
public final class FaceRenderer {
    public interface ImageLoader { Raster load(String file); }
    /** Returns the 20x40 front region of a vanilla banner pattern texture (white shape on transparent), or null. */
    public interface PatternSource { Raster pattern(String id); }

    private FaceRenderer() {}

    public static Raster render(FaceSpec f, int w, int h, ImageLoader images, PatternSource patterns) {
        switch (f.type) {
            case BANNER: return banner(f.banner, w, h, patterns);
            case IMAGE: return image(f.image, w, h, images);
            default: return gradient(f.gradient, w, h);
        }
    }

    public static Raster gradient(GradientSpec g, int w, int h) {
        Raster r = new Raster(w, h);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                r.px[y * w + x] = g.colorAt((x + 0.5f) / w, (y + 0.5f) / h);
        return r;
    }

    public static Raster banner(BannerSpec b, int w, int h, PatternSource patterns) {
        Raster r = new Raster(w, h);
        java.util.Arrays.fill(r.px, 0xFF000000 | b.base.rgb);
        for (BannerSpec.Layer l : b.layers) {
            Raster p = patterns == null ? null : patterns.pattern(l.pattern);
            if (p == null) continue;
            Raster m = p.sample(0, 0, p.w, p.h, w, h);
            int tr = (l.color.rgb >> 16) & 255, tg = (l.color.rgb >> 8) & 255, tb = l.color.rgb & 255;
            for (int i = 0; i < r.px.length; i++) {
                int s = m.px[i];
                int a = s >>> 24;
                if (a == 0) continue;
                int sr = (((s >> 16) & 255) * tr) / 255, sg = (((s >> 8) & 255) * tg) / 255, sb = ((s & 255) * tb) / 255;
                int d = r.px[i];
                int nr = (((d >> 16) & 255) * (255 - a) + sr * a) / 255;
                int ng = (((d >> 8) & 255) * (255 - a) + sg * a) / 255;
                int nb = ((d & 255) * (255 - a) + sb * a) / 255;
                r.px[i] = 0xFF000000 | (nr << 16) | (ng << 8) | nb;
            }
        }
        return r;
    }

    public static Raster image(ImageSpec s, int w, int h, ImageLoader images) {
        Raster src = (images == null || s.file == null) ? null : images.load(s.file);
        if (src == null) { // visible "missing image" checkerboard
            Raster r = new Raster(w, h);
            int cell = Math.max(1, w / 5);
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++)
                    r.px[y * w + x] = (((x / cell) + (y / cell)) & 1) == 0 ? 0xFFFF00FF : 0xFF000000;
            return r;
        }
        float[] c = s.cropRect(src.w, src.h, (float) w / h);
        Raster r = src.sample(c[0], c[1], c[2], c[3], w, h);
        if (s.flipH) {
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w / 2; x++) {
                    int a = y * w + x, b = y * w + (w - 1 - x);
                    int t = r.px[a]; r.px[a] = r.px[b]; r.px[b] = t;
                }
        }
        return r;
    }
}
