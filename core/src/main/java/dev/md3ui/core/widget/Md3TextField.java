package dev.md3ui.core.widget;

import dev.md3ui.core.color.Argb;
import dev.md3ui.core.gfx.Md3Canvas;
import dev.md3ui.core.gfx.Shapes;
import dev.md3ui.core.motion.Motion;
import dev.md3ui.core.motion.Spring;
import dev.md3ui.core.theme.Md3Scheme;
import dev.md3ui.core.theme.Md3Tokens;

/**
 * MD3 filled/outlined text field with the floating label.
 *
 * <p>The floating label is the whole reason this component is worth writing:
 * the placeholder rises into the border and shrinks when the field gains focus
 * or content, which is instantly recognisable as Material and impossible with
 * vanilla's {@code EditBox}.
 *
 * <p>Editing state lives here rather than delegating to Minecraft's text
 * handler, because the vanilla one is entangled with its own rendering across
 * the supported version range.
 */
public final class Md3TextField extends Md3Widget {

    public enum Style { FILLED, OUTLINED }

    private final StringBuilder text = new StringBuilder();
    private String labelText;
    private String placeholder;
    private String supportingText;
    private String errorText;
    private Style style = Style.OUTLINED;
    private int maxLength = 256;
    private java.util.function.Consumer<String> onChange = v -> {};
    private java.util.function.Predicate<String> validator = v -> true;

    private int cursor;
    private int selectionAnchor = -1;
    private float scrollOffset;
    private long lastBlink = System.currentTimeMillis();
    private boolean blinkOn = true;

    private final Spring labelFloat = Motion.SPATIAL_FAST.instance(0);
    private final Spring focusRing = Motion.EFFECT_FAST.instance(0);

    public Md3TextField(float x, float y, float w, String labelText) {
        super(x, y, w, Md3Tokens.TEXT_FIELD_HEIGHT);
        this.labelText = labelText;
    }

    public Md3TextField style(Style s) { this.style = s; return this; }
    public Md3TextField placeholder(String p) { this.placeholder = p; return this; }
    public Md3TextField supporting(String s) { this.supportingText = s; return this; }
    public Md3TextField maxLength(int n) { this.maxLength = Math.max(1, n); return this; }

    public Md3TextField onChange(java.util.function.Consumer<String> c) {
        this.onChange = c == null ? v -> {} : c;
        return this;
    }

    public Md3TextField validator(java.util.function.Predicate<String> p) {
        this.validator = p == null ? v -> true : p;
        return this;
    }

    public String text() { return text.toString(); }

    public void setText(String t) {
        text.setLength(0);
        text.append(t == null ? "" : t);
        if (text.length() > maxLength) text.setLength(maxLength);
        cursor = text.length();
        selectionAnchor = -1;
        revalidate();
    }

    public boolean valid() { return errorText == null; }

    private void revalidate() {
        errorText = validator.test(text.toString()) ? null : "Invalid value";
    }

    @Override
    public Md3Widget setFocused(boolean f) {
        super.setFocused(f);
        if (f) { lastBlink = System.currentTimeMillis(); blinkOn = true; }
        else selectionAnchor = -1;
        return this;
    }

    @Override
    protected void onTick(double dt) {
        boolean floatUp = focused || text.length() > 0;
        labelFloat.target(floatUp ? 1 : 0);
        labelFloat.advance(dt);
        focusRing.target(focused ? 1 : 0);
        focusRing.advance(dt);

        long now = System.currentTimeMillis();
        if (now - lastBlink > 500) {
            blinkOn = !blinkOn;
            lastBlink = now;
        }
    }

    @Override
    protected boolean onClick(double mouseX, double mouseY, int button) {
        setFocused(true);
        // Place the caret at the clicked character.
        cursor = text.length();
        selectionAnchor = -1;
        return true;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (!focused || !enabled) return false;
        if (chr < 32 || chr == 127) return false;
        if (text.length() >= maxLength) return true;
        deleteSelection();
        text.insert(cursor, chr);
        cursor++;
        revalidate();
        onChange.accept(text.toString());
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!focused || !enabled) return false;
        boolean ctrl = (modifiers & 0x2) != 0;
        boolean shift = (modifiers & 0x1) != 0;

