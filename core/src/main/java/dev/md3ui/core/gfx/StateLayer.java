package dev.md3ui.core.gfx;

import dev.md3ui.core.color.Argb;
import dev.md3ui.core.motion.Motion;
import dev.md3ui.core.motion.Spring;
import dev.md3ui.core.theme.Md3Tokens;

/**
 * Per-widget interaction state: the hover/focus/press overlay plus ripple.
 *
 * <p>Getting this right matters more to "does it feel like Material" than the
 * palette does. The overlay is {@code onX} colour at the spec opacity, animated
 * with a non-overshooting effect spring; the ripple is a circle that grows from
 * the pointer and fades, clipped to the widget's own shape by the caller.
 */
public final class StateLayer {

    private final Spring overlay = Motion.EFFECT_FAST.instance(0);
    private final Spring rippleGrow = Motion.SPATIAL_DEFAULT.instance(0);

    private boolean hovered;
    private boolean focused;
    private boolean pressed;
    private boolean selected;

    private float rippleX, rippleY;
    private float rippleAlpha;
    private boolean rippleActive;

    /** Feed the current interaction flags; call once per frame before drawing. */
    public void update(boolean hovered, boolean focused, boolean pressed, boolean selected,
                       double dtSeconds) {
        this.hovered = hovered;
        this.focused = focused;
        this.pressed = pressed;
        this.selected = selected;

        float goal = 0f;
        if (pressed) goal = Md3Tokens.STATE_PRESS;
        else if (focused) goal = Md3Tokens.STATE_FOCUS;
        else if (hovered) goal = Md3Tokens.STATE_HOVER;
        if (selected) goal = Math.max(goal, Md3Tokens.STATE_SELECTED);

        overlay.target(goal);
        overlay.advance(dtSeconds);

        if (rippleActive) {
            rippleGrow.advance(dtSeconds);
            // Fade the ripple out over its growth, so a click reads as one gesture.
            rippleAlpha = Math.max(0f, 1f - (float) rippleGrow.value());
            if (rippleGrow.settled()) {
                rippleActive = false;
                rippleAlpha = 0f;
            }
        }
    }

    /** Start a ripple at widget-local coordinates. */
    public void ripple(float localX, float localY) {
        rippleX = localX;
        rippleY = localY;
        rippleGrow.snapTo(0);
        rippleGrow.target(1);
        rippleAlpha = 1f;
        rippleActive = true;
    }

    /** Current overlay opacity, animated. */
    public float overlayAlpha() { return (float) overlay.value(); }

    public boolean rippleActive() { return rippleActive && rippleAlpha > 0.01f; }

    /**
     * Draw the overlay and ripple for a rounded-rect widget.
     *
     * @param contentColor the {@code onX} role for the container being covered
     */
    public void draw(Md3Canvas c, float x, float y, float w, float h, float radius,
                     int contentColor, boolean reducedMotion) {
        float a = overlayAlpha();
        if (a > 0.002f) {
            Shapes.roundRect(c, x, y, w, h, radius,
                    Argb.withAlpha(contentColor, Math.round(a * 255)));
        }
        if (reducedMotion || !rippleActive()) return;

        // Ripple radius reaches the far corner of the widget.
        float far = (float) Math.hypot(Math.max(rippleX, w - rippleX),
                Math.max(rippleY, h - rippleY));
        float r = far * (float) rippleGrow.value();
        int col = Argb.withAlpha(contentColor, Math.round(rippleAlpha * 0.10f * 255));
        c.pushClip(x, y, w, h);
        Shapes.circle(c, x + rippleX, y + rippleY, r, col);
        c.popClip();
    }

    public boolean hovered() { return hovered; }
    public boolean focused() { return focused; }
    public boolean pressed() { return pressed; }
    public boolean selected() { return selected; }

    /** Flatten a state layer onto a container colour, for text contrast checks. */
    public static int flatten(int container, int contentColor, float alpha) {
        return Argb.over(Argb.withAlpha(contentColor, Math.round(alpha * 255)),
                Argb.opaque(container));
    }
}
