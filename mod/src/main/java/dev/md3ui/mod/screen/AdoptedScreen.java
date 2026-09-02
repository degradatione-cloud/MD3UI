package dev.md3ui.mod.screen;

import dev.md3ui.core.color.Argb;
import dev.md3ui.core.gfx.Md3Canvas;
import dev.md3ui.core.gfx.Shapes;
import dev.md3ui.core.motion.Motion;
import dev.md3ui.core.motion.Spring;
import dev.md3ui.core.theme.Md3Scheme;
import dev.md3ui.core.theme.Md3Tokens;
import dev.md3ui.mod.Md3Config;
import dev.md3ui.mod.Md3UI;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;

import java.util.ArrayList;
import java.util.List;

/**
 * Wraps a live vanilla screen and paints MD3 over its widgets.
 *
 * <p>Each vanilla widget is measured, hidden from rendering, and given an MD3
 * proxy that draws at the same rectangle. Clicks are still handled by the vanilla
 * widget, so all original behaviour, tooltips, and other mods' injected buttons
 * keep working exactly as before &mdash; they simply look different.
 *
 * <p>Widget kind is inferred from the vanilla class name rather than by
 * {@code instanceof} against classes that were renamed across
 * 1.21.4&ndash;1.21.11, and the value of a slider or cycling button is read
 * reflectively with a fallback. If inference fails the widget is left vanilla,
 * which is the safe outcome: an unstyled control beats an invisible one.
 */
public final class AdoptedScreen {

    /** How a vanilla widget should be drawn. */
    private enum Kind { BUTTON, SLIDER, CYCLE, TEXT_FIELD, LIST, UNKNOWN }

    private static final class Proxy {
        final AbstractWidget widget;
        final Kind kind;
        final Spring hover;
        final Spring press;
        boolean wasVisible;
        float pressAnchorX, pressAnchorY;

        Proxy(AbstractWidget w, Kind k) {
            this.widget = w;
            this.kind = k;
            this.hover = Motion.EFFECT_FAST.instance(0);
            this.press = Motion.SPATIAL_FAST.instance(0);
        }
    }

    private final Screen screen;
    private final Md3Config config;
    private final List<Proxy> proxies = new ArrayList<>();
    private final Spring enter = Motion.SPATIAL_DEFAULT.instance(0);
    private String heading;

    public AdoptedScreen(Screen screen, Md3Config config) {
        this.screen = screen;
        this.config = config;
        this.heading = plain(screen.getTitle() == null ? "" : screen.getTitle().getString());
        enter.target(1);
        if (config.reducedMotion) enter.snapTo(1);
    }

    public Screen screen() { return screen; }

    /** Take over rendering for the given widgets. */
    public void adopt(List<AbstractWidget> widgets) {
        for (AbstractWidget w : widgets) {
            Kind k = classify(w);
            Proxy p = new Proxy(w, k);
            p.wasVisible = w.visible;
            // Buttons, sliders and cycle controls are fully repainted, so the
            // vanilla art is suppressed. Text fields and lists keep drawing
            // themselves: reimplementing EditBox's caret/selection state or a
            // list's entry rendering would be a large behavioural surface to get
            // wrong, so MD3 only supplies their container underneath.
            if (k == Kind.BUTTON || k == Kind.SLIDER || k == Kind.CYCLE) {
                w.visible = false;
            }
            proxies.add(p);
        }
        Md3UI.LOGGER.debug("[MD3UI] adopted {} widgets on {}",
                proxies.size(), screen.getClass().getSimpleName());
    }

    /** Put every widget back exactly as it was; used when rendering fails. */
    public void restore() {
        for (Proxy p : proxies) {
            p.widget.visible = p.wasVisible;
        }
        proxies.clear();
    }

