package dev.md3ui.core.color;

/**
 * sRGB &harr; CIE XYZ &harr; CIELAB &harr; LCh conversions.
 *
 * <p>D65 white point, sRGB transfer function. This is the numeric floor under
 * {@link TonalPalette}: tone stops are L* values, and gamut mapping is a chroma
 * search that needs an exact "is this Lab triple representable in sRGB" test.
 */
public final class Cie {
    private Cie() {}

    // D65 reference white, scaled to Y = 100.
    private static final double XN = 95.047, YN = 100.000, ZN = 108.883;
    private static final double EPS = 216.0 / 24389.0;
    private static final double KAPPA = 24389.0 / 27.0;

    /** sRGB electro-optical transfer function (gamma decode), 0..255 -> 0..100. */
    private static double toLinear(int v) {
        double s = v / 255.0;
        double lin = s <= 0.04045 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
        return lin * 100.0;
    }

    /** Gamma encode a linear 0..100 channel back to 0..255, or -1 if out of gamut. */
    private static int fromLinear(double lin) {
        double s = lin / 100.0;
        double enc = s <= 0.0031308 ? s * 12.92 : 1.055 * Math.pow(s, 1.0 / 2.4) - 0.055;
        double v = enc * 255.0;
        // Allow a sliver of rounding slack at the ends, reject real overflow.
        if (v < -0.5 || v > 255.5) return -1;
        return (int) Math.round(Math.max(0, Math.min(255, v)));
    }

    private static int clampLinear(double lin) {
        double s = lin / 100.0;
        s = Math.max(0, Math.min(1, s));
        double enc = s <= 0.0031308 ? s * 12.92 : 1.055 * Math.pow(s, 1.0 / 2.4) - 0.055;
        return (int) Math.round(Math.max(0, Math.min(255, enc * 255.0)));
    }

    public static double[] rgbToXyz(int argb) {
        double r = toLinear(Argb.r(argb));
        double g = toLinear(Argb.g(argb));
        double b = toLinear(Argb.b(argb));
        return new double[] {
                0.41233895 * r + 0.35762064 * g + 0.18051042 * b,
                0.2126 * r + 0.7152 * g + 0.0722 * b,
                0.01932141 * r + 0.11916382 * g + 0.95034478 * b
        };
    }

    /** @return {L*, a*, b*} */
    public static double[] rgbToLab(int argb) {
        double[] xyz = rgbToXyz(argb);
        double fx = f(xyz[0] / XN), fy = f(xyz[1] / YN), fz = f(xyz[2] / ZN);
        return new double[] { 116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz) };
    }

    /** @return {L*, C, h(deg)} */
    public static double[] rgbToLch(int argb) {
        double[] lab = rgbToLab(argb);
        double c = Math.hypot(lab[1], lab[2]);
        double h = Math.toDegrees(Math.atan2(lab[2], lab[1]));
        if (h < 0) h += 360;
        return new double[] { lab[0], c, h };
    }

    /** Polar chroma/hue to cartesian a-star, b-star. */
    public static double[] chToAb(double chroma, double hueDeg) {
        double rad = Math.toRadians(hueDeg);
        return new double[] { chroma * Math.cos(rad), chroma * Math.sin(rad) };
    }

    /**
     * Lab to opaque sRGB.
     *
     * @return packed ARGB, or -1 when the color is outside the sRGB gamut.
     */
    public static int labToRgb(double l, double a, double b) {
        double[] rgb = labToLinearRgb(l, a, b);
        int rr = fromLinear(rgb[0]), gg = fromLinear(rgb[1]), bb = fromLinear(rgb[2]);
        if (rr < 0 || gg < 0 || bb < 0) return -1;
        return Argb.rgb(rr, gg, bb);
    }

    /** Lab to sRGB, clamping out-of-gamut channels instead of failing. */
    public static int labToRgbClamped(double l, double a, double b) {
        double[] rgb = labToLinearRgb(l, a, b);
        return Argb.rgb(clampLinear(rgb[0]), clampLinear(rgb[1]), clampLinear(rgb[2]));
    }

    private static double[] labToLinearRgb(double l, double a, double b) {
        double fy = (l + 16) / 116;
        double fx = a / 500 + fy;
        double fz = fy - b / 200;
        double x = fInv(fx) * XN;
        double y = (l > 8 ? Math.pow(fy, 3) : l / KAPPA) * YN;
        double z = fInv(fz) * ZN;
        return new double[] {
                3.2413774792388685 * x - 1.5376652402851851 * y - 0.49885366846268053 * z,
                -0.9691452513005321 * x + 1.8758853451067872 * y + 0.04156585616912061 * z,
                0.05562093689691305 * x - 0.20395524564742123 * y + 1.0571799111220335 * z
        };
    }

    /** L* of an opaque color: the "tone" axis MD3 talks about. */
    public static double tone(int argb) {
        return rgbToLab(argb)[0];
    }

    private static double f(double t) {
        return t > EPS ? Math.cbrt(t) : (KAPPA * t + 16) / 116;
    }

    private static double fInv(double ft) {
        double c = ft * ft * ft;
        return c > EPS ? c : (116 * ft - 16) / KAPPA;
    }
}
