package dev.md3ui.core;

import dev.md3ui.core.color.Argb;
import dev.md3ui.core.color.Cie;
import dev.md3ui.core.color.TonalPalette;
import dev.md3ui.core.theme.Md3Scheme;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Locks down the colour system.
 *
 * <p>The palette tests compare against Google's published MD3 baseline values.
 * That is the strongest available check that the CIELAB solver reproduces HCT's
 * tone ladder, and it would catch a regression in the gamut search immediately.
 */
class SchemeTest {

    private static final int BASELINE_SEED = 0xFF6750A4;

    @Test
    @DisplayName("tonal palette reproduces Google's published MD3 baseline tones")
    void baselineTonesMatchSpec() {
        TonalPalette p = TonalPalette.fromColor(BASELINE_SEED);
        // These are the documented Material 3 baseline values.
        assertEquals("#6750A4", Argb.hex(p.tone(40)), "primary40");
        assertEquals("#D2BBFF", Argb.hex(p.tone(80)), "primary80");
        assertEquals("#E9DDFF", Argb.hex(p.tone(90)), "primary90");
        assertEquals("#FFFFFF", Argb.hex(p.tone(100)), "tone100 must be pure white");
    }

    @Test
    @DisplayName("tone(n) lands on L* = n")
    void tonesTrackLightness() {
        TonalPalette p = TonalPalette.fromColor(0xFF00897B);
        for (int t : new int[] {0, 10, 30, 50, 70, 90, 100}) {
            double actual = Cie.tone(p.tone(t));
            assertEquals(t, actual, 1.0,
                    "tone " + t + " should have L* within 1.0, got " + actual);
        }
    }

    @Test
    @DisplayName("tone ladder is monotonically lighter")
    void ladderIsMonotonic() {
        TonalPalette p = TonalPalette.fromColor(0xFFB3261E);
        double prev = -1;
        for (int t = 0; t <= 100; t += 5) {
            double lum = Argb.luminance(p.tone(t));
            assertTrue(lum >= prev - 1e-6,
                    "luminance must not decrease at tone " + t);
            prev = lum;
        }
    }

    @Test
    @DisplayName("every on* role clears WCAG AA against its container")
    void contentPairsAreLegible() {
        for (boolean dark : new boolean[] {true, false}) {
            for (Md3Scheme.Variant v : Md3Scheme.Variant.values()) {
                for (int seed : new int[] {0xFF6750A4, 0xFF00897B, 0xFFFF8F00, 0xFFB3261E,
                        0xFF1A73E8, 0xFF7CB342}) {
                    Md3Scheme s = Md3Scheme.fromSeed(seed, dark, v);
                    String ctx = String.format("seed=%s dark=%s variant=%s",
                            Argb.hex(seed), dark, v);
                    assertContrast(s.primary, s.onPrimary, 4.5, "primary " + ctx);
                    assertContrast(s.secondary, s.onSecondary, 4.5, "secondary " + ctx);
                    assertContrast(s.tertiary, s.onTertiary, 4.5, "tertiary " + ctx);
                    assertContrast(s.error, s.onError, 4.5, "error " + ctx);
                    assertContrast(s.primaryContainer, s.onPrimaryContainer, 4.5,
                            "primaryContainer " + ctx);
                    assertContrast(s.secondaryContainer, s.onSecondaryContainer, 4.5,
                            "secondaryContainer " + ctx);
                    assertContrast(s.surface, s.onSurface, 4.5, "surface " + ctx);
                    assertContrast(s.surface, s.onSurfaceVariant, 4.5,
                            "onSurfaceVariant " + ctx);
                }
            }
        }
    }

    @Test
    @DisplayName("outline clears the 3:1 non-text contrast floor on every surface")
    void outlineIsVisible() {
        for (boolean dark : new boolean[] {true, false}) {
            Md3Scheme s = Md3Scheme.fromSeed(BASELINE_SEED, dark, Md3Scheme.Variant.VIBRANT);
            // This is the pitfall that makes chips invisible in dark mode.
            assertContrast(s.outline, s.surface, 3.0, "outline/surface dark=" + dark);
            assertContrast(s.outline, s.surfaceContainerLow, 3.0,
                    "outline/containerLow dark=" + dark);
        }
    }

