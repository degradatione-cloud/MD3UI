package dev.md3ui.core.widget;

import dev.md3ui.core.color.Argb;
import dev.md3ui.core.gfx.Md3Canvas;
import dev.md3ui.core.gfx.Shapes;
import dev.md3ui.core.motion.Motion;
import dev.md3ui.core.motion.Spring;
import dev.md3ui.core.theme.Md3Scheme;
import dev.md3ui.core.theme.Md3Tokens;

/**
 * MD3 switch: the replacement for vanilla's ON/OFF text button.
 *
 * <p>Three animated properties, which is what makes it read as a real switch
 * rather than a moving dot: the thumb travels on an expressive (slightly bouncy)
 * spring, the thumb radius grows when checked and grows further while pressed,
 * and the track colour cross-fades on a non-overshooting effect spring so it
 * never flashes a wrong hue mid-flight.
 */
public final class Md3Switch extends Md3Widget {

    private boolean checked;
    private java.util.function.Consumer<Boolean> onChange = v -> {};
    private String label;

    private final Spring travel = Motion.EXPRESSIVE_DEFAULT.instance(0);
    private final Spring thumbSize = Motion.SPATIAL_FAST.instance(0);
    private final Spring colorMix = Motion.EFFECT_DEFAULT.instance(0);

    public Md3Switch(float x, float y, boolean checked) {
        super(x, y, Md3Tokens.SWITCH_WIDTH, Md3Tokens.SWITCH_HEIGHT);
        this.checked = checked;
        travel.snapTo(checked ? 1 : 0);
        colorMix.snapTo(checked ? 1 : 0);
        thumbSize.snapTo(checked ? 1 : 0);
    }

    public Md3Switch onChange(java.util.function.Consumer<Boolean> c) {
        this.onChange = c == null ? v -> {} : c;
        return this;
    }

    public Md3Switch label(String l) { this.label = l; return this; }

    public boolean checked() { return checked; }

    public void setChecked(boolean v) {
        if (checked == v) return;
        checked = v;
        travel.target(v ? 1 : 0);
        colorMix.target(v ? 1 : 0);
        thumbSize.target(v ? 1 : 0);
    }

    @Override
    protected void onTick(double dt) {
        travel.advance(dt);
        colorMix.advance(dt);
        // Pressed thumb swells past its checked size, per the Expressive spec.
        thumbSize.target(state.pressed() ? 1.25 : (checked ? 1 : 0));
        thumbSize.advance(dt);
    }

    @Override
    protected boolean onClick(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        setChecked(!checked);
        onChange.accept(checked);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!enabled || !focused) return false;
        if (keyCode == 257 || keyCode == 32 || keyCode == 335) {
            setChecked(!checked);
            onChange.accept(checked);
            return true;
        }
        return false;
    }

    @Override
    protected boolean isSelected() { return checked; }

    @Override
    public void render(Md3Canvas c, Md3Scheme s) {
        if (!visible) return;

        float mix = reducedMotion ? (checked ? 1f : 0f) : (float) colorMix.value();
        float pos = reducedMotion ? (checked ? 1f : 0f) : (float) travel.value();
        float grow = reducedMotion ? (checked ? 1f : 0f) : (float) thumbSize.value();

        int trackOff = s.surfaceContainerHighest;
        int trackOn = s.primary;
        int track = Argb.lerp(trackOff, trackOn, mix);

        int thumbOff = s.outline;
        int thumbOn = s.onPrimary;
        int thumb = Argb.lerp(thumbOff, thumbOn, mix);

        if (!enabled) {
            track = checked
                    ? Argb.withAlpha(s.onSurface, Math.round(Md3Tokens.DISABLED_CONTAINER * 255))
                    : Argb.withAlpha(s.surfaceContainerHighest, 128);
            thumb = Argb.withAlpha(s.onSurface, Math.round(Md3Tokens.DISABLED_CONTENT * 255));
        }

        float trackH = height;
        float trackY = y;
        Shapes.pill(c, x, trackY, width, trackH, track);

        // Unchecked switches carry a visible outline; checked ones do not.
        if (mix < 0.98f) {
            int ol = Argb.withAlpha(enabled ? s.outline : s.onSurface,
                    Math.round((1f - mix) * (enabled ? 255 : 60)));
            Shapes.roundRectOutline(c, x, trackY, width, trackH, trackH / 2f,
                    Md3Tokens.STROKE_THIN, ol);
        }

        // Thumb: 6px unchecked, 8px checked, 9.5px while pressed.
        float rMin = 3f, rMax = 5f;
        float r = rMin + (rMax - rMin) * Math.min(grow, 1.25f);
        float pad = 2f;
        float travelStart = x + pad + rMin;
        float travelEnd = x + width - pad - rMin;
        float cx = Motion.lerp(travelStart, travelEnd, pos);
        float cy = trackY + trackH / 2f;

        // Focus/hover halo around the thumb.
        float sa = state.overlayAlpha();
        if (sa > 0.002f && enabled) {
            Shapes.circle(c, cx, cy, r + 5f,
                    Argb.withAlpha(checked ? s.primary : s.onSurface, Math.round(sa * 255)));
        }

        Shapes.circle(c, cx, cy, r, thumb);

        if (label != null) {
            float ty = y + (height - c.lineHeight()) / 2f + 0.5f;
            c.drawText(label, x + width + Md3Tokens.SPACE_MD, ty,
                    enabled ? s.onSurface
                            : Argb.withAlpha(s.onSurface,
                                    Math.round(Md3Tokens.DISABLED_CONTENT * 255)),
                    false);
        }
    }
}
