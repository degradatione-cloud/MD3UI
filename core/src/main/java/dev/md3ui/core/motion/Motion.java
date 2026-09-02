package dev.md3ui.core.motion;

/**
 * MD3 Expressive motion: spring physics plus the legacy easing set.
 *
 * <p>Material 3 Expressive replaced most duration+easing pairs with
 * <em>spatial springs</em>. The practical difference is that a spring is
 * interruptible and retargetable mid-flight without a visible seam, which is
 * what makes Expressive UIs feel physical instead of scheduled. Effects that
 * must not overshoot (colour, alpha, elevation) keep non-bouncy springs.
 */
public final class Motion {
    private Motion() {}

    /** Spatial springs: allowed to overshoot. Used for position, size, shape. */
    public static final Spring SPATIAL_FAST = Spring.of(0.9, 1400);
    public static final Spring SPATIAL_DEFAULT = Spring.of(0.9, 700);
    public static final Spring SPATIAL_SLOW = Spring.of(0.9, 300);

    /** Expressive springs: lower damping, visible bounce. The signature feel. */
    public static final Spring EXPRESSIVE_FAST = Spring.of(0.6, 800);
    public static final Spring EXPRESSIVE_DEFAULT = Spring.of(0.6, 380);
    public static final Spring EXPRESSIVE_SLOW = Spring.of(0.8, 200);

    /** Effect springs: critically damped, never overshoot. Colour and alpha. */
    public static final Spring EFFECT_FAST = Spring.of(1.0, 3800);
    public static final Spring EFFECT_DEFAULT = Spring.of(1.0, 1600);
    public static final Spring EFFECT_SLOW = Spring.of(1.0, 800);

    // --- Legacy MD3 easing, still used for simple cross-fades ---

    public static float emphasized(float t) {
        return cubicBezier(t, 0.05f, 0.7f, 0.1f, 1.0f);
    }

    public static float emphasizedDecelerate(float t) {
        return cubicBezier(t, 0.05f, 0.7f, 0.1f, 1.0f);
    }

    public static float emphasizedAccelerate(float t) {
        return cubicBezier(t, 0.3f, 0.0f, 0.8f, 0.15f);
    }

    public static float standard(float t) {
        return cubicBezier(t, 0.2f, 0.0f, 0.0f, 1.0f);
    }

    public static float standardDecelerate(float t) {
        return cubicBezier(t, 0.0f, 0.0f, 0.0f, 1.0f);
    }

    public static float linear(float t) { return clamp(t); }

    /** Overshooting ease for playful one-shots (badge pop, star burst). */
    public static float backOut(float t) {
        float x = clamp(t) - 1f;
        float s = 1.70158f;
        return x * x * ((s + 1) * x + s) + 1f;
    }

    /**
     * Cubic bezier with fixed endpoints (0,0)..(1,1), solved by Newton with a
     * bisection fallback. Same approach as CSS engines.
     */
    public static float cubicBezier(float t, float x1, float y1, float x2, float y2) {
        float x = clamp(t);
        if (x <= 0) return 0;
        if (x >= 1) return 1;
        float guess = x;
        for (int i = 0; i < 8; i++) {
            float cx = bez(guess, x1, x2) - x;
            if (Math.abs(cx) < 1e-5f) return bez(guess, y1, y2);
            float d = bezDeriv(guess, x1, x2);
            if (Math.abs(d) < 1e-6f) break;
            guess -= cx / d;
        }
        float lo = 0, hi = 1;
        guess = x;
        for (int i = 0; i < 20; i++) {
            float cx = bez(guess, x1, x2);
            if (Math.abs(cx - x) < 1e-5f) break;
            if (cx < x) lo = guess; else hi = guess;
            guess = (lo + hi) / 2;
        }
        return bez(guess, y1, y2);
    }

    private static float bez(float t, float a, float b) {
        float mt = 1 - t;
        return 3 * mt * mt * t * a + 3 * mt * t * t * b + t * t * t;
    }

    private static float bezDeriv(float t, float a, float b) {
        float mt = 1 - t;
        return 3 * mt * mt * a + 6 * mt * t * (b - a) + 3 * t * t * (1 - b);
    }

    public static float clamp(float t) { return t < 0 ? 0 : Math.min(t, 1); }

    public static float lerp(float a, float b, float t) { return a + (b - a) * clamp(t); }
}
