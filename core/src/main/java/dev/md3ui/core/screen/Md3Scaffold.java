package dev.md3ui.core.screen;

import dev.md3ui.core.color.Argb;
import dev.md3ui.core.gfx.Md3Canvas;
import dev.md3ui.core.gfx.Shapes;
import dev.md3ui.core.motion.Motion;
import dev.md3ui.core.motion.Spring;
import dev.md3ui.core.theme.Md3Scheme;
import dev.md3ui.core.theme.Md3Tokens;
import dev.md3ui.core.widget.Md3Widget;

/**
 * Layout shell shared by every replaced screen: app bar, body, optional rail,
 * and the widget/focus plumbing.
 *
 * <p>Responsive by breakpoint, the same rule Material uses on Android: below
 * 480 logical pixels the navigation collapses to a bottom bar, above it a side
 * rail. Minecraft windows genuinely span that range once GUI scale is taken into
 * account, so the switch is not decorative.
 *
 * <p>The scaffold also owns the entry transition. A screen fades and rises a few
 * pixels on an emphasised spring, which replaces vanilla's instant cut and is
 * the cheapest change that makes the whole UI feel deliberate.
 */
public class Md3Scaffold {

    /** Layout breakpoint between bottom-bar and side-rail navigation. */
    public static final float BREAKPOINT_COMPACT = 480f;

    protected final java.util.List<Md3Widget> widgets = new java.util.ArrayList<>();
    protected String title;
    protected String subtitle;
    protected boolean showBackButton = true;

    private final Spring enter = Motion.SPATIAL_DEFAULT.instance(0);
    private final Spring appBarElevation = Motion.EFFECT_DEFAULT.instance(0);
    private int focusIndex = -1;
    private boolean reducedMotion;
    private float lastWidth, lastHeight;
    private boolean scrolled;

    public Md3Scaffold(String title) {
        this.title = title;
        enter.target(1);
    }

    public Md3Scaffold subtitle(String s) { this.subtitle = s; return this; }
    public Md3Scaffold backButton(boolean b) { this.showBackButton = b; return this; }

    public Md3Scaffold reducedMotion(boolean r) {
        this.reducedMotion = r;
        if (r) enter.snapTo(1);
        for (Md3Widget w : widgets) w.setReducedMotion(r);
        return this;
    }

    public boolean reducedMotion() { return reducedMotion; }

    public <T extends Md3Widget> T add(T w) {
        w.setReducedMotion(reducedMotion);
        widgets.add(w);
        return w;
    }

    public void clearWidgets() {
        widgets.clear();
        focusIndex = -1;
    }

    public java.util.List<Md3Widget> widgets() { return widgets; }

    /** True when the viewport is narrow enough to want bottom navigation. */
    public boolean compact() { return lastWidth > 0 && lastWidth < BREAKPOINT_COMPACT; }

    /** Content area below the app bar. */
    public float contentTop() { return Md3Tokens.APP_BAR_HEIGHT; }

    public void setScrolled(boolean s) {
        this.scrolled = s;
        appBarElevation.target(s ? 1 : 0);
    }

    public void tick(double dt, double mouseX, double mouseY, boolean mouseDown) {
        enter.advance(dt);
        appBarElevation.advance(dt);
        for (Md3Widget w : widgets) w.tick(dt, mouseX, mouseY, mouseDown);
    }

    /**
     * Draw the scrim, app bar and every widget.
     *
     * @param inWorld true when a world is rendering behind the screen, which
     *                calls for a scrim instead of an opaque surface
     */
    public void render(Md3Canvas c, Md3Scheme s, boolean inWorld) {
        lastWidth = c.width();
        lastHeight = c.height();

        float t = reducedMotion ? 1f : (float) enter.value();
        float rise = (1f - t) * 6f;

        if (inWorld) {
            c.fillRect(0, 0, c.width(), c.height(),
                    Argb.withAlpha(s.scrim, Math.round(Md3Tokens.SCRIM_OPACITY * 255 * t)));
        } else {
            c.fillRect(0, 0, c.width(), c.height(), s.surface);
        }

        c.pushAlpha(t);
        c.pushTranslate(0, rise);

        renderAppBar(c, s);
        renderContent(c, s);

        for (Md3Widget w : widgets) w.render(c, s);

        c.popTransform();
        c.popAlpha();
    }

