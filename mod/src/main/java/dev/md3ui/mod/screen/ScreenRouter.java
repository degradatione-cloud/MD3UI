package dev.md3ui.mod.screen;

import dev.md3ui.core.gfx.Md3Canvas;
import dev.md3ui.core.theme.Md3Scheme;
import dev.md3ui.mod.Md3Config;
import dev.md3ui.mod.Md3UI;
import dev.md3ui.mod.render.MinecraftCanvas;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;

import java.util.List;

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
 */
public final class ScreenRouter {

    private final Md3Config config;
    private final MinecraftCanvas canvas = new MinecraftCanvas();
    private AdoptedScreen active;
    private long lastFrameNanos;

    public ScreenRouter(Md3Config config) {
        this.config = config;
    }

    public void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!config.enabled) return;
            if (!shouldSkin(screen)) {
                active = null;
                return;
            }
            try {
                active = new AdoptedScreen(screen, config);
                // Vanilla widgets stay functional but invisible; MD3 draws in
                // their place and forwards clicks to them.
                active.adopt(Screens.getButtons(screen));
            } catch (RuntimeException e) {
                Md3UI.LOGGER.warn("[MD3UI] could not adopt {}: {}",
                        screen.getClass().getSimpleName(), e.toString());
                active = null;
                return;
            }

            ScreenEvents.beforeRender(screen).register((s, graphics, mouseX, mouseY, delta) -> {
                if (active == null || active.screen() != s) return;
                double dt = frameDelta();
                Md3Scheme scheme = config.scheme();
                float w = s.width, h = s.height;
                Md3Canvas c = canvas.begin(graphics, w, h);
                try {
                    active.tick(dt, mouseX, mouseY);
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
        String n = screen.getClass().getName();
        if (n.startsWith("dev.md3ui")) return false;
        String simple = screen.getClass().getSimpleName();

        if (config.replaceTitle && simple.equals("TitleScreen")) return true;
        if (config.replaceOptions && (simple.equals("OptionsScreen")
                || simple.equals("VideoSettingsScreen")
                || simple.equals("SoundOptionsScreen")
                || simple.equals("SkinCustomizationScreen")
                || simple.equals("LanguageSelectScreen")
                || simple.equals("AccessibilityOptionsScreen")
                || simple.equals("ChatOptionsScreen")
                || simple.equals("OnlineOptionsScreen")
                || simple.equals("ControlsScreen")
                || simple.equals("MouseSettingsScreen")
                || simple.equals("KeyBindsScreen"))) return true;
        if (config.replacePause && simple.equals("PauseScreen")) return true;
        if (config.replaceWorldSelect && (simple.equals("SelectWorldScreen")
                || simple.equals("CreateWorldScreen"))) return true;
        if (config.replaceMultiplayer && (simple.equals("JoinMultiplayerScreen")
                || simple.equals("ServerSelectionList"))) return true;
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