    private static Kind classify(AbstractWidget w) {
        String n = w.getClass().getSimpleName();
        String full = w.getClass().getName();
        if (n.contains("EditBox")) return Kind.TEXT_FIELD;
        if (n.contains("Slider")) return Kind.SLIDER;
        if (n.contains("CycleButton")) return Kind.CYCLE;
        if (n.contains("Button")) return Kind.BUTTON;
        if (n.contains("List") || n.contains("Selection")) return Kind.LIST;
        // Anonymous subclasses of Button are common in vanilla option screens.
        if (full.contains("Button")) return Kind.BUTTON;
        return Kind.UNKNOWN;
    }

    /** Slider progress 0..1, read reflectively; -1 when unavailable. */
    private static double sliderValue(AbstractWidget w) {
        for (String field : new String[] {"value", "f_93577_"}) {
            try {
                java.lang.reflect.Field f = findField(w.getClass(), field);
                if (f == null) continue;
                f.setAccessible(true);
                Object v = f.get(w);
                if (v instanceof Double) return (Double) v;
                if (v instanceof Float) return ((Float) v).doubleValue();
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // fall through to the next candidate
            }
        }
        return -1;
    }

    private static java.lang.reflect.Field findField(Class<?> c, String name) {
        while (c != null && c != Object.class) {
            try {
                return c.getDeclaredField(name);
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            }
        }
        return null;
    }

    public void tick(double dt, int mouseX, int mouseY) {
        enter.advance(dt);
        for (Proxy p : proxies) {
            AbstractWidget w = p.widget;
            boolean hovered = w.isActive() && p.wasVisible
                    && mouseX >= w.getX() && mouseX < w.getX() + w.getWidth()
                    && mouseY >= w.getY() && mouseY < w.getY() + w.getHeight();
            p.hover.target(hovered ? (w.isFocused() ? Md3Tokens.STATE_FOCUS
                    : Md3Tokens.STATE_HOVER) : (w.isFocused() ? Md3Tokens.STATE_FOCUS : 0));
            p.hover.advance(dt);
            p.press.target(hovered && isMouseDown() ? 1 : 0);
            p.press.advance(dt);
        }
    }

    private static boolean isMouseDown() {
        try {
            long window = net.minecraft.client.Minecraft.getInstance()
                    .getWindow().getWindow();
            return org.lwjgl.glfw.GLFW.glfwGetMouseButton(window,
                    org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT)
                    == org.lwjgl.glfw.GLFW.GLFW_PRESS;
        } catch (RuntimeException | LinkageError e) {
            return false;
        }
    }

    public void render(Md3Canvas c, Md3Scheme s, boolean inWorld) {
        float t = config.reducedMotion ? 1f : (float) enter.value();

        // Background: scrim over a world, tonal surface otherwise.
        if (inWorld) {
            int a = Math.round(Md3Tokens.SCRIM_OPACITY * 255 * t * config.scrimStrength);
            c.fillRect(0, 0, c.width(), c.height(), Argb.withAlpha(s.scrim, Math.min(255, a)));
        } else if (!config.keepBackground) {
            c.fillRect(0, 0, c.width(), c.height(), s.surface);
        } else {
            // Keep the panorama but tone it down so MD3 surfaces read clearly.
            c.fillRect(0, 0, c.width(), c.height(),
                    Argb.withAlpha(s.surface, Math.round(190 * t)));
        }

        c.pushAlpha(t);
        c.pushTranslate(0, (1f - t) * 5f);

        renderHeading(c, s);
        for (Proxy p : proxies) {
            if (!p.wasVisible || p.kind == Kind.UNKNOWN) continue;
            drawProxy(c, s, p);
        }

        c.popTransform();
        c.popAlpha();
    }

    private void renderHeading(Md3Canvas c, Md3Scheme s) {
        if (heading == null || heading.isEmpty()) return;
        float h = Md3Tokens.APP_BAR_HEIGHT;
        c.fillRect(0, 0, c.width(), h, Argb.withAlpha(s.surfaceContainer, 210));
        c.drawText(heading, Md3Tokens.SPACE_XL, (h - c.lineHeight()) / 2f,
                s.onSurface, false);
    }

