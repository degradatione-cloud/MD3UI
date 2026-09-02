package dev.md3ui.core.widget;

import dev.md3ui.core.color.Argb;
import dev.md3ui.core.gfx.Md3Canvas;
import dev.md3ui.core.gfx.Shapes;
import dev.md3ui.core.motion.Motion;
import dev.md3ui.core.motion.Spring;
import dev.md3ui.core.theme.Md3Scheme;
import dev.md3ui.core.theme.Md3Tokens;

/**
 * Scrollable MD3 list, used for world/server selection and option groups.
 *
 * <p>Scrolling is spring-based with rubber-band overscroll, which is what makes
 * it feel Android-native rather than like vanilla's hard-clamped scroll. The
 * scrollbar is an MD3 thumb that fades in while scrolling and out when idle.
 *
 * <p>Rows are rendered only for the visible window, so a 500-server list costs
 * the same as a 10-server one.
 */
public final class Md3List extends Md3Widget {

    /** One row. {@code render} draws inside the row's own local rect. */
    public interface Row {
        float height();
        void render(Md3Canvas c, Md3Scheme s, float x, float y, float w, float h,
                    boolean hovered, boolean selected);
        default void onClick() {}
        default String searchKey() { return ""; }
    }

    /** Convenience row: leading glyph, headline, supporting line, trailing text. */
    public static final class TextRow implements Row {
        private final String icon, headline, supporting, trailing;
        private final Runnable action;
        private final float h;

        public TextRow(String icon, String headline, String supporting, String trailing,
                       Runnable action) {
            this.icon = icon;
            this.headline = headline;
            this.supporting = supporting;
            this.trailing = trailing;
            this.action = action == null ? () -> {} : action;
            this.h = supporting != null ? Md3Tokens.LIST_ITEM_HEIGHT_TWO_LINE
                    : Md3Tokens.LIST_ITEM_HEIGHT;
        }

        @Override public float height() { return h; }
        @Override public void onClick() { action.run(); }
        @Override public String searchKey() {
            return (headline == null ? "" : headline) + " "
                    + (supporting == null ? "" : supporting);
        }

        @Override
        public void render(Md3Canvas c, Md3Scheme s, float x, float y, float w, float h,
                           boolean hovered, boolean selected) {
            int container = selected ? s.secondaryContainer : s.surfaceContainerLow;
            int headColor = selected ? s.onSecondaryContainer : s.onSurface;
            int suppColor = selected ? s.onSecondaryContainer : s.onSurfaceVariant;

            Shapes.roundRect(c, x, y, w, h, Md3Tokens.SHAPE_MD, container);
            if (hovered) {
                Shapes.roundRect(c, x, y, w, h, Md3Tokens.SHAPE_MD,
                        Argb.withAlpha(headColor, Math.round(Md3Tokens.STATE_HOVER * 255)));
            }

            float tx = x + Md3Tokens.SPACE_LG;
            if (icon != null) {
                float iy = y + (h - c.lineHeight()) / 2f;
                c.drawText(icon, tx, iy, suppColor, false);
                tx += c.textWidth(icon) + Md3Tokens.SPACE_MD;
            }

            if (supporting != null) {
                c.drawText(headline, tx, y + h / 2f - c.lineHeight() - 0.5f, headColor, false);
                c.drawText(supporting, tx, y + h / 2f + 1.5f, suppColor, false);
            } else {
                c.drawText(headline, tx, y + (h - c.lineHeight()) / 2f, headColor, false);
            }

            if (trailing != null) {
                float tw = c.textWidth(trailing);
                c.drawText(trailing, x + w - Md3Tokens.SPACE_LG - tw,
                        y + (h - c.lineHeight()) / 2f, suppColor, false);
            }
        }
    }

    private final java.util.List<Row> rows = new java.util.ArrayList<>();
    private final Spring scroll = Motion.SPATIAL_DEFAULT.instance(0);
    private final Spring barFade = Motion.EFFECT_DEFAULT.instance(0);
    private double scrollTarget;
    private int selectedIndex = -1;
    private int hoveredIndex = -1;
    private float gap = Md3Tokens.SPACE_SM;
    private long lastScrollAt;

    public Md3List(float x, float y, float w, float h) {
        super(x, y, w, h);
    }

    public Md3List add(Row r) { rows.add(r); return this; }

