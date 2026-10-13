package dev.customcape.core;

/**
 * Custom image with a user-chosen crop, like picking a profile picture.
 * (cx,cy) is the centre of the crop in source-relative coordinates (0..1); zoom 1 = the largest crop of the
 * face's aspect ratio that fits in the image, higher values zoom in.
 */
public class ImageSpec {
    public String file = null;
    public float cx = 0.5f, cy = 0.5f, zoom = 1f;
    public boolean flipH = false;

    /** @return {x, y, w, h} of the crop rectangle in source pixels */
    public float[] cropRect(int sw, int sh, float aspect) {
        float bw, bh;
        if ((float) sw / sh > aspect) { bh = sh; bw = sh * aspect; } else { bw = sw; bh = sw / aspect; }
        float z = Math.max(1f, zoom);
        float w = bw / z, h = bh / z;
        float x = Math.max(0f, Math.min(sw - w, cx * sw - w / 2f));
        float y = Math.max(0f, Math.min(sh - h, cy * sh - h / 2f));
        return new float[]{x, y, w, h};
    }
}
