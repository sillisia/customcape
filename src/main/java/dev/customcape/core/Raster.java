package dev.customcape.core;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

/** Minimal ARGB pixel buffer. No Minecraft dependencies, so it is shared by every game version. */
public final class Raster {
    public final int w, h;
    public final int[] px;

    public Raster(int w, int h) {
        this.w = w;
        this.h = h;
        this.px = new int[w * h];
    }

    public static int argb(int a, int r, int g, int b) { return (a << 24) | (r << 16) | (g << 8) | b; }

    public static Raster fromImage(BufferedImage img) {
        Raster r = new Raster(img.getWidth(), img.getHeight());
        img.getRGB(0, 0, r.w, r.h, r.px, 0, r.w);
        return r;
    }

    public BufferedImage toImage() {
        BufferedImage i = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        i.setRGB(0, 0, w, h, px, 0, w);
        return i;
    }

    public byte[] toPng() {
        try {
            ByteArrayOutputStream o = new ByteArrayOutputStream();
            ImageIO.write(toImage(), "png", o);
            return o.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Decodes PNG/JPEG/GIF/BMP bytes. Refuses images larger than maxDim in either direction (decompression-bomb guard). */
    public static Raster decode(byte[] data, int maxDim) throws IOException {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
            Iterator<ImageReader> it = ImageIO.getImageReaders(in);
            if (!it.hasNext()) throw new IOException("Unknown image format");
            ImageReader rd = it.next();
            try {
                rd.setInput(in, true, true);
                int iw = rd.getWidth(0), ih = rd.getHeight(0);
                if (iw < 1 || ih < 1 || iw > maxDim || ih > maxDim) throw new IOException("Image too large: " + iw + "x" + ih);
                return fromImage(rd.read(0));
            } finally {
                rd.dispose();
            }
        }
    }

    /**
     * Resamples the source rectangle (sx,sy,sw,sh) (fractional pixels allowed) into a new ow x oh raster.
     * Nearest-neighbour when enlarging (keeps banner pixel art crisp), box-filtered when shrinking.
     */
    public Raster sample(float sx, float sy, float sw, float sh, int ow, int oh) {
        Raster out = new Raster(ow, oh);
        int n = Math.max(1, Math.min(6, (int) Math.ceil(Math.max(sw / ow, sh / oh))));
        for (int oy = 0; oy < oh; oy++) {
            for (int ox = 0; ox < ow; ox++) {
                long a = 0, r = 0, g = 0, b = 0;
                for (int j = 0; j < n; j++) {
                    for (int i = 0; i < n; i++) {
                        float fx = sx + (ox + (i + 0.5f) / n) * sw / ow;
                        float fy = sy + (oy + (j + 0.5f) / n) * sh / oh;
                        int ix = Math.max(0, Math.min(w - 1, (int) Math.floor(fx)));
                        int iy = Math.max(0, Math.min(h - 1, (int) Math.floor(fy)));
                        int c = px[iy * w + ix];
                        int ca = c >>> 24;
                        a += ca;
                        r += ((c >> 16) & 255) * ca;
                        g += ((c >> 8) & 255) * ca;
                        b += (c & 255) * ca;
                    }
                }
                if (a == 0) { out.px[oy * ow + ox] = 0; continue; }
                out.px[oy * ow + ox] = argb((int) (a / (n * n)), (int) (r / a), (int) (g / a), (int) (b / a));
            }
        }
        return out;
    }

    public Raster resize(int ow, int oh) { return sample(0, 0, w, h, ow, oh); }

    public void copyRegion(Raster src, int sx, int sy, int cw, int ch, int dx, int dy) {
        for (int y = 0; y < ch; y++) {
            for (int x = 0; x < cw; x++) {
                int tx = dx + x, ty = dy + y, ux = sx + x, uy = sy + y;
                if (tx < 0 || ty < 0 || tx >= w || ty >= h || ux < 0 || uy < 0 || ux >= src.w || uy >= src.h) continue;
                px[ty * w + tx] = src.px[uy * src.w + ux];
            }
        }
    }
}
