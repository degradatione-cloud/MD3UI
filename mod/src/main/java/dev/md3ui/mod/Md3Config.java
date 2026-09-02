package dev.md3ui.mod;

import dev.md3ui.core.theme.Md3Scheme;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Flat key=value config, parsed by hand.
 *
 * <p>No Gson, no JSON library: the mod ships zero runtime dependencies beyond
 * Fabric Loader itself. Fewer jars means nothing to shade, nothing to clash with
 * another mod's bundled copy, and a jar small enough that including it in a pack
 * costs nothing.
 */
public final class Md3Config {

    private static final String FILE = "md3ui.properties";

    /** Seed colour for the generated scheme. */
    public int seedColor = 0xFF6750A4;
    public boolean dark = true;
    public Md3Scheme.Variant variant = Md3Scheme.Variant.VIBRANT;

    /** Master switch. When false the mod renders nothing and vanilla is untouched. */
    public boolean enabled = true;

    /** Per-screen replacement toggles, so users can opt out selectively. */
    public boolean replaceTitle = true;
    public boolean replaceOptions = true;
    public boolean replacePause = true;
    public boolean replaceWorldSelect = true;
    public boolean replaceMultiplayer = true;

    /** Honour accessibility: kill ripple and springs. */
    public boolean reducedMotion = false;

    /** Draw the vanilla panorama/background behind MD3 surfaces. */
    public boolean keepBackground = true;

    /** Scrim opacity multiplier when a world is visible behind the screen. */
    public float scrimStrength = 1.0f;

    private transient Path path;
    private transient Md3Scheme cached;
    private transient boolean cachedDark;

    public static Md3Config load(Path configDir) {
        Md3Config c = new Md3Config();
        c.path = configDir.resolve(FILE);
        if (!Files.exists(c.path)) {
            c.save();
            return c;
        }
        try {
            for (String line : Files.readAllLines(c.path, StandardCharsets.UTF_8)) {
                String s = line.trim();
                if (s.isEmpty() || s.startsWith("#")) continue;
                int eq = s.indexOf('=');
                if (eq <= 0) continue;
                String k = s.substring(0, eq).trim();
                String v = s.substring(eq + 1).trim();
                c.apply(k, v);
            }
        } catch (IOException | RuntimeException e) {
            Md3UI.LOGGER.warn("[MD3UI] config unreadable, using defaults: {}", e.toString());
        }
        return c;
    }

    private void apply(String k, String v) {
        switch (k) {
            case "seedColor": seedColor = parseColor(v, seedColor); break;
            case "dark": dark = Boolean.parseBoolean(v); break;
            case "variant":
                try {
                    variant = Md3Scheme.Variant.valueOf(v.toUpperCase(java.util.Locale.ROOT));
                } catch (IllegalArgumentException ignored) {
                    // keep default
                }
                break;
            case "enabled": enabled = Boolean.parseBoolean(v); break;
            case "replaceTitle": replaceTitle = Boolean.parseBoolean(v); break;
            case "replaceOptions": replaceOptions = Boolean.parseBoolean(v); break;
            case "replacePause": replacePause = Boolean.parseBoolean(v); break;
            case "replaceWorldSelect": replaceWorldSelect = Boolean.parseBoolean(v); break;
            case "replaceMultiplayer": replaceMultiplayer = Boolean.parseBoolean(v); break;
            case "reducedMotion": reducedMotion = Boolean.parseBoolean(v); break;
            case "keepBackground": keepBackground = Boolean.parseBoolean(v); break;
            case "scrimStrength": scrimStrength = parseFloat(v, scrimStrength); break;
            default: break;
        }
    }

    private static int parseColor(String v, int fallback) {
        try {
            String s = v.startsWith("#") ? v.substring(1) : v;
            if (s.startsWith("0x") || s.startsWith("0X")) s = s.substring(2);
            long parsed = Long.parseLong(s, 16);
            return (int) (parsed | 0xFF000000L);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static float parseFloat(String v, float fallback) {
        try {
            return Math.max(0f, Math.min(2f, Float.parseFloat(v)));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** The resolved scheme, rebuilt only when the theme inputs change. */
    public Md3Scheme scheme() {
        if (cached == null || cachedDark != dark) {
            cached = Md3Scheme.fromSeed(seedColor, dark, variant);
            cachedDark = dark;
        }
        return cached;
    }

    public void invalidateScheme() { cached = null; }

    public void save() {
        if (path == null) return;
        Map<String, String> out = new LinkedHashMap<>();
        out.put("enabled", String.valueOf(enabled));
        out.put("seedColor", String.format("#%06X", seedColor & 0xFFFFFF));
        out.put("dark", String.valueOf(dark));
        out.put("variant", variant.name());
        out.put("reducedMotion", String.valueOf(reducedMotion));
        out.put("keepBackground", String.valueOf(keepBackground));
        out.put("scrimStrength", String.valueOf(scrimStrength));
        out.put("replaceTitle", String.valueOf(replaceTitle));
        out.put("replaceOptions", String.valueOf(replaceOptions));
        out.put("replacePause", String.valueOf(replacePause));
        out.put("replaceWorldSelect", String.valueOf(replaceWorldSelect));
        out.put("replaceMultiplayer", String.valueOf(replaceMultiplayer));

        StringBuilder sb = new StringBuilder();
        sb.append("# MD3UI configuration\n");
        sb.append("# seedColor: any hex colour, the whole scheme is generated from it\n");
        sb.append("# variant: TONAL_SPOT | NEUTRAL | VIBRANT | CONTENT\n");
        for (Map.Entry<String, String> e : out.entrySet()) {
            sb.append(e.getKey()).append('=').append(e.getValue()).append('\n');
        }
        try {
            Files.createDirectories(path.getParent());
            Files.write(path, sb.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            Md3UI.LOGGER.warn("[MD3UI] could not save config: {}", e.toString());
        }
    }
}