        switch (keyCode) {
            case 259: // backspace
                if (!deleteSelection() && cursor > 0) {
                    text.deleteCharAt(--cursor);
                }
                revalidate();
                onChange.accept(text.toString());
                return true;
            case 261: // delete
                if (!deleteSelection() && cursor < text.length()) {
                    text.deleteCharAt(cursor);
                }
                revalidate();
                onChange.accept(text.toString());
                return true;
            case 263: // left
                if (shift && selectionAnchor < 0) selectionAnchor = cursor;
                else if (!shift) selectionAnchor = -1;
                cursor = Math.max(0, cursor - 1);
                return true;
            case 262: // right
                if (shift && selectionAnchor < 0) selectionAnchor = cursor;
                else if (!shift) selectionAnchor = -1;
                cursor = Math.min(text.length(), cursor + 1);
                return true;
            case 268: // home
                cursor = 0; if (!shift) selectionAnchor = -1; return true;
            case 269: // end
                cursor = text.length(); if (!shift) selectionAnchor = -1; return true;
            case 65: // A
                if (ctrl) { selectionAnchor = 0; cursor = text.length(); return true; }
                return false;
            default:
                return false;
        }
    }

    private boolean deleteSelection() {
        if (selectionAnchor < 0 || selectionAnchor == cursor) return false;
        int from = Math.min(selectionAnchor, cursor);
        int to = Math.max(selectionAnchor, cursor);
        text.delete(from, to);
        cursor = from;
        selectionAnchor = -1;
        return true;
    }

    @Override
    public void render(Md3Canvas c, Md3Scheme s) {
        if (!visible) return;

        float fl = reducedMotion ? ((focused || text.length() > 0) ? 1f : 0f)
                : (float) labelFloat.value();
        float fr = reducedMotion ? (focused ? 1f : 0f) : (float) focusRing.value();

        boolean isError = errorText != null;
        int accent = isError ? s.error : Argb.lerp(s.outline, s.primary, fr);
        int contentColor = enabled ? s.onSurface
                : Argb.withAlpha(s.onSurface, Math.round(Md3Tokens.DISABLED_CONTENT * 255));
        int labelColor = isError ? s.error
                : (fr > 0.5f ? s.primary : s.onSurfaceVariant);

        float radius = style == Style.FILLED ? Md3Tokens.SHAPE_SM : Md3Tokens.SHAPE_XS + 2f;

        if (style == Style.FILLED) {
            Shapes.roundRect(c, x, y, width, height, radius, radius, 0, 0,
                    s.surfaceContainerHighest);
            // Filled fields carry a bottom indicator that thickens on focus.
            float ind = Motion.lerp(Md3Tokens.STROKE_THIN, Md3Tokens.STROKE_FOCUS, fr);
            c.fillRect(x, y + height - ind, width, ind, accent);
        } else {
            float stroke = Motion.lerp(Md3Tokens.STROKE_THIN, Md3Tokens.STROKE_FOCUS, fr);
            Shapes.roundRectOutline(c, x, y, width, height, radius, stroke, accent);
        }

        // Hover state layer, only on the filled variant per spec.
        if (style == Style.FILLED && state.overlayAlpha() > 0.002f) {
            Shapes.roundRect(c, x, y, width, height, radius, radius, 0, 0,
                    Argb.withAlpha(s.onSurface, Math.round(state.overlayAlpha() * 255)));
        }

        float padX = Md3Tokens.SPACE_MD;
        float lh = c.lineHeight();

        // Floating label: rides from the middle up to the top edge.
        if (labelText != null) {
            float restY = y + (height - lh) / 2f;
            float floatY = y - lh / 2f + 1f;
            float ly = Motion.lerp(restY, floatY, fl);
            // Punch a gap in the outline so the label sits inside the border.
            if (style == Style.OUTLINED && fl > 0.2f) {
                float lw = c.textWidth(labelText) + 2f;
                c.fillRect(x + padX - 1f, y - 0.5f, lw * Motion.lerp(0.2f, 1f, fl), 2f,
                        s.surface);
            }
            c.drawText(labelText, x + padX, ly, labelColor, false);
        }

        // Text content, clipped to the field.
        c.pushClip(x + padX, y, width - padX * 2, height);
        float ty = y + (height - lh) / 2f + (labelText != null ? 2.5f : 0f);
        String shown = text.toString();

        if (shown.isEmpty() && placeholder != null && focused) {
            c.drawText(placeholder, x + padX - scrollOffset, ty, s.onSurfaceVariant, false);
        }

        // Selection highlight behind the glyphs.
        if (selectionAnchor >= 0 && selectionAnchor != cursor) {
            int from = Math.min(selectionAnchor, cursor);
            int to = Math.max(selectionAnchor, cursor);
            float sx = x + padX - scrollOffset + c.textWidth(shown.substring(0, from));
            float sw = c.textWidth(shown.substring(from, to));
            c.fillRect(sx, ty - 1f, sw, lh + 1f, Argb.withAlpha(s.primary, 90));
        }

        c.drawText(shown, x + padX - scrollOffset, ty, contentColor, false);

        // Caret.
        if (focused && blinkOn && enabled) {
            float cx = x + padX - scrollOffset + c.textWidth(shown.substring(0, cursor));
            c.fillRect(cx, ty - 1f, 1f, lh + 1f, isError ? s.error : s.primary);
        }
        c.popClip();

        // Supporting / error text below the field.
        String below = isError ? errorText : supportingText;
        if (below != null) {
            c.drawText(below, x + padX, y + height + 2f,
                    isError ? s.error : s.onSurfaceVariant, false);
        }
    }

    /** Total height including supporting text, for layout. */
    public float totalHeight(Md3Canvas c) {
        return height + ((errorText != null || supportingText != null)
                ? c.lineHeight() + 2f : 0);
    }
}
