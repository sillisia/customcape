package dev.customcape.core;

import java.util.ArrayList;
import java.util.List;

/** An OptiFine-style banner cape: a base dye plus any number of pattern layers. */
public class BannerSpec {
    public static class Layer {
        public String pattern = "stripe_center";
        public Dye color = Dye.WHITE;
        public Layer() {}
        public Layer(String pattern, Dye color) { this.pattern = pattern; this.color = color; }
    }

    /** Vanilla banner pattern ids == file names in assets/minecraft/textures/entity/banner/. */
    public static final List<String> PATTERNS = List.of(
        "border", "bricks", "circle", "creeper", "cross", "curly_border", "diagonal_left", "diagonal_right",
        "diagonal_up_left", "diagonal_up_right", "flower", "gradient", "gradient_up", "half_horizontal",
        "half_horizontal_bottom", "half_vertical", "half_vertical_right", "mojang", "rhombus", "skull",
        "small_stripes", "square_bottom_left", "square_bottom_right", "square_top_left", "square_top_right",
        "straight_cross", "stripe_bottom", "stripe_center", "stripe_downleft", "stripe_downright", "stripe_left",
        "stripe_middle", "stripe_right", "stripe_top", "triangle_bottom", "triangle_top", "triangles_bottom",
        "triangles_top", "globe", "piglin", "flow", "guster");

    public Dye base = Dye.RED;
    public List<Layer> layers = new ArrayList<>();
}