    private void drawProxy(Md3Canvas c, Md3Scheme s, Proxy p) {
        AbstractWidget w = p.widget;
        float x = w.getX(), y = w.getY();
        float ww = w.getWidth(), hh = w.getHeight();
        if (ww <= 0 || hh <= 0) return;

        boolean on = w.isActive();
        float ov = (float) p.hover.value();
        float pr = config.reducedMotion ? 0f : (float) p.press.value();
        String label = plain(w.getMessage() == null ? "" : w.getMessage().getString());

        switch (p.kind) {
            case SLIDER:
                drawSlider(c, s, p, x, y, ww, hh, label, on, ov);
                break;
            case CYCLE:
                drawCycle(c, s, x, y, ww, hh, label, on, ov, pr);
                break;
            case TEXT_FIELD:
                drawTextField(c, s, x, y, ww, hh, label, on, w.isFocused());
                break;
            case LIST:
                Shapes.roundRect(c, x, y, ww, hh, Md3Tokens.SHAPE_MD,
                        s.surfaceContainerLow);
                break;
            case BUTTON:
            default:
                drawButton(c, s, x, y, ww, hh, label, on, ov, pr, w.isFocused());
                break;
        }
    }

    private void drawButton(Md3Canvas c, Md3Scheme s, float x, float y, float w, float h,
                            String label, boolean enabled, float overlay, float press,
                            boolean focused) {
        // Shape morph: a held button eases from pill toward a rounded rectangle.
        float radius = Motion.lerp(h / 2f, Md3Tokens.SHAPE_SM, press);

        int container = enabled ? s.primary
                : Argb.withAlpha(s.onSurface, Math.round(Md3Tokens.DISABLED_CONTAINER * 255));
        int content = enabled ? s.onPrimary
                : Argb.withAlpha(s.onSurface, Math.round(Md3Tokens.DISABLED_CONTENT * 255));

        // Destructive and back-style actions get their own roles.
        String low = label.toLowerCase(java.util.Locale.ROOT);
        if (enabled && (low.startsWith("delete") || low.contains("удал"))) {
            container = s.error;
            content = s.onError;
        } else if (enabled && (low.equals("cancel") || low.equals("back")
                || low.contains("отмен") || low.contains("назад")
                || low.equals("done") || low.contains("готов"))) {
            container = s.secondaryContainer;
            content = s.onSecondaryContainer;
        }

        Shapes.roundRect(c, x, y, w, h, radius, container);

        if (focused) {
            Shapes.roundRectOutline(c, x - 1.5f, y - 1.5f, w + 3, h + 3, radius + 1.5f,
                    Md3Tokens.STROKE_FOCUS, s.primary);
        }
        if (overlay > 0.002f) {
            Shapes.roundRect(c, x, y, w, h, radius,
                    Argb.withAlpha(content, Math.round(overlay * 255)));
        }

        float tw = c.textWidth(label);
        // Long vanilla labels must not spill outside the container.
        c.pushClip(x + 2, y, w - 4, h);
        c.drawText(label, x + (w - tw) / 2f, y + (h - c.lineHeight()) / 2f + 0.5f,
                content, false);
        c.popClip();
    }

