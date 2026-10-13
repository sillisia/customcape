package dev.customcape.core;

/** The 16 banner dye colours (same RGB values the game uses for banner tinting). */
public enum Dye {
    WHITE(0xF9FFFE), ORANGE(0xF9801D), MAGENTA(0xC74EBD), LIGHT_BLUE(0x3AB3DA),
    YELLOW(0xFED83D), LIME(0x80C71F), PINK(0xF38BAA), GRAY(0x474F52),
    LIGHT_GRAY(0x9D9D97), CYAN(0x169C9C), PURPLE(0x8932B8), BLUE(0x3C44AA),
    BROWN(0x835432), GREEN(0x5E7C16), RED(0xB02E26), BLACK(0x1D1D21);

    public final int rgb;

    Dye(int rgb) { this.rgb = rgb; }

    public Dye shift(int d) {
        Dye[] v = values();
        return v[Math.floorMod(ordinal() + d, v.length)];
    }

    public String pretty() {
        String s = name().toLowerCase().replace('_', ' ');
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
