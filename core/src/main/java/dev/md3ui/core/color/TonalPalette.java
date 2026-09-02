package dev.md3ui.core.color;

/**
 * A Material tonal palette: one hue/chroma pair sampled at 13 tone stops.
 *
 * <p>Google's reference implementation resolves tones in HCT (CAM16 hue/chroma
 * over L*). MD3UI uses CIELAB LCh instead: hue and chroma are held constant
 * while L* is swept. Perceptual lightness (L*) is identical to HCT's tone
 * dimension, so tone stops land on the same luminance ladder; only chroma
 * behaviour differs slightly in the deep-saturation corners. This keeps the
 * whole solver at ~120 lines with no lookup tables, which matters because it
 * runs on the client thread when a user retunes their theme.
 *
 * <p>Chroma is reduced automatically when a requested (L*, C, h) triple falls
 * outside the sRGB gamut, matching HCT's behaviour of preserving hue and tone
 * over chroma.
 */
public final class TonalPalette {

    private final double hue;      // LCh hue angle, degrees
    private final double chroma;   // requested chroma
    private final int[] cache = new int[101];
    private final boolean[] cached = new boolean[101];

    private TonalPalette(double hue, double chroma) {
        this.hue = hue;
        this.chroma = chroma;
    }

    public static TonalPalette of(double hue, double chroma) {
        return new TonalPalette(((hue % 360) + 360) % 360, Math.max(0, chroma));
    }

    /** Derive a palette from a seed color, keeping its hue and chroma. */
    public static TonalPalette fromColor(int argb) {
        double[] lch = Cie.rgbToLch(argb);
        return of(lch[2], lch[1]);
    }

    /** Derive a palette from a seed hue but force a chroma (for neutrals etc.). */
    public static TonalPalette fromColorWithChroma(int argb, double forcedChroma) {
        double[] lch = Cie.rgbToLch(argb);
        return of(lch[2], forcedChroma);
    }

    public double hue() { return hue; }
    public double chroma() { return chroma; }

    /** Opaque sRGB color at the given tone (L*, 0..100). */
    public int tone(int t) {
        int tt = t < 0 ? 0 : Math.min(t, 100);
        if (cached[tt]) return cache[tt];
        int c = solve(tt);
        cache[tt] = c;
        cached[tt] = true;
        return c;
    }

    /**
     * Find the most chromatic in-gamut color at this tone, never exceeding the
     * palette's chroma. Binary search on chroma: monotone in-gamut predicate.
     */
    private int solve(int t) {
        if (chroma <= 0.1) return Cie.labToRgbClamped(t, 0, 0);
        double lo = 0, hi = chroma;
        int best = Cie.labToRgbClamped(t, 0, 0);
        for (int i = 0; i < 24 && hi - lo > 0.05; i++) {
            double mid = (lo + hi) / 2;
            double[] ab = Cie.chToAb(mid, hue);
            int candidate = Cie.labToRgb(t, ab[0], ab[1]);
            if (candidate != -1) {
                best = candidate;
                lo = mid;
            } else {
                hi = mid;
            }
        }
        return best;
    }

    @Override
    public String toString() {
        return String.format("TonalPalette(h=%.1f, c=%.1f)", hue, chroma);
    }
}