    /** Override to draw content behind the widgets. */
    protected void renderContent(Md3Canvas c, Md3Scheme s) {}

    protected void renderAppBar(Md3Canvas c, Md3Scheme s) {
        if (title == null) return;
        float h = Md3Tokens.APP_BAR_HEIGHT;
        float el = reducedMotion ? (scrolled ? 1f : 0f) : (float) appBarElevation.value();

        // App bar gains tone (not a shadow) once content scrolls under it.
        int bg = Argb.lerp(0x00000000, s.surfaceContainer, el);
        if (Argb.a(bg) > 0) c.fillRect(0, 0, c.width(), h, bg);

        float tx = Md3Tokens.SPACE_XL;
        float ty = subtitle != null ? h / 2f - c.lineHeight() - 0.5f
                : (h - c.lineHeight()) / 2f;
        c.drawText(title, tx, ty, s.onSurface, false);
        if (subtitle != null) {
            c.drawText(subtitle, tx, h / 2f + 1.5f, s.onSurfaceVariant, false);
        }
    }

    // --- Input plumbing ---

    public boolean mouseClicked(double mx, double my, int button) {
        // Iterate in reverse so the topmost widget wins.
        for (int i = widgets.size() - 1; i >= 0; i--) {
            Md3Widget w = widgets.get(i);
            if (w.mouseClicked(mx, my, button)) {
                setFocus(i);
                return true;
            }
        }
        setFocus(-1);
        return false;
    }

    public boolean mouseReleased(double mx, double my, int button) {
        boolean any = false;
        for (Md3Widget w : widgets) any |= w.mouseReleased(mx, my, button);
        return any;
    }

    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        for (Md3Widget w : widgets) {
            if (w.mouseDragged(mx, my, button, dx, dy)) return true;
        }
        return false;
    }

    public boolean mouseScrolled(double mx, double my, double amount) {
        for (int i = widgets.size() - 1; i >= 0; i--) {
            if (widgets.get(i).mouseScrolled(mx, my, amount)) return true;
        }
        return false;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 258) { // tab
            cycleFocus((modifiers & 0x1) != 0 ? -1 : 1);
            return true;
        }
        if (focusIndex >= 0 && focusIndex < widgets.size()) {
            if (widgets.get(focusIndex).keyPressed(keyCode, scanCode, modifiers)) return true;
        }
        for (Md3Widget w : widgets) {
            if (w != focused() && w.keyPressed(keyCode, scanCode, modifiers)) return true;
        }
        return false;
    }

    public boolean charTyped(char chr, int modifiers) {
        Md3Widget f = focused();
        return f != null && f.charTyped(chr, modifiers);
    }

    public Md3Widget focused() {
        return focusIndex >= 0 && focusIndex < widgets.size() ? widgets.get(focusIndex) : null;
    }

    private void setFocus(int index) {
        for (Md3Widget w : widgets) w.setFocused(false);
        focusIndex = index;
        Md3Widget f = focused();
        if (f != null) f.setFocused(true);
    }

    private void cycleFocus(int dir) {
        if (widgets.isEmpty()) return;
        int n = widgets.size();
        for (int step = 1; step <= n; step++) {
            int i = ((focusIndex + dir * step) % n + n) % n;
            if (widgets.get(i).focusable()) {
                setFocus(i);
                return;
            }
        }
    }

    /** Centre a widget horizontally in the viewport. */
    public static float centerX(Md3Canvas c, float w) {
        return (c.width() - w) / 2f;
    }
}
