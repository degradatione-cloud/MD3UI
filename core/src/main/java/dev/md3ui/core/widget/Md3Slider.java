package dev.md3ui.core.widget;

import dev.md3ui.core.color.Argb;
import dev.md3ui.core.gfx.Md3Canvas;
import dev.md3ui.core.gfx.Shapes;
import dev.md3ui.core.motion.Motion;
import dev.md3ui.core.motion.Spring;
import dev.md3ui.core.theme.Md3Scheme;
import dev.md3ui.core.theme.Md3Tokens;

/**
 * MD3 Expressive slider, which is what every vanilla options slider becomes.
 *
 * <p>Two Expressive details are load-bearing here. The handle is a rounded
 * <em>bar</em> rather than a circle, and it narrows while dragged as the track
 * thickens &mdash; that inversion is the current spec's slider signature. Stop
 * indicators sit at the ends of the active track. Discrete sliders also get tick
 * marks, and the value label rides above the handle while dragging.
 */
public final class Md3Slider extends Md3Widget {

    private double value;          // normalised 0..1
    private final double min, max;
    private final double step;     // 0 = continuous
    private String label;
    private java.util.function.DoubleConsumer onChange = v -> {};
    private java.util.function.DoubleFunction<String> formatter;

    private boolean dragging;
    private final Spring handleSquash = Motion.SPATIAL_FAST.instance(0);
    private final Spring visualValue = Motion.SPATIAL_FAST.instance(0);

    public Md3Slider(float x, float y, float w, double min, double max, double value,
                     double step) {
        super(x, y, w, Md3Tokens.SLIDER_TRACK + 8f);
        this.min = min;
        this.max = max;
        this.step = Math.max(0, step);
        this.value = normalise(value);
        visualValue.snapTo(this.value);
    }

    public Md3Slider label(String l) { this.label = l; return this; }

    public Md3Slider onChange(java.util.function.DoubleConsumer c) {
        this.onChange = c == null ? v -> {} : c;
        return this;
    }

    public Md3Slider format(java.util.function.DoubleFunction<String> f) {
        this.formatter = f;
        return this;
    }

    public double realValue() { return min + (max - min) * value; }

    public void setRealValue(double v) {
        value = normalise(v);
        visualValue.target(value);
    }

    private double normalise(double real) {
        double t = (real - min) / (max - min);
        return quantise(Math.max(0, Math.min(1, t)));
    }

    private double quantise(double t) {
        if (step <= 0) return t;
        double range = max - min;
        double steps = range / step;
        return Math.round(t * steps) / steps;
    }

    @Override
    protected void onTick(double dt) {
        handleSquash.target(dragging ? 1 : 0);
        handleSquash.advance(dt);
        visualValue.advance(dt);
    }

    @Override
    protected boolean onClick(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        dragging = true;
        applyFromMouse(mouseX);
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button,
                                double dragX, double dragY) {
        if (!dragging || !enabled) return false;
        applyFromMouse(mouseX);
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (!dragging) return false;
        dragging = false;
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!enabled || !focused) return false;
        double delta = step > 0 ? step / (max - min) : 0.02;
        // GLFW left / right.
        if (keyCode == 263) { setNormalised(value - delta); return true; }
        if (keyCode == 262) { setNormalised(value + delta); return true; }
        return false;
    }

    private void setNormalised(double t) {
        double nv = quantise(Math.max(0, Math.min(1, t)));
        if (nv != value) {
            value = nv;
            visualValue.target(value);
            onChange.accept(realValue());
        }
    }

    private void applyFromMouse(double mouseX) {
        float trackX = x;
        float trackW = width;
        double t = (mouseX - trackX) / trackW;
        setNormalised(t);
        // While dragging the handle should track the pointer exactly, no lag.
        visualValue.snapTo(value);
    }

    @Override
    public void render(Md3Canvas c, Md3Scheme s) {
        if (!visible) return;

        float squash = reducedMotion ? 0f : (float) handleSquash.value();
        float v = reducedMotion ? (float) value : (float) visualValue.value();

        // Track thickens while dragging; handle narrows. Expressive inversion.
        float trackH = Motion.lerp(Md3Tokens.SLIDER_TRACK, Md3Tokens.SLIDER_TRACK + 3f, squash);
        float handleW = Motion.lerp(4f, 2f, squash);
        float handleH = Motion.lerp(trackH + 6f, trackH + 10f, squash);

        float trackY = y + (height - trackH) / 2f;
        float cy = y + height / 2f;

        int activeColor = enabled ? s.primary
                : Argb.withAlpha(s.onSurface, Math.round(Md3Tokens.DISABLED_CONTENT * 255));
        int inactiveColor = enabled ? s.secondaryContainer
                : Argb.withAlpha(s.onSurface, Math.round(Md3Tokens.DISABLED_CONTAINER * 255));

        float handleX = x + (width - handleW) * v;
        float gap = 3f;

        // Inactive track sits to the right of the handle, with an MD3 gap.
        float inactiveStart = handleX + handleW + gap;
        if (inactiveStart < x + width) {
            Shapes.pill(c, inactiveStart, trackY, x + width - inactiveStart, trackH,
                    inactiveColor);
        }
        // Active track to the left.
        float activeW = handleX - gap - x;
        if (activeW > 0) {
            Shapes.pill(c, x, trackY, activeW, trackH, activeColor);
        }

        // Stop indicator at the far end of the inactive track.
        if (v < 0.97f) {
            Shapes.circle(c, x + width - trackH / 2f, cy, 1.2f,
                    enabled ? s.onSecondaryContainer : inactiveColor);
        }
        // Stop indicator inside the active track near the start.
        if (v > 0.05f) {
            Shapes.circle(c, x + trackH / 2f, cy, 1.2f, s.onPrimary);
        }

        // Tick marks for discrete sliders with a sane number of steps.
        if (step > 0) {
            int count = (int) Math.round((max - min) / step);
            if (count > 1 && count <= 24) {
                for (int i = 0; i <= count; i++) {
                    float t = i / (float) count;
                    float tx = x + (width - handleW) * t + handleW / 2f;
                    boolean active = t <= v;
                    if (Math.abs(tx - (handleX + handleW / 2f)) < 3f) continue;
                    Shapes.circle(c, tx, cy, 0.9f,
                            active ? s.onPrimary : s.onSecondaryContainer);
                }
            }
        }

        // Handle: rounded bar, focus halo behind it.
        float sa = state.overlayAlpha();
        if (sa > 0.002f && enabled) {
            Shapes.circle(c, handleX + handleW / 2f, cy, handleH / 2f + 5f,
                    Argb.withAlpha(s.primary, Math.round(sa * 255)));
        }
        Shapes.pill(c, handleX, cy - handleH / 2f, handleW, handleH,
                enabled ? s.primary : activeColor);

        // Label above the track; value follows the handle while dragging.
        if (label != null) {
            int textColor = enabled ? s.onSurface
                    : Argb.withAlpha(s.onSurface, Math.round(Md3Tokens.DISABLED_CONTENT * 255));
            String text = formatter != null ? label + ": " + formatter.apply(realValue())
                    : label;
            c.drawText(text, x, y - c.lineHeight() - 1f, textColor, false);
        }
    }

    /** Height including the label line, for layout code. */
    public float totalHeight(Md3Canvas c) {
        return height + (label != null ? c.lineHeight() + 1f : 0);
    }
}
