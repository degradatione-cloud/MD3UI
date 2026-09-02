package dev.md3ui.core.widget;

import dev.md3ui.core.color.Argb;
import dev.md3ui.core.gfx.Md3Canvas;
import dev.md3ui.core.gfx.Shapes;
import dev.md3ui.core.motion.Motion;
import dev.md3ui.core.motion.Spring;
import dev.md3ui.core.theme.Md3Scheme;
import dev.md3ui.core.theme.Md3Tokens;

/**
 * The five MD3 button variants, plus the Expressive shape-morph on press.
 *
 * <p>Shape morph is the detail that sells Expressive: a resting pill eases
 * toward a rounded rectangle while held, driven by a spatial spring so an
 * interrupted press does not snap. Vanilla buttons have no equivalent, which is
 * why replacing them reads as a genuine redesign rather than a recolour.
 */
public final class Md3Button extends Md3Widget {

    public enum Style {
        /** Highest emphasis: filled with {@code primary}. */
        FILLED,
        /** Filled with {@code secondaryContainer}; medium emphasis. */
        TONAL,
        /** Outlined, transparent container. */
        OUTLINED,
        /** Text only, no container. Lowest emphasis. */
        TEXT,
        /** Filled with {@code surfaceContainerLow} plus a real shadow. */
        ELEVATED,
        /** Destructive: filled with {@code error}. */
        DANGER
    }

    private String label;
    private Style style;
    private Runnable onPress = () -> {};
    private final Spring morph = Motion.SPATIAL_FAST.instance(1f);
    private Md3Tokens.Type type = Md3Tokens.Type.LABEL_LARGE;
    private String leadingIcon;

    public Md3Button(float x, float y, float w, float h, String label, Style style) {
        super(x, y, w, h);
        this.label = label;
        this.style = style;
    }

    public static Md3Button filled(float x, float y, float w, String label, Runnable onPress) {
        return new Md3Button(x, y, w, Md3Tokens.BUTTON_HEIGHT, label, Style.FILLED).onPress(onPress);
    }

    public static Md3Button tonal(float x, float y, float w, String label, Runnable onPress) {
        return new Md3Button(x, y, w, Md3Tokens.BUTTON_HEIGHT, label, Style.TONAL).onPress(onPress);
    }

    public static Md3Button outlined(float x, float y, float w, String label, Runnable onPress) {
        return new Md3Button(x, y, w, Md3Tokens.BUTTON_HEIGHT, label, Style.OUTLINED).onPress(onPress);
    }

    public static Md3Button text(float x, float y, float w, String label, Runnable onPress) {
        return new Md3Button(x, y, w, Md3Tokens.BUTTON_HEIGHT, label, Style.TEXT).onPress(onPress);
    }

    public Md3Button onPress(Runnable r) { this.onPress = r == null ? () -> {} : r; return this; }
    public Md3Button label(String l) { this.label = l; return this; }
    public Md3Button style(Style s) { this.style = s; return this; }
    public Md3Button icon(String iconGlyph) { this.leadingIcon = iconGlyph; return this; }
    public Md3Button type(Md3Tokens.Type t) { this.type = t; return this; }

    public String label() { return label; }

    @Override
    protected void onTick(double dt) {
        // Held -> morph toward a squarer shape (Expressive shape morph).
        morph.target(state.pressed() ? 0.45 : 1.0);
        morph.advance(dt);
    }

    @Override
    protected boolean onClick(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        onPress.run();
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Enter (257) / Space (32) activate a focused button.
        if (!enabled || !focused) return false;
        if (keyCode == 257 || keyCode == 335 || keyCode == 32) {
            state.ripple(width / 2f, height / 2f);
            onPress.run();
            return true;
        }
        return false;
    }

    @Override
    public void render(Md3Canvas c, Md3Scheme s) {
        if (!visible) return;

        float radius = reducedMotion
                ? height / 2f
                : Motion.lerp(Md3Tokens.SHAPE_SM, height / 2f, morph.valueF());

        int container;
        int content;
        boolean outline = false;
        int elevation = 0;

        switch (style) {
            case FILLED:
                container = s.primary; content = s.onPrimary; break;
            case TONAL:
                container = s.secondaryContainer; content = s.onSecondaryContainer; break;
            case OUTLINED:
                container = 0x00000000; content = s.primary; outline = true; break;
            case TEXT:
                container = 0x00000000; content = s.primary; break;
            case ELEVATED:
                container = s.surfaceContainerLow; content = s.primary; elevation = 1; break;
            case DANGER:
                container = s.error; content = s.onError; break;
            default:
                container = s.primary; content = s.onPrimary;
        }

        if (!enabled) {
            if (Argb.a(container) != 0) {
                container = Argb.withAlpha(s.onSurface,
                        Math.round(Md3Tokens.DISABLED_CONTAINER * 255));
            }
            content = Argb.withAlpha(s.onSurface,
                    Math.round(Md3Tokens.DISABLED_CONTENT * 255));
            outline = outline && false;
        }

        if (elevation > 0 && enabled) {
            Shapes.softShadow(c, x, y, width, height, radius,
                    Md3Tokens.shadowSpread(elevation), Argb.withAlpha(s.shadow, 90));
        }

        if (Argb.a(container) != 0) {
            Shapes.roundRect(c, x, y, width, height, radius, container);
        }

        if (outline) {
            int ol = state.focused() ? s.primary : s.outline;
            float stroke = state.focused() ? Md3Tokens.STROKE_FOCUS : Md3Tokens.STROKE_THIN;
            Shapes.roundRectOutline(c, x, y, width, height, radius, stroke, ol);
        }

        // State layer sits above the container, below the label.
        state.draw(c, x, y, width, height, radius, content, reducedMotion);

        String text = label;
        float tw = c.textWidth(text);
        float iconW = leadingIcon != null ? c.textWidth(leadingIcon) + Md3Tokens.SPACE_SM : 0;
        float tx = x + (width - tw - iconW) / 2f + iconW;
        float ty = y + (height - c.lineHeight()) / 2f + 0.5f;

        if (leadingIcon != null) {
            c.drawText(leadingIcon, x + (width - tw - iconW) / 2f, ty, content, false);
        }
        c.drawText(text, tx, ty, content, false);
    }

    /** Intrinsic width for this label, honouring MD3 minimum padding. */
    public float intrinsicWidth(Md3Canvas c) {
        float w = c.textWidth(label) + Md3Tokens.BUTTON_PADDING_H * 2;
        if (leadingIcon != null) w += c.textWidth(leadingIcon) + Md3Tokens.SPACE_SM;
        return Math.max(w, 48f);
    }
}