    public Md3List addAll(java.util.Collection<? extends Row> r) { rows.addAll(r); return this; }

    public Md3List clearRows() { rows.clear(); selectedIndex = -1; return this; }

    public Md3List gap(float g) { this.gap = g; return this; }

    public int selectedIndex() { return selectedIndex; }

    public int size() { return rows.size(); }

    private float contentHeight() {
        float total = 0;
        for (Row r : rows) total += r.height() + gap;
        return Math.max(0, total - gap);
    }

    private float maxScroll() {
        return Math.max(0, contentHeight() - height);
    }

    @Override
    protected void onTick(double dt) {
        scroll.advance(dt);
        boolean scrolling = System.currentTimeMillis() - lastScrollAt < 900;
        barFade.target(scrolling || state.hovered() ? 1 : 0);
        barFade.advance(dt);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (!contains(mouseX, mouseY)) return false;
        scrollTarget -= amount * 18;
        // Rubber band: allow a little overscroll, then spring back.
        scrollTarget = Math.max(-14, Math.min(scrollTarget, maxScroll() + 14));
        scroll.target(Math.max(0, Math.min(scrollTarget, maxScroll())));
        lastScrollAt = System.currentTimeMillis();
        return true;
    }

    @Override
    protected boolean onClick(double mouseX, double mouseY, int button) {
        int i = indexAt(mouseY);
        if (i < 0) return false;
        selectedIndex = i;
        rows.get(i).onClick();
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!focused || rows.isEmpty()) return false;
        if (keyCode == 264) { // down
            selectedIndex = Math.min(rows.size() - 1, selectedIndex + 1);
            ensureVisible(selectedIndex);
            return true;
        }
        if (keyCode == 265) { // up
            selectedIndex = Math.max(0, selectedIndex - 1);
            ensureVisible(selectedIndex);
            return true;
        }
        if ((keyCode == 257 || keyCode == 335) && selectedIndex >= 0) {
            rows.get(selectedIndex).onClick();
            return true;
        }
        return false;
    }

    private void ensureVisible(int index) {
        float top = 0;
        for (int i = 0; i < index; i++) top += rows.get(i).height() + gap;
        float bottom = top + rows.get(index).height();
        double s = scroll.target();
        if (top < s) s = top;
        else if (bottom > s + height) s = bottom - height;
        scrollTarget = Math.max(0, Math.min(s, maxScroll()));
        scroll.target(scrollTarget);
        lastScrollAt = System.currentTimeMillis();
    }

    private int indexAt(double my) {
        if (my < y || my > y + height) return -1;
        float cy = y - (float) scroll.value();
        for (int i = 0; i < rows.size(); i++) {
            float rh = rows.get(i).height();
            if (my >= cy && my < cy + rh) return i;
            cy += rh + gap;
        }
        return -1;
    }

    @Override
    public void tick(double dtSeconds, double mouseX, double mouseY, boolean mouseDown) {
        super.tick(dtSeconds, mouseX, mouseY, mouseDown);
        hoveredIndex = contains(mouseX, mouseY) ? indexAt(mouseY) : -1;
    }

    @Override
    public void render(Md3Canvas c, Md3Scheme s) {
        if (!visible) return;

        c.pushClip(x, y, width, height);
        float sv = (float) scroll.value();
        float cy = y - sv;
        float barW = 3f;
        float rowW = width - (maxScroll() > 0 ? barW + Md3Tokens.SPACE_SM : 0);

        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            float rh = r.height();
            // Cull rows outside the viewport.
            if (cy + rh >= y - 2 && cy <= y + height + 2) {
                r.render(c, s, x, cy, rowW, rh, i == hoveredIndex, i == selectedIndex);
            }
            cy += rh + gap;
        }
        c.popClip();

        // Scrollbar thumb, fading with activity.
        float ms = maxScroll();
        float fade = reducedMotion ? 1f : (float) barFade.value();
        if (ms > 0 && fade > 0.01f) {
            float thumbH = Math.max(12f, height * (height / contentHeight()));
            float t = sv / ms;
            float ty = y + (height - thumbH) * t;
            Shapes.pill(c, x + width - barW, y, barW, height,
                    Argb.scaleAlpha(s.surfaceContainerHighest, fade * 0.6f));
            Shapes.pill(c, x + width - barW, ty, barW, thumbH,
                    Argb.scaleAlpha(s.primary, fade));
        }
    }
}
