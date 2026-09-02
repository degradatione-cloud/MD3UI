package dev.md3ui.core.color;

/** Packed ARGB integer helpers. All colors in MD3UI are 0xAARRGGBB ints. */
public final class Argb {
    private Argb() {}

    public static int of(int a, int r, int g, int b) {
        return ((a & 0xFF) << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
    }

    public static int rgb(int r, int g, int b) { return of(255, r, g, b); }

    public static int a(int c) { return (c >>> 24) & 0xFF; }
    public static int r(int c) { return (c >> 16) & 0xFF; }
    public static int g(int c) { return (c >> 8) & 0xFF; }
    public static int b(int c) { return c & 0xFF; }

    public static int withAlpha(int c, int alpha) {
        return (c & 0x00FFFFFF) | ((clamp255(alpha)) << 24);
    }

    /** Multiply existing alpha by {@code f} (0..1). */
    public static int scaleAlpha(int c, float f) {
        return withAlpha(c, Math.round(a(c) * clamp01(f)));
    }

    public static int opaque(int c) { return c | 0xFF000000; }

    /**
     * Source-over composite of {@code src} onto opaque {@code dst}.
     * Used by the raster backend and by state-layer flattening.
     */
    public static int over(int src, int dst) {
        int sa = a(src);
        if (sa == 255) return src;
        if (sa == 0) return dst;
        int da = a(dst);
        int outA = sa + da * (255 - sa) / 255;
        if (outA == 0) return 0;
        int rr = (r(src) * sa + r(dst) * da * (255 - sa) / 255) / outA;
        int gg = (g(src) * sa + g(dst) * da * (255 - sa) / 255) / outA;
        int bb = (b(src) * sa + b(dst) * da * (255 - sa) / 255) / outA;
        return of(outA, rr, gg, bb);
    }

    /** Linear interpolation in sRGB space. {@code t} is clamped to 0..1. */
    public static int lerp(int from, int to, float t) {
        float f = clamp01(t);
        return of(
                Math.round(a(from) + (a(to) - a(from)) * f),
                Math.round(r(from) + (r(to) - r(from)) * f),
                Math.round(g(from) + (g(to) - g(from)) * f),
                Math.round(b(from) + (b(to) - b(from)) * f));
    }

    /** Relative luminance per WCAG 2.1. */
    public static double luminance(int c) {
        return 0.2126 * lin(r(c)) + 0.7152 * lin(g(c)) + 0.0722 * lin(b(c));
    }

    private static double lin(int v) {
        double s = v / 255.0;
        return s <= 0.03928 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
    }

    /** WCAG contrast ratio between two opaque colors (1.0 .. 21.0). */
    public static double contrast(int a, int b) {
        double la = luminance(a), lb = luminance(b);
        double hi = Math.max(la, lb), lo = Math.min(la, lb);
        return (hi + 0.05) / (lo + 0.05);
    }

    public static int clamp255(int v) { return v < 0 ? 0 : Math.min(v, 255); }
    public static float clamp01(float v) { return v < 0f ? 0f : Math.min(v, 1f); }

    public static String hex(int c) {
        return String.format("#%02X%02X%02X", r(c), g(c), b(c));
    }
}
