package dev.md3ui.core.widget;

import dev.md3ui.core.gfx.Md3Canvas;
import dev.md3ui.core.gfx.StateLayer;
import dev.md3ui.core.theme.Md3Scheme;

/**
 * Base for every MD3UI component.
 *
 * <p>Deliberately independent of Minecraft's {@code AbstractWidget}: the mod
 * module adapts these into vanilla widgets. Keeping the hierarchy clean means
 * the same components render in the offline screenshot tool, and it avoids
 * inheriting vanilla's texture-atlas drawing which is exactly the part that
 * varies across 1.21.4&ndash;1.21.11.
 */
public abstract class Md3Widget {

    protected float x, y, width, height;
    protected boolean visible = true;
    protected boolean enabled = true;
    protected boolean focused;
    protected final StateLayer state = new StateLayer();
    protected String tooltip;

    /** Set by the host screen each frame; components never read a global. */
    protected boolean reducedMotion;

    public Md3Widget(float x, float y, float width, float height) {
        this.x = x; this.y = y; this.width = width; this.height = height;
    }

    /** Advance animation. Called once per frame before {@link #render}. */
    public void tick(double dtSeconds, double mouseX, double mouseY, boolean mouseDown) {
        boolean hov = enabled && contains(mouseX, mouseY);
        state.update(hov, focused, hov && mouseDown, isSelected(), dtSeconds);
        onTick(dtSeconds);
    }

    protected void onTick(double dtSeconds) {}

    public abstract void render(Md3Canvas c, Md3Scheme s);

    /** @return true when the event was consumed. */
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!enabled || !visible || !contains(mouseX, mouseY)) return false;
        state.ripple((float) (mouseX - x), (float) (mouseY - y));
        return onClick(mouseX, mouseY, button);
    }

    protected boolean onClick(double mouseX, double mouseY, int button) { return true; }

    public boolean mouseReleased(double mouseX, double mouseY, int button) { return false; }

    public boolean mouseDragged(double mouseX, double mouseY, int button,
                                double dragX, double dragY) { return false; }

    public boolean mouseScrolled(double mouseX, double mouseY, double amount) { return false; }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) { return false; }

    public boolean charTyped(char chr, int modifiers) { return false; }

    protected boolean isSelected() { return false; }

    public boolean contains(double mx, double my) {
        return visible && mx >= x && mx < x + width && my >= y && my < y + height;
    }

    // --- Geometry / flags ---

    public float x() { return x; }
    public float y() { return y; }
    public float width() { return width; }
    public float height() { return height; }

    public Md3Widget setPos(float x, float y) { this.x = x; this.y = y; return this; }
    public Md3Widget setSize(float w, float h) { this.width = w; this.height = h; return this; }
    public Md3Widget setEnabled(boolean e) { this.enabled = e; return this; }
    public Md3Widget setVisible(boolean v) { this.visible = v; return this; }
    public Md3Widget setTooltip(String t) { this.tooltip = t; return this; }
    public Md3Widget setReducedMotion(boolean r) { this.reducedMotion = r; return this; }
    public Md3Widget setFocused(boolean f) { this.focused = f; return this; }

    public boolean enabled() { return enabled; }
    public boolean visible() { return visible; }
    public boolean isFocused() { return focused; }
    public String tooltip() { return tooltip; }

    /** True when this widget wants keyboard focus in tab order. */
    public boolean focusable() { return enabled && visible; }
}
