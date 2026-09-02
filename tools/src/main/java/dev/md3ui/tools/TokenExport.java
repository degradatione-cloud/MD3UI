package dev.md3ui.tools;

import dev.md3ui.core.color.Argb;
import dev.md3ui.core.color.TonalPalette;
import dev.md3ui.core.theme.Md3Scheme;
import dev.md3ui.core.theme.Md3Tokens;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Exports the generated theme as design tokens for Figma.
 *
 * <p>Two files, because Figma has two import paths and designers use both:
 *
 * <ul>
 *   <li>{@code tokens.json} &mdash; W3C Design Tokens Community Group format,
 *       which the Tokens Studio plugin imports directly as colour styles and
 *       variables.</li>
 *   <li>{@code variables.json} &mdash; Figma's own REST variables payload shape,
 *       for teams that push tokens through the Figma API instead.</li>
 * </ul>
 *
 * <p>The point of generating rather than hand-maintaining these: the mod's colour
 * scheme is computed from a seed at runtime, so a hand-drawn Figma palette would
 * describe only one of infinitely many themes and would rot on the first tweak to
 * the tone ladder. Exporting from the same solver keeps design and build in sync
 * by construction. Written without a JSON library to keep the module dependency-free.
 */
public final class TokenExport {

    public static void main(String[] args) throws Exception {
        File dir = new File(args.length > 0 ? args[0] : "design");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("cannot create " + dir);
        }

        int seed = 0xFF6750A4;
        Md3Scheme dark = Md3Scheme.fromSeed(seed, true, Md3Scheme.Variant.VIBRANT);
        Md3Scheme light = Md3Scheme.fromSeed(seed, false, Md3Scheme.Variant.VIBRANT);

        String tokens = buildW3cTokens(seed, light, dark);
        Files.write(new File(dir, "tokens.json").toPath(),
                tokens.getBytes(StandardCharsets.UTF_8));

        String vars = buildFigmaVariables(light, dark);
        Files.write(new File(dir, "variables.json").toPath(),
                vars.getBytes(StandardCharsets.UTF_8));