    private void drawSlider(Md3Canvas c, Md3Scheme s, Proxy p, float x, float y,
                            float w, float h, String label, boolean enabled, float overlay) {
        double v = sliderValue(p.widget);
        // Vanilla sliders bake the value into their label, so an unreadable
        // field is not fatal: draw a full track and rely on the text.
        float frac = v < 0 ? 1f : (float) Math.max(0, Math.min(1, v));

        float trackH = Md3Tokens.SLIDER_TRACK;
        float trackY = y + (h - trackH) / 2f;
        int active = enabled ? s.primary
                : Argb.withAlpha(s.onSurface, Math.round(Md3Tokens.DISABLED_CONTENT * 255));
        int inactive = enabled ? s.secondaryContainer
                : Argb.withAlpha(s.onSurface, Math.round(Md3Tokens.DISABLED_CONTAINER * 255));

        float handleW = 4f;
        float handleX = x + (w - handleW) * frac;
        float gap = 3f;

        float inactiveStart = handleX + handleW + gap;
        if (inactiveStart < x + w) {
            Shapes.pill(c, inactiveStart, trackY, x + w - inactiveStart, trackH, inactive);
        }
        if (handleX - gap > x) {
            Shapes.pill(c, x, trackY, handleX - gap - x, trackH, active);
        }
        if (overlay > 0.002f && enabled) {
            Shapes.circle(c, handleX + handleW / 2f, y + h / 2f, trackH / 2f + 5f,
                    Argb.withAlpha(s.primary, Math.round(overlay * 255)));
        }
        Shapes.pill(c, handleX, y + (h - trackH - 6f) / 2f, handleW, trackH + 6f, active);

        // Label above the track, matching the MD3 slider layout.
        int textColor = enabled ? s.onSurface
                : Argb.withAlpha(s.onSurface, Math.round(Md3Tokens.DISABLED_CONTENT * 255));
        c.pushClip(x, y - c.lineHeight() - 1f, w, c.lineHeight() + 1f);
        c.drawText(label, x, y - c.lineHeight() - 0.5f, textColor, false);
        c.popClip();
    }

    private void drawCycle(Md3Canvas c, Md3Scheme s, float x, float y, float w, float h,
                           String label, boolean enabled, float overlay, float press) {
        // A cycling option reads as a tonal button with a trailing chevron.
        float radius = Motion.lerp(Md3Tokens.SHAPE_LG, Md3Tokens.SHAPE_SM, press);
        int container = enabled ? s.secondaryContainer
                : Argb.withAlpha(s.onSurface, Math.round(Md3Tokens.DISABLED_CONTAINER * 255));
        int content = enabled ? s.onSecondaryContainer
                : Argb.withAlpha(s.onSurface, Math.round(Md3Tokens.DISABLED_CONTENT * 255));

        Shapes.roundRect(c, x, y, w, h, radius, container);
        if (overlay > 0.002f) {
            Shapes.roundRect(c, x, y, w, h, radius,
                    Argb.withAlpha(content, Math.round(overlay * 255)));
        }

        c.pushClip(x + 3, y, w - 14, h);
        c.drawText(label, x + Md3Tokens.SPACE_MD, y + (h - c.lineHeight()) / 2f + 0.5f,
                content, false);
        c.popClip();

        // Trailing chevron built from quads.
        float cx = x + w - 9f, cy = y + h / 2f;
        for (int i = 0; i < 3; i++) {
            c.fillRect(cx + i, cy - 2 + i, 1.4f, 1.4f, content);
            c.fillRect(cx + i, cy + 2 - i, 1.4f, 1.4f, content);
        }
    }

    private void drawTextField(Md3Canvas c, Md3Scheme s, float x, float y, float w, float h,
                               String label, boolean enabled, boolean focused) {
        // Only the container is drawn: the vanilla EditBox keeps rendering its
        // own text and caret, which is far safer than reimplementing its state.
        float radius = Md3Tokens.SHAPE_XS + 2f;
        int accent = focused ? s.primary : s.outline;
        Shapes.roundRect(c, x - 2, y - 2, w + 4, h + 4, radius, s.surfaceContainerHighest);
        Shapes.roundRectOutline(c, x - 2, y - 2, w + 4, h + 4, radius,
                focused ? Md3Tokens.STROKE_FOCUS : Md3Tokens.STROKE_THIN, accent);
    }

    /** Strip vanilla's section signs and formatting codes. */
    private static String plain(String in) {
        if (in == null) return "";
        StringBuilder sb = new StringBuilder(in.length());
        for (int i = 0; i < in.length(); i++) {
            char ch = in.charAt(i);
            if (ch == '\u00A7' && i + 1 < in.length()) {
                i++;
                continue;
            }
            sb.append(ch);
        }
        return sb.toString();
    }

    /** Text fields must keep painting themselves; report whether one was adopted. */
    public boolean hasTextField() {
        for (Proxy p : proxies) if (p.kind == Kind.TEXT_FIELD) return true;
        return false;
    }
}
