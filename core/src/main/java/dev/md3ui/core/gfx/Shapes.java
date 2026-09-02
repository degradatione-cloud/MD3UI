package dev.md3ui.core.gfx;

import dev.md3ui.core.color.Argb;

/**
 * Decomposes MD3 shapes into axis-aligned quads.
 *
 * <p>Everything here is a scanline solver: for each row of pixels, work out the
 * horizontal span that is inside the shape and emit one quad for it. That keeps
 * the whole library on {@code fillRect}, which is the compatibility guarantee
 * described in {@link Md3Canvas} &mdash; no shaders, no custom vertex formats,
 * nothing for VulkanMod to disagree with.
 *
 * <p>Cost is one quad per scanline of a corner, not per shape. A 28px-radius
 * dialog corner is 28 quads; a full dialog is ~120. For reference, vanilla's own
 * widget rendering already issues a comparable number of blits per screen, and
 * {@link #roundRect} results are cached by {@link ShapeCache} keyed on
 * size+radius, so a static screen re-emits from a prebuilt span table.
 */
public final class Shapes {
    private Shapes() {}

    /** Antialias coverage steps. 1 = hard edge, 4 = smooth enough at GUI scale 2. */
    public static final int AA_STEPS = 4;

    /**
     * Rounded rectangle with independent corner radii.
     *
     * <p>Radii are clamped so opposite corners cannot overlap, matching CSS
     * behaviour and preventing the pinched look when a caller asks for a radius
     * larger than half the box.
     */
    public static void roundRect(Md3Canvas c, float x, float y, float w, float h,
                                 float tl, float tr, float br, float bl, int argb) {
        if (w <= 0 || h <= 0 || Argb.a(argb) == 0) return;
        float max = Math.min(w, h) / 2f;
        tl = clamp(tl, max); tr = clamp(tr, max); br = clamp(br, max); bl = clamp(bl, max);

        float top = Math.max(tl, tr);
        float bottom = Math.max(bl, br);

        // Middle band: full width, no corner influence.
        float midY = y + top;
        float midH = h - top - bottom;
        if (midH > 0) c.fillRect(x, midY, w, midH, argb);

        // Top band, scanline by scanline.
        emitCornerBand(c, x, y, w, h, top, tl, tr, argb, true);
        // Bottom band.
        emitCornerBand(c, x, y, w, h, bottom, bl, br, argb, false);
    }

    /** Uniform-radius convenience overload. */
    public static void roundRect(Md3Canvas c, float x, float y, float w, float h,
                                 float radius, int argb) {
        roundRect(c, x, y, w, h, radius, radius, radius, radius, argb);
    }

    /** Fully rounded "pill" / stadium shape used by buttons, chips and badges. */
    public static void pill(Md3Canvas c, float x, float y, float w, float h, int argb) {
        roundRect(c, x, y, w, h, h / 2f, argb);
    }

    private static void emitCornerBand(Md3Canvas c, float x, float y, float w, float h,
                                       float band, float rLeft, float rRight,
                                       int argb, boolean isTop) {
        if (band <= 0) return;
        int rows = (int) Math.ceil(band);
        for (int i = 0; i < rows; i++) {
            // Distance from the outer edge of this band, sampled at pixel centre.
            float d = i + 0.5f;
            float rowY = isTop ? y + i : y + h - i - 1;
            float rowH = 1f;

            float insetL = cornerInset(rLeft, d);
            float insetR = cornerInset(rRight, d);

            float sx = x + insetL;
            float sw = w - insetL - insetR;
            if (sw <= 0) continue;

            // Coverage-based alpha for the partially covered outermost rows.
            float cov = coverage(rLeft, rRight, d);
            int col = cov >= 1f ? argb : Argb.scaleAlpha(argb, cov);
            c.fillRect(sx, rowY, sw, rowH, col);
        }
    }

    /**
     * Horizontal inset of a circular corner of radius {@code r} at depth
     * {@code d} from the outer edge.
     */
    private static float cornerInset(float r, float d) {
        if (r <= 0 || d >= r) return 0;
        float dy = r - d;
        float dx = (float) Math.sqrt(Math.max(0, r * r - dy * dy));
        return r - dx;
    }

    /**
     * Approximate coverage of the outermost scanline so corners do not look
     * stair-stepped. Only the first row of a corner is fractional in practice.
     */
    private static float coverage(float rLeft, float rRight, float d) {
        float r = Math.max(rLeft, rRight);
        if (r <= 0) return 1f;
        if (d > 1f) return 1f;
        // Sub-sample the row vertically.
        int hits = 0;
        for (int s = 0; s < AA_STEPS; s++) {
            float sd = d - 0.5f + (s + 0.5f) / AA_STEPS;
            if (sd >= 0) hits++;
        }
        return Math.max(0.35f, hits / (float) AA_STEPS);
    }

