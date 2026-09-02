package dev.md3ui.mod;

import dev.md3ui.mod.compat.RendererDetect;
import dev.md3ui.mod.screen.ScreenRouter;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client entry point.
 *
 * <p>Deliberately thin. The mod hooks Fabric's {@code ScreenEvents} and draws on
 * top of vanilla screens rather than replacing their classes, which is what keeps
 * it compatible with other UI mods: nothing is mixed into, no vanilla screen
 * class is swapped, and any mod that adds widgets to a screen still works because
 * its widgets keep receiving events.
 */
public final class Md3UI implements ClientModInitializer {

    public static final String MOD_ID = "md3ui";
    public static final Logger LOGGER = LoggerFactory.getLogger("MD3UI");

    private static Md3Config config;
    private static ScreenRouter router;

    @Override
    public void onInitializeClient() {
        config = Md3Config.load(FabricLoader.getInstance().getConfigDir());

        RendererDetect.probe();
        LOGGER.info("[MD3UI] starting on {} (renderer: {})",
                FabricLoader.getInstance().getModContainer("minecraft")
                        .map(m -> m.getMetadata().getVersion().getFriendlyString())
                        .orElse("unknown"),
                RendererDetect.describe());

        if (!config.enabled) {
            LOGGER.info("[MD3UI] disabled in config, vanilla UI left untouched");
            return;
        }

        router = new ScreenRouter(config);
        router.register();
    }

    public static Md3Config config() {
        if (config == null) {
            config = Md3Config.load(FabricLoader.getInstance().getConfigDir());
        }
        return config;
    }

    public static ScreenRouter router() { return router; }
}
