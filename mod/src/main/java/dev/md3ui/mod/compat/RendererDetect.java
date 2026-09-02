package dev.md3ui.mod.compat;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Detects which rendering mods are present, purely to report it.
 *
 * <p>MD3UI does not branch its drawing on the result &mdash; that is the whole
 * point of the {@code fill}-only canvas. This exists so a bug report says which
 * renderer was active, and so the in-game theme screen can show it. Detection is
 * by mod id through Fabric Loader, never by touching the other mod's classes,
 * which would create a hard dependency and a crash when it is absent.
 */
public final class RendererDetect {
    private RendererDetect() {}

    private static boolean vulkanMod;
    private static boolean sodium;
    private static boolean iris;
    private static boolean embeddium;
    private static boolean probed;

    public static void probe() {
        FabricLoader l = FabricLoader.getInstance();
        vulkanMod = l.isModLoaded("vulkanmod");
        sodium = l.isModLoaded("sodium");
        iris = l.isModLoaded("iris");
        embeddium = l.isModLoaded("embeddium");
        probed = true;
    }

    public static boolean vulkanMod() { ensure(); return vulkanMod; }
    public static boolean sodium() { ensure(); return sodium; }
    public static boolean iris() { ensure(); return iris; }

    /** Human-readable renderer summary for logs and the theme screen. */
    public static String describe() {
        ensure();
        StringBuilder sb = new StringBuilder();
        if (vulkanMod) sb.append("VulkanMod");
        if (sodium) { if (sb.length() > 0) sb.append(" + "); sb.append("Sodium"); }
        if (embeddium) { if (sb.length() > 0) sb.append(" + "); sb.append("Embeddium"); }
        if (iris) { if (sb.length() > 0) sb.append(" + "); sb.append("Iris"); }
        if (sb.length() == 0) sb.append("vanilla OpenGL");
        return sb.toString();
    }

    private static void ensure() { if (!probed) probe(); }
}