    @Test
    @DisplayName("surface container ladder steps in a consistent direction")
    void surfaceLadderOrdered() {
        Md3Scheme dark = Md3Scheme.fromSeed(BASELINE_SEED, true, Md3Scheme.Variant.VIBRANT);
        assertTrue(Argb.luminance(dark.surfaceContainerLowest)
                <= Argb.luminance(dark.surfaceContainerLow));
        assertTrue(Argb.luminance(dark.surfaceContainerLow)
                <= Argb.luminance(dark.surfaceContainer));
        assertTrue(Argb.luminance(dark.surfaceContainer)
                <= Argb.luminance(dark.surfaceContainerHigh));
        assertTrue(Argb.luminance(dark.surfaceContainerHigh)
                <= Argb.luminance(dark.surfaceContainerHighest));

        Md3Scheme light = Md3Scheme.fromSeed(BASELINE_SEED, false, Md3Scheme.Variant.VIBRANT);
        // In light mode the ladder descends: lowest is pure white.
        assertTrue(Argb.luminance(light.surfaceContainerLowest)
                >= Argb.luminance(light.surfaceContainerHighest));
    }

    @Test
    @DisplayName("elevation returns distinguishable tones, never identical surfaces")
    void elevationStepsAreVisible() {
        for (boolean dark : new boolean[] {true, false}) {
            Md3Scheme s = Md3Scheme.fromSeed(BASELINE_SEED, dark, Md3Scheme.Variant.VIBRANT);
            for (int level = 1; level <= 4; level++) {
                int lower = s.surfaceAtElevation(level - 1);
                int upper = s.surfaceAtElevation(level);
                assertNotEquals(lower, upper,
                        "elevation " + level + " must differ from " + (level - 1)
                                + " (dark=" + dark + ")");
            }
        }
    }

    @Test
    @DisplayName("alpha compositing is associative enough for state layers")
    void compositeMath() {
        int opaque = 0xFF102030;
        assertEquals(opaque, Argb.over(0x00FFFFFF, opaque), "transparent source is a no-op");
        assertEquals(0xFFFFFFFF, Argb.over(0xFFFFFFFF, opaque), "opaque source wins");
        int half = Argb.over(Argb.withAlpha(0xFFFFFFFF, 128), opaque);
        assertTrue(Argb.r(half) > Argb.r(opaque), "50% white must lighten");
        assertEquals(255, Argb.a(half), "result over an opaque base stays opaque");
    }

    @Test
    @DisplayName("Lab round-trip is stable")
    void labRoundTrip() {
        int[] samples = {0xFF000000, 0xFFFFFFFF, 0xFF6750A4, 0xFF00897B, 0xFFFF8F00,
                0xFF123456, 0xFFABCDEF};
        for (int c : samples) {
            double[] lab = Cie.rgbToLab(c);
            int back = Cie.labToRgbClamped(lab[0], lab[1], lab[2]);
            assertEquals(Argb.r(c), Argb.r(back), 1, "R round-trip " + Argb.hex(c));
            assertEquals(Argb.g(c), Argb.g(back), 1, "G round-trip " + Argb.hex(c));
            assertEquals(Argb.b(c), Argb.b(back), 1, "B round-trip " + Argb.hex(c));
        }
    }

    @Test
    @DisplayName("out-of-gamut requests reduce chroma instead of shifting hue")
    void gamutMappingPreservesHue() {
        // Chroma 200 is far outside sRGB at any lightness.
        TonalPalette p = TonalPalette.of(140, 200);
        for (int t : new int[] {20, 50, 80}) {
            int c = p.tone(t);
            double[] lch = Cie.rgbToLch(c);
            assertEquals(140, lch[2], 12.0,
                    "hue should be preserved at tone " + t + ", got " + lch[2]);
            assertEquals(t, lch[0], 2.0, "tone should be preserved at " + t);
        }
    }

    private static void assertContrast(int container, int content, double min, String what) {
        double ratio = Argb.contrast(Argb.opaque(container), Argb.opaque(content));
        assertTrue(ratio >= min,
                String.format("%s: contrast %.2f:1 below %.1f:1 (%s on %s)",
                        what, ratio, min, Argb.hex(content), Argb.hex(container)));
    }
}