        System.out.println("design tokens written to " + dir.getAbsolutePath());
    }

    private static String buildW3cTokens(int seed, Md3Scheme light, Md3Scheme dark) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"$description\": \"MD3UI generated design tokens. ")
          .append("Seed ").append(Argb.hex(seed))
          .append(", CIELAB tone ladder. Regenerate with ./gradlew :tools:designTokens\",\n");

        // Tonal palettes, the source of every role below.
        sb.append("  \"palette\": {\n");
        String[] names = {"primary", "secondary", "tertiary", "neutral", "neutralVariant",
                "error"};
        TonalPalette[] pals = {
                TonalPalette.fromColor(seed),
                TonalPalette.fromColorWithChroma(seed, 24),
                TonalPalette.of(dev.md3ui.core.color.Cie.rgbToLch(seed)[2] + 60, 32),
                TonalPalette.fromColorWithChroma(seed, 8),
                TonalPalette.fromColorWithChroma(seed, 12),
                TonalPalette.of(25, 84)
        };
        int[] tones = {0, 10, 20, 30, 40, 50, 60, 70, 80, 90, 95, 99, 100};
        for (int i = 0; i < names.length; i++) {
            sb.append("    \"").append(names[i]).append("\": {\n");
            for (int t = 0; t < tones.length; t++) {
                sb.append("      \"").append(tones[t]).append("\": { \"$type\": \"color\", ")
                  .append("\"$value\": \"").append(Argb.hex(pals[i].tone(tones[t])))
                  .append("\" }");
                sb.append(t < tones.length - 1 ? ",\n" : "\n");
            }
            sb.append("    }").append(i < names.length - 1 ? ",\n" : "\n");
        }
        sb.append("  },\n");

        sb.append("  \"scheme\": {\n");
        sb.append("    \"light\": {\n");
        appendRoles(sb, light, "      ");
        sb.append("    },\n");
        sb.append("    \"dark\": {\n");
        appendRoles(sb, dark, "      ");
        sb.append("    }\n");
        sb.append("  },\n");

        // Shape and spacing scales, so a designer lays out with the real numbers.
        sb.append("  \"shape\": {\n");
        appendDim(sb, "none", Md3Tokens.SHAPE_NONE, true);
        appendDim(sb, "extraSmall", Md3Tokens.SHAPE_XS, true);
        appendDim(sb, "small", Md3Tokens.SHAPE_SM, true);
        appendDim(sb, "medium", Md3Tokens.SHAPE_MD, true);
        appendDim(sb, "large", Md3Tokens.SHAPE_LG, true);
        appendDim(sb, "extraLarge", Md3Tokens.SHAPE_XL, false);
        sb.append("  },\n");

        sb.append("  \"spacing\": {\n");
        appendDim(sb, "xs", Md3Tokens.SPACE_XS, true);
        appendDim(sb, "sm", Md3Tokens.SPACE_SM, true);
        appendDim(sb, "md", Md3Tokens.SPACE_MD, true);
        appendDim(sb, "lg", Md3Tokens.SPACE_LG, true);
        appendDim(sb, "xl", Md3Tokens.SPACE_XL, true);
        appendDim(sb, "xxl", Md3Tokens.SPACE_XXL, false);
        sb.append("  },\n");

        sb.append("  \"state\": {\n");
        appendNum(sb, "hover", Md3Tokens.STATE_HOVER, true);
        appendNum(sb, "focus", Md3Tokens.STATE_FOCUS, true);
        appendNum(sb, "pressed", Md3Tokens.STATE_PRESS, true);
        appendNum(sb, "dragged", Md3Tokens.STATE_DRAG, true);
        appendNum(sb, "selected", Md3Tokens.STATE_SELECTED, true);
        appendNum(sb, "disabledContent", Md3Tokens.DISABLED_CONTENT, true);
        appendNum(sb, "disabledContainer", Md3Tokens.DISABLED_CONTAINER, false);
        sb.append("  },\n");

        // Motion: spring constants rather than durations, matching MD3 Expressive.
        sb.append("  \"motion\": {\n");
        sb.append("    \"$description\": \"MD3 Expressive springs. ")
          .append("damping/stiffness pairs, not durations.\",\n");
        sb.append("    \"spatialFast\": { \"damping\": 0.9, \"stiffness\": 1400 },\n");
        sb.append("    \"spatialDefault\": { \"damping\": 0.9, \"stiffness\": 700 },\n");
        sb.append("    \"expressiveDefault\": { \"damping\": 0.6, \"stiffness\": 380 },\n");
        sb.append("    \"effectFast\": { \"damping\": 1.0, \"stiffness\": 3800 }\n");
        sb.append("  }\n");
        sb.append("}\n");
        return sb.toString();
    }

    private static void appendRoles(StringBuilder sb, Md3Scheme s, String pad) {
        Map<String, Integer> roles = roleMap(s);
        int i = 0;
        for (Map.Entry<String, Integer> e : roles.entrySet()) {
            sb.append(pad).append('"').append(e.getKey()).append("\": { \"$type\": \"color\", ")
              .append("\"$value\": \"").append(Argb.hex(e.getValue())).append("\" }");
            sb.append(++i < roles.size() ? ",\n" : "\n");
        }
    }

    private static Map<String, Integer> roleMap(Md3Scheme s) {
        Map<String, Integer> m = new LinkedHashMap<>();
        m.put("primary", s.primary);
        m.put("onPrimary", s.onPrimary);
        m.put("primaryContainer", s.primaryContainer);
        m.put("onPrimaryContainer", s.onPrimaryContainer);
        m.put("secondary", s.secondary);
        m.put("onSecondary", s.onSecondary);
        m.put("secondaryContainer", s.secondaryContainer);
        m.put("onSecondaryContainer", s.onSecondaryContainer);
        m.put("tertiary", s.tertiary);
        m.put("onTertiary", s.onTertiary);
        m.put("tertiaryContainer", s.tertiaryContainer);
        m.put("onTertiaryContainer", s.onTertiaryContainer);
        m.put("error", s.error);
        m.put("onError", s.onError);
        m.put("errorContainer", s.errorContainer);
        m.put("onErrorContainer", s.onErrorContainer);
        m.put("surface", s.surface);
        m.put("onSurface", s.onSurface);
        m.put("onSurfaceVariant", s.onSurfaceVariant);
        m.put("surfaceDim", s.surfaceDim);
        m.put("surfaceBright", s.surfaceBright);
        m.put("surfaceContainerLowest", s.surfaceContainerLowest);
        m.put("surfaceContainerLow", s.surfaceContainerLow);
        m.put("surfaceContainer", s.surfaceContainer);
        m.put("surfaceContainerHigh", s.surfaceContainerHigh);
        m.put("surfaceContainerHighest", s.surfaceContainerHighest);
        m.put("inverseSurface", s.inverseSurface);
        m.put("inverseOnSurface", s.inverseOnSurface);
        m.put("inversePrimary", s.inversePrimary);
        m.put("outline", s.outline);
        m.put("outlineVariant", s.outlineVariant);
        return m;
    }

    private static void appendDim(StringBuilder sb, String name, float v, boolean comma) {
        sb.append("    \"").append(name).append("\": { \"$type\": \"dimension\", ")
          .append("\"$value\": \"").append(trim(v * 2)).append("px\" }")
          .append(comma ? ",\n" : "\n");
    }

    private static void appendNum(StringBuilder sb, String name, float v, boolean comma) {
        sb.append("    \"").append(name).append("\": { \"$type\": \"number\", ")
          .append("\"$value\": ").append(trim(v)).append(" }")
          .append(comma ? ",\n" : "\n");
    }

    private static String trim(float v) {
        if (v == Math.rint(v)) return String.valueOf((int) v);
        return String.valueOf(v);
    }

    /** Figma REST variables shape: collections, modes, variable values. */
    private static String buildFigmaVariables(Md3Scheme light, Md3Scheme dark) {
        StringBuilder sb = new StringBuilder();
        Map<String, Integer> l = roleMap(light);
        Map<String, Integer> d = roleMap(dark);

        sb.append("{\n");
        sb.append("  \"variableCollections\": [\n");
        sb.append("    {\n");
        sb.append("      \"name\": \"MD3UI scheme\",\n");
        sb.append("      \"modes\": [\"Light\", \"Dark\"],\n");
        sb.append("      \"variables\": [\n");
        int i = 0;
        for (String key : l.keySet()) {
            sb.append("        {\n");
            sb.append("          \"name\": \"").append(key).append("\",\n");
            sb.append("          \"type\": \"COLOR\",\n");
            sb.append("          \"valuesByMode\": {\n");
            sb.append("            \"Light\": ").append(rgbaObject(l.get(key))).append(",\n");
            sb.append("            \"Dark\": ").append(rgbaObject(d.get(key))).append("\n");
            sb.append("          }\n");
            sb.append("        }").append(++i < l.size() ? ",\n" : "\n");
        }
        sb.append("      ]\n");
        sb.append("    }\n");
        sb.append("  ]\n");
        sb.append("}\n");
        return sb.toString();
    }

    /** Figma expects 0..1 float channels, not 0..255. */
    private static String rgbaObject(int argb) {
        return String.format(java.util.Locale.ROOT,
                "{ \"r\": %.4f, \"g\": %.4f, \"b\": %.4f, \"a\": %.4f }",
                Argb.r(argb) / 255.0, Argb.g(argb) / 255.0,
                Argb.b(argb) / 255.0, Argb.a(argb) / 255.0);
    }
}
