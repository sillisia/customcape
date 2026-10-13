package dev.customcape.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Any number of colour stops. The gradient runs from (startX,startY) to (endX,endY), both in
 * face-relative coordinates (0..1, origin top-left), so the user decides exactly where it begins and ends.
 * Stop positions are 0..1 along that line.
 */
public class GradientSpec {
    public static class Stop {
        public float pos;
        public int rgb;
        public Stop() {}
        public Stop(float pos, int rgb) { this.pos = pos; this.rgb = rgb & 0xFFFFFF; }
    }

    public List<Stop> stops = new ArrayList<>(List.of(new Stop(0f, 0x8A2BE2), new Stop(1f, 0x1E90FF)));
    public float startX = 0.5f, startY = 0f, endX = 0.5f, endY = 1f;
    public boolean radial = false;

    public int colorAt(float x, float y) {
        if (stops == null || stops.isEmpty()) return 0xFF000000;
        float vx = endX - startX, vy = endY - startY;
        float len2 = vx * vx + vy * vy;
        float t;
        if (len2 < 1e-6f) t = 0f;
        else if (radial) t = (float) Math.sqrt(((x - startX) * (x - startX) + (y - startY) * (y - startY)) / len2);
        else t = ((x - startX) * vx + (y - startY) * vy) / len2;
        t = Math.max(0f, Math.min(1f, t));

        List<Stop> s = new ArrayList<>(stops);
        s.sort(Comparator.comparingDouble(a -> a.pos));
        if (t <= s.get(0).pos) return 0xFF000000 | s.get(0).rgb;
        for (int i = 1; i < s.size(); i++) {
            Stop a = s.get(i - 1), b = s.get(i);
            if (t <= b.pos) {
                float span = b.pos - a.pos;
                float k = span < 1e-6f ? 1f : (t - a.pos) / span;
                return 0xFF000000 | lerp(a.rgb, b.rgb, k);
            }
        }
        return 0xFF000000 | s.get(s.size() - 1).rgb;
    }

    private static int lerp(int c1, int c2, float k) {
        int r = Math.round(((c1 >> 16) & 255) * (1 - k) + ((c2 >> 16) & 255) * k);
        int g = Math.round(((c1 >> 8) & 255) * (1 - k) + ((c2 >> 8) & 255) * k);
        int b = Math.round((c1 & 255) * (1 - k) + (c2 & 255) * k);
        return (r << 16) | (g << 8) | b;
    }
}
