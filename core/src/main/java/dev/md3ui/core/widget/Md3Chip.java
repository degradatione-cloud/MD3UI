package dev.md3ui.core.widget;

import dev.md3ui.core.color.Argb;
import dev.md3ui.core.gfx.Md3Canvas;
import dev.md3ui.core.gfx.Shapes;
import dev.md3ui.core.theme.Md3Scheme;
import dev.md3ui.core.theme.Md3Tokens;

/**
 * MD3 chips: filter, assist, input and suggestion.
 *
 * <p>Filter chips carry a check glyph when selected and swap their container to
 * {@code secondaryContainer}. Their border uses {@code outline} rather than
 * {@code outlineVariant} on purpose &mdash; {@code outlineVariant} falls below
 * the 3:1 non-text contrast floor in dark mode, which makes unselected chips
 * effectively invisible.
 */
public final class Md3Chip extends Md3Widget {

    public enum Kind { FILTER, ASSIST, INPUT, SUGGESTION }

    private String label;
    private Kind kind = Kind.FILTER;
    private boolean selected;
    private boolean elevated;
    private java.util.function.Consumer<Boolean> onToggle = v -> {};
    private Runnable onRemove;

    public Md3Chip(float x, float y, String label, Kind kind) {
        super(x, y, 40f, 16f);
        this.label = label;
        this.kind = kind;
    }

    public Md3Chip selected(boolean s) { this.selected = s; return this; }
    public Md3Chip elevated(boolean e) { this.elevated = e; return this; }

    public Md3Chip onToggle(java.util.function.Consumer<Boolean> c) {
        this.onToggle = c == null ? v -> {} : c;
        return this;
    }

    public Md3Chip onRemove(Runnable r) { this.onRemove = r; return this; }

    public boolean isSelectedChip() { return selected; }

    @Override
    protected boolean isSelected() { return selected; }

    /** Lay the chip out to fit its label; call once the canvas font is known. */
    public Md3Chip measure(Md3Canvas c) {
        float w = c.textWidth(label) + Md3Tokens.SPACE_MD * 2;
        if (kind == Kind.FILTER && selected) w += 9f;
        if (kind == Kind.INPUT) w += 9f;
        this.width = w;
        this.height = 16f;
        return this;
    }

    @Override
    protected boolean onClick(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        if (kind == Kind.INPUT && onRemove != null
                && mouseX > x + width - 10f) {
            onRemove.run();
            return true;
        }
        if (kind == Kind.FILTER) {
            selected = !selected;
            onToggle.accept(selected);
        } else {
            onToggle.accept(true);
        }
        return true;
    }

    @Override
    public void render(Md3Canvas c, Md3Scheme s) {
        if (!visible) return;

        float radius = Md3Tokens.SHAPE_SM;
        int container;
        int content;
        boolean outline;

        if (selected) {
            container = s.secondaryContainer;
            content = s.onSecondaryContainer;
            outline = false;
        } else if (elevated) {
            container = s.surfaceContainerLow;
            content = s.onSurfaceVariant;
            outline = false;
        } else {
            container = 0x00000000;
            content = s.onSurfaceVariant;
            outline = true;
        }

        if (!enabled) {
            content = Argb.withAlpha(s.onSurface,
                    Math.round(Md3Tokens.DISABLED_CONTENT * 255));
            if (Argb.a(container) != 0) {
                container = Argb.withAlpha(s.onSurface,
                        Math.round(Md3Tokens.DISABLED_CONTAINER * 255));
            }
        }

        if (elevated && enabled) {
            Shapes.softShadow(c, x, y, width, height, radius, 1f,
                    Argb.withAlpha(s.shadow, 70));
        }

        if (Argb.a(container) != 0) {
            Shapes.roundRect(c, x, y, width, height, radius, container);
        }
        if (outline) {
            Shapes.roundRectOutline(c, x, y, width, height, radius,
                    Md3Tokens.STROKE_THIN, enabled ? s.outline : s.outlineVariant);
        }

        state.draw(c, x, y, width, height, radius, content, reducedMotion);

        float tx = x + Md3Tokens.SPACE_MD;
        float ty = y + (height - c.lineHeight()) / 2f + 0.5f;

        if (kind == Kind.FILTER && selected) {
            // Leading check mark, drawn from two quads.
            float cx = tx, cy = y + height / 2f;
            c.fillRect(cx, cy, 2f, 2f, content);
            c.fillRect(cx + 1.5f, cy - 1.5f, 2f, 2f, content);
            c.fillRect(cx + 3f, cy - 3f, 2f, 2f, content);
            tx += 9f;
        }

        c.drawText(label, tx, ty, content, false);

        if (kind == Kind.INPUT && onRemove != null) {
            // Trailing close glyph.
            float cx = x + width - 8f, cy = y + height / 2f;
            for (int i = -2; i <= 2; i++) {
                c.fillRect(cx + i, cy + i, 1f, 1f, content);
                c.fillRect(cx + i, cy - i, 1f, 1f, content);
            }
        }
    }
}
