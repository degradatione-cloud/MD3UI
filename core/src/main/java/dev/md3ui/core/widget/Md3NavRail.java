package dev.md3ui.core.widget;

import dev.md3ui.core.color.Argb;
import dev.md3ui.core.gfx.Md3Canvas;
import dev.md3ui.core.gfx.Shapes;
import dev.md3ui.core.motion.Motion;
import dev.md3ui.core.motion.Spring;
import dev.md3ui.core.theme.Md3Scheme;
import dev.md3ui.core.theme.Md3Tokens;

/**
 * Navigation rail with the sliding {@code secondaryContainer} pill.
 *
 * <p>The travelling pill indicator is the most recognisable MD3 layout element,
 * and it maps unusually well onto Minecraft: the options menu, the pause screen
 * and the world-selection screen are all really tab sets that vanilla renders as
 * loose button grids. One rail replaces all of them.
 *
 * <p>The indicator is a single spring-driven rectangle rather than a per-item
 * fade, so switching tabs mid-animation retargets smoothly instead of stacking
 * cross-fades.
 */
public final class Md3NavRail extends Md3Widget {

    public static final class Item {
        public final String icon;
        public final String label;
        public final Runnable action;
        public int badge;

        public Item(String icon, String label, Runnable action) {
            this.icon = icon;
            this.label = label;
            this.action = action == null ? () -> {} : action;
        }

        public Item badge(int n) { this.badge = n; return this; }
    }

    private final java.util.List<Item> items = new java.util.ArrayList<>();
    private int selected;
    private final Spring indicator = Motion.SPATIAL_DEFAULT.instance(0);
    private final Spring[] itemStates;
    private boolean horizontal;
    private boolean showLabels = true;

    public Md3NavRail(float x, float y, float w, float h, java.util.List<Item> items) {
        super(x, y, w, h);
        this.items.addAll(items);
        this.itemStates = new Spring[this.items.size()];
        for (int i = 0; i < itemStates.length; i++) {
            itemStates[i] = Motion.EFFECT_DEFAULT.instance(i == 0 ? 1 : 0);
        }
    }

    /** Bottom-bar mode: items laid out left-to-right. */
    public Md3NavRail horizontal(boolean h) { this.horizontal = h; return this; }

    public Md3NavRail showLabels(boolean s) { this.showLabels = s; return this; }

    public Md3NavRail select(int index) {
        if (index < 0 || index >= items.size()) return this;
        selected = index;
        indicator.target(index);
        return this;
    }

    public int selected() { return selected; }

    public int itemCount() { return items.size(); }

    /** Slot size along the layout axis. */
    private float slot() {
        return horizontal ? width / Math.max(1, items.size()) : 34f;
    }

    private float itemOrigin(int i) {
        return horizontal ? x + slot() * i : y + Md3Tokens.SPACE_MD + slot() * i;
    }

    @Override
    protected void onTick(double dt) {
        indicator.advance(dt);
        for (int i = 0; i < itemStates.length; i++) {
            itemStates[i].target(i == selected ? 1 : 0);
            itemStates[i].advance(dt);
        }
    }

    /** Index under the pointer, or -1. */
    private int indexAt(double mx, double my) {
        if (!contains(mx, my)) return -1;
        float s = slot();
        if (horizontal) {
            int i = (int) ((mx - x) / s);
            return i >= 0 && i < items.size() ? i : -1;
        }
        int i = (int) ((my - (y + Md3Tokens.SPACE_MD)) / s);
        return i >= 0 && i < items.size() ? i : -1;
    }

    @Override
    protected boolean onClick(double mouseX, double mouseY, int button) {
        int i = indexAt(mouseX, mouseY);
        if (i < 0 || button != 0) return false;
        select(i);
        items.get(i).action.run();
        return true;
    }

    @Override
    public void render(Md3Canvas c, Md3Scheme s) {
        if (!visible) return;

        // The rail itself sits on surface; a bottom bar steps up one container level.
        int railBg = horizontal ? s.surfaceContainer : s.surface;
        if (horizontal) {
            c.fillRect(x, y, width, height, railBg);
        }

        float sl = slot();
        float indPos = reducedMotion ? selected : (float) indicator.value();

        for (int i = 0; i < items.size(); i++) {
            Item it = items.get(i);
            float ox = horizontal ? itemOrigin(i) : x;
            float oy = horizontal ? y : itemOrigin(i);
            float iw = horizontal ? sl : width;
            float ih = horizontal ? height : sl;

            float act = reducedMotion ? (i == selected ? 1f : 0f)
                    : (float) itemStates[i].value();

            int iconColor = Argb.lerp(s.onSurfaceVariant, s.onSecondaryContainer, act);
            int labelColor = Argb.lerp(s.onSurfaceVariant, s.onSurface, act);

            // Indicator pill: one shape, positioned by the spring.
            float dist = Math.abs(i - indPos);
            if (dist < 1f) {
                float alpha = 1f - dist;
                float pillW = horizontal ? Math.min(iw - 8f, 32f) : Math.min(iw - 8f, 28f);
                float pillH = 16f;
                float px = ox + (iw - pillW) / 2f;
                float py = oy + (showLabels ? 3f : (ih - pillH) / 2f);
                Shapes.pill(c, px, py, pillW, pillH,
                        Argb.scaleAlpha(s.secondaryContainer, alpha));
            }

            // Icon glyph, centred in the pill area.
            float glyphW = c.textWidth(it.icon);
            float gx = ox + (iw - glyphW) / 2f;
            float gy = oy + (showLabels ? 3f + (16f - c.lineHeight()) / 2f
                    : (ih - c.lineHeight()) / 2f);
            c.drawText(it.icon, gx, gy, iconColor, false);

            // Badge dot, hidden at zero (rendering "0" is off-spec).
            if (it.badge > 0) {
                String bt = it.badge > 99 ? "99+" : String.valueOf(it.badge);
                float bw = c.textWidth(bt) + 4f;
                float bx = gx + glyphW + 1f;
                float by = gy - 2f;
                Shapes.pill(c, bx, by, bw, 8f, s.error);
                c.drawText(bt, bx + 2f, by + 0.5f, s.onError, false);
            }

            if (showLabels) {
                float lw = c.textWidth(it.label);
                c.drawText(it.label, ox + (iw - lw) / 2f, oy + 21f, labelColor, false);
            }
        }
    }
}
