package dev.customcape.core;

/** One side of a cape or elytra. All three editors keep their data so switching type never loses work. */
public class FaceSpec {
    public enum Type { GRADIENT, BANNER, IMAGE }

    public Type type = Type.GRADIENT;
    public GradientSpec gradient = new GradientSpec();
    public BannerSpec banner = new BannerSpec();
    public ImageSpec image = new ImageSpec();

    public void normalize() {
        if (type == null) type = Type.GRADIENT;
        if (gradient == null) gradient = new GradientSpec();
        if (gradient.stops == null || gradient.stops.isEmpty()) gradient = new GradientSpec();
        if (banner == null) banner = new BannerSpec();
        if (banner.layers == null) banner.layers = new java.util.ArrayList<>();
        if (banner.base == null) banner.base = Dye.RED;
        banner.layers.removeIf(l -> l == null);
        for (BannerSpec.Layer l : banner.layers) { if (l.color == null) l.color = Dye.WHITE; if (l.pattern == null) l.pattern = "stripe_center"; }
        if (image == null) image = new ImageSpec();
    }
}
