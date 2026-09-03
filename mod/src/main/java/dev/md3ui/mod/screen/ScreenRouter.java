package dev.md3ui.mod.screen;

import dev.md3ui.core.gfx.Md3Canvas;
import dev.md3ui.core.theme.Md3Scheme;
import dev.md3ui.mod.Md3Config;
import dev.md3ui.mod.Md3UI;
import dev.md3ui.mod.render.MinecraftCanvas;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;

/**
 * Decides which vanilla screens get an MD3 skin, and drives the overlay.
 *
 * <p>Strategy: keep the vanilla screen, hide its widgets, and draw an MD3 layer
 * that forwards interaction to the original widgets. Nothing is subclassed or
 * mixed into.
 *
 * <p>Why not replace the {@code Screen} classes outright, which would be simpler
 * to draw? Because vanilla screens carry behaviour that is genuinely hard to
 * reproduce and easy to break: world deletion confirmations, resource-pack
 * reloads, realms plumbing, and every other mod's injected widgets. Adopting the
 * existing widgets means a modpack's added buttons keep working and still get the
 * MD3 look, and if MD3UI fails to recognise a control it stays visible in vanilla
 * form rather than becoming unclickable.
 *
 * <p><b>Screens are matched with {@code instanceof}, never by class name.</b> In a
 * production jar Minecraft's classes carry intermediary names, so
 * {@code getClass().getSimpleName()} returns {@code class_442} rather than
 * {@code TitleScreen}: name matching works in a dev environment and silently
 * matches nothing for actual players. Real class references are remapped by Loom
 * at build time and therefore work in both.
 */
public final class ScreenRouter {

    private final Md3Config config;
    private final MinecraftCanvas canvas = new MinecraftCanvas();
    private AdoptedScreen active;
    private long lastFrameNanos;

    // Press state is deliberately not tracked for adopted vanilla widgets.
    //
    // Three routes exist and all three are worse than doing without:
    // ScreenMouseEvents changed its callback signature twice in this range
    // ((screen, x, y, button) through 1.21.8, then `Click`, then
    // `MouseButtonEvent`), polling GLFW needs the native window handle whose
    // accessor is not stable either, and reflecting into MouseHandler is a
    // guess that breaks silently. Hover and focus layers cover the visual
    // feedback that matters; MD3UI's own widgets keep full press animation
    // including the Expressive shape morph, since they receive events directly.

    public ScreenRouter(Md3Config config) {
        this.config = config;
    }

    public void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!config.enabled) return;

            // Fabric can emit AFTER_INIT more than once for one screen during
            // its initial layout pass. The first adoption deliberately sets
            // vanilla widgets invisible; if the second event recreated the
            // wrapper it would record those already-hidden widgets as
            // `wasVisible=false` and the renderer would correctly-but-uselessly
            // skip every proxy. Reuse the existing wrapper instead.
            if (active != null && active.screen() == screen) {
                Md3UI.LOGGER.debug("[MD3UI] ignoring duplicate init for {}",
                        screen.getClass().getName());
                return;
            }

            if (!shouldSkin(screen)) {
                active = null;
                return;
            }
            try {
                active = new AdoptedScreen(screen, config);
                // Vanilla widgets stay functional but invisible; MD3 draws in
                // their place and forwards clicks to them.
                active.adopt(Screens.getButtons(screen));
                Md3UI.LOGGER.info("[MD3UI] skinning {} ({} widgets)",
                        screen.getClass().getName(), Screens.getButtons(screen).size());
            } catch (RuntimeException e) {
                Md3UI.LOGGER.warn("[MD3UI] could not adopt {}: {}",
                        screen.getClass().getName(), e.toString());
                active = null;
                return;
            }

            // This must be AFTER_RENDER. BEFORE_RENDER was a deceptively clean
            // compile-time success but vanilla painted its screen background and
            // widget sprites immediately afterwards, covering the MD3 layer in
            // the released game. AFTER_RENDER leaves existing unadopted widgets
            // (and other mods' overlays) above us while the adopted widgets stay
            // hidden, exactly the intended stacking order.
            ScreenEvents.afterRender(screen).register((s, graphics, mouseX, mouseY, delta) -> {
                if (active == null || active.screen() != s) return;
                double dt = frameDelta();
                Md3Scheme scheme = config.scheme();
                float w = s.width, h = s.height;
                Md3Canvas c = canvas.begin(graphics, w, h);
                try {
                    active.tick(dt, mouseX, mouseY, false);
                    active.render(c, scheme, inWorld());
                } catch (RuntimeException e) {
                    Md3UI.LOGGER.error("[MD3UI] render failure, restoring vanilla UI", e);
                    active.restore();
                    active = null;
                } finally {
                    canvas.end();
                }
            });

            ScreenEvents.remove(screen).register(s -> {
                if (active != null && active.screen() == s) active = null;
            });
        });
    }

    /** Which vanilla screens are in scope, per config. */
    private boolean shouldSkin(Screen screen) {
        if (config.replaceTitle && screen instanceof TitleScreen) return true;

        // OptionsSubScreen is the common parent of every settings sub-page
        // (video, sound, controls, key binds, language, chat, skin, online,
        // accessibility). Matching the base class instead of enumerating
        // subclasses covers pages that moved package between versions, and
        // picks up new ones for free.
        if (config.replaceOptions && (screen instanceof OptionsScreen
                || screen instanceof OptionsSubScreen)) return true;

        if (config.replacePause && screen instanceof PauseScreen) return true;
        if (config.replaceWorldSelect && screen instanceof SelectWorldScreen) return true;
        if (config.replaceMultiplayer && screen instanceof JoinMultiplayerScreen) return true;

        return false;
    }

    private boolean inWorld() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null;
    }

    private double frameDelta() {
        long now = System.nanoTime();
        if (lastFrameNanos == 0) {
            lastFrameNanos = now;
            return 1.0 / 60.0;
        }
        double dt = (now - lastFrameNanos) / 1_000_000_000.0;
        lastFrameNanos = now;
        // Clamp so a world-load stall does not fast-forward every animation.
        return Math.max(0.0, Math.min(dt, 0.1));
    }

    public AdoptedScreen active() { return active; }
}