    /** Stroked rounded rectangle, drawn as outer minus inner. */
    public static void roundRectOutline(Md3Canvas c, float x, float y, float w, float h,
                                        float radius, float stroke, int argb) {
        if (stroke <= 0) return;
        // Four edge strips plus corner arcs; cheaper and cleaner than XOR fills.
        float r = Math.min(radius, Math.min(w, h) / 2f);
        // Top and bottom straight runs.
        c.fillRect(x + r, y, w - 2 * r, stroke, argb);
        c.fillRect(x + r, y + h - stroke, w - 2 * r, stroke, argb);
        // Left and right straight runs.
        c.fillRect(x, y + r, stroke, h - 2 * r, argb);
        c.fillRect(x + w - stroke, y + r, stroke, h - 2 * r, argb);
        // Corner arcs.
        arc(c, x + r, y + r, r, stroke, 180, 270, argb);
        arc(c, x + w - r, y + r, r, stroke, 270, 360, argb);
        arc(c, x + w - r, y + h - r, r, stroke, 0, 90, argb);
        arc(c, x + r, y + h - r, r, stroke, 90, 180, argb);
    }

    /** Stroked arc approximated with short quads along the sweep. */
    public static void arc(Md3Canvas c, float cx, float cy, float r, float stroke,
                          float fromDeg, float toDeg, int argb) {
        if (r <= 0) return;
        int steps = Math.max(4, (int) (r * 2));
        double a0 = Math.toRadians(fromDeg), a1 = Math.toRadians(toDeg);
        for (int i = 0; i < steps; i++) {
            double t = a0 + (a1 - a0) * (i + 0.5) / steps;
            float px = cx + (float) Math.cos(t) * (r - stroke / 2f);
            float py = cy + (float) Math.sin(t) * (r - stroke / 2f);
            c.fillRect(px - stroke / 2f, py - stroke / 2f, stroke, stroke, argb);
        }
    }

    /** Filled circle, used by radio buttons, ripples and the loading indicator. */
    public static void circle(Md3Canvas c, float cx, float cy, float r, int argb) {
        if (r <= 0) return;
        int rows = (int) Math.ceil(r * 2);
        for (int i = 0; i < rows; i++) {
            float py = cy - r + i;
            float dy = (py + 0.5f) - cy;
            float halfW = (float) Math.sqrt(Math.max(0, r * r - dy * dy));
            if (halfW <= 0) continue;
            c.fillRect(cx - halfW, py, halfW * 2, 1f, argb);
        }
    }

    /** Ring / donut: outer circle minus inner radius. Progress indicators use it. */
    public static void ring(Md3Canvas c, float cx, float cy, float rOuter, float thickness,
                            int argb) {
        float rInner = Math.max(0, rOuter - thickness);
        int rows = (int) Math.ceil(rOuter * 2);
        for (int i = 0; i < rows; i++) {
            float py = cy - rOuter + i;
            float dy = (py + 0.5f) - cy;
            float outer = (float) Math.sqrt(Math.max(0, rOuter * rOuter - dy * dy));
            if (outer <= 0) continue;
            float inner = rInner > Math.abs(dy)
                    ? (float) Math.sqrt(Math.max(0, rInner * rInner - dy * dy)) : 0;
            if (inner <= 0) {
                c.fillRect(cx - outer, py, outer * 2, 1f, argb);
            } else {
                c.fillRect(cx - outer, py, outer - inner, 1f, argb);
                c.fillRect(cx + inner, py, outer - inner, 1f, argb);
            }
        }
    }

    /**
     * MD3 Expressive "cookie" / squircle superellipse.
     *
     * <p>Expressive shapes are not circular arcs; they are superellipses with
     * n &asymp; 3.6, which is what gives the softer, less mechanical silhouette.
     * Buttons in the Expressive spec morph between this and a pill.
     */
    public static void squircle(Md3Canvas c, float x, float y, float w, float h,
                               float n, int argb) {
        float a = w / 2f, b = h / 2f;
        float cx = x + a, cy = y + b;
        int rows = (int) Math.ceil(h);
        double invN = 1.0 / n;
        for (int i = 0; i < rows; i++) {
            float py = y + i;
            float dy = Math.abs((py + 0.5f) - cy) / b;
            if (dy > 1) continue;
            double inner = 1.0 - Math.pow(dy, n);
            if (inner <= 0) continue;
            float dx = (float) Math.pow(inner, invN) * a;
            c.fillRect(cx - dx, py, dx * 2, 1f, argb);
        }
    }

    /**
     * Tonal "shadow" for genuinely floating surfaces (FAB, dialog, snackbar).
     * MD3 expresses resting elevation as tone; this is only for the few
     * components the spec still gives a real shadow.
     */
    public static void softShadow(Md3Canvas c, float x, float y, float w, float h,
                                  float radius, float spread, int shadowArgb) {
        int layers = Math.max(1, (int) spread);
        for (int i = layers; i >= 1; i--) {
            float t = i / (float) layers;
            float grow = t * spread;
            int alpha = Math.round(Argb.a(shadowArgb) * (1f - t) * 0.5f);
            if (alpha <= 0) continue;
            roundRect(c, x - grow, y - grow + spread * 0.35f, w + grow * 2, h + grow * 2,
                    radius + grow, Argb.withAlpha(shadowArgb, alpha));
        }
    }

    private static float clamp(float v, float max) {
        return v < 0 ? 0 : Math.min(v, max);
    }
}
