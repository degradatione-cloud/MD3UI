package dev.md3ui.core.theme;

import dev.md3ui.core.color.Argb;
import dev.md3ui.core.color.TonalPalette;

/**
 * A resolved Material Design 3 color scheme: the full set of semantic roles
 * derived from five tonal palettes at the tone stops the spec assigns.
 *
 * <p>Every role has a guaranteed-legible {@code on*} partner, which is the whole
 * point of the system. Widgets must never reference a raw color; they ask the
 * scheme for a role. That is what lets one theme swap recolor every screen.
 */
public final class Md3Scheme {

    public enum Variant {
        /** Seed hue for primary, analogous secondary, +60&deg; tertiary. Default MD3. */
        TONAL_SPOT,
        /** Low chroma throughout; reads as a grey UI with a colored accent. */
        NEUTRAL,
        /** High chroma primary, punchy tertiary. MD3 Expressive default. */
        VIBRANT,
        /** Complementary tertiary (+180&deg;) for maximum accent separation. */
        CONTENT
    }

    public final boolean dark;
    public final Variant variant;
    public final int seed;

    // --- Primary ---
    public final int primary, onPrimary, primaryContainer, onPrimaryContainer;
    // --- Secondary ---
    public final int secondary, onSecondary, secondaryContainer, onSecondaryContainer;
    // --- Tertiary ---
    public final int tertiary, onTertiary, tertiaryContainer, onTertiaryContainer;
    // --- Error ---
    public final int error, onError, errorContainer, onErrorContainer;
    // --- Surfaces (elevation is tone, not shadow) ---
    public final int surface, onSurface, onSurfaceVariant;
    public final int surfaceDim, surfaceBright;
    public final int surfaceContainerLowest, surfaceContainerLow, surfaceContainer,
            surfaceContainerHigh, surfaceContainerHighest;
    public final int surfaceVariant;
    public final int inverseSurface, inverseOnSurface, inversePrimary;
    // --- Outlines ---
    public final int outline, outlineVariant;
    // --- Misc ---
    public final int scrim, shadow;

    private Md3Scheme(Builder b) {
        this.dark = b.dark;
        this.variant = b.variant;
        this.seed = b.seed;

        TonalPalette p = b.primaryPalette;
        TonalPalette s = b.secondaryPalette;
        TonalPalette t = b.tertiaryPalette;
        TonalPalette n = b.neutralPalette;
        TonalPalette nv = b.neutralVariantPalette;
        TonalPalette e = b.errorPalette;

        if (dark) {
            primary = p.tone(80);  onPrimary = p.tone(20);
            primaryContainer = p.tone(30); onPrimaryContainer = p.tone(90);
            secondary = s.tone(80); onSecondary = s.tone(20);
            secondaryContainer = s.tone(30); onSecondaryContainer = s.tone(90);
            tertiary = t.tone(80); onTertiary = t.tone(20);
            tertiaryContainer = t.tone(30); onTertiaryContainer = t.tone(90);
            error = e.tone(80); onError = e.tone(20);
            errorContainer = e.tone(30); onErrorContainer = e.tone(90);

            surface = n.tone(6);
            onSurface = n.tone(90);
            onSurfaceVariant = nv.tone(80);
            surfaceVariant = nv.tone(30);
            surfaceDim = n.tone(6);
            surfaceBright = n.tone(24);
            surfaceContainerLowest = n.tone(4);
            surfaceContainerLow = n.tone(10);
            surfaceContainer = n.tone(12);
            surfaceContainerHigh = n.tone(17);
            surfaceContainerHighest = n.tone(22);
            inverseSurface = n.tone(90);
            inverseOnSurface = n.tone(20);
            inversePrimary = p.tone(40);
            outline = nv.tone(60);
            outlineVariant = nv.tone(30);
        } else {
            primary = p.tone(40); onPrimary = p.tone(100);
            primaryContainer = p.tone(90); onPrimaryContainer = p.tone(10);
            secondary = s.tone(40); onSecondary = s.tone(100);
            secondaryContainer = s.tone(90); onSecondaryContainer = s.tone(10);
            tertiary = t.tone(40); onTertiary = t.tone(100);
            tertiaryContainer = t.tone(90); onTertiaryContainer = t.tone(10);
            error = e.tone(40); onError = e.tone(100);
            errorContainer = e.tone(90); onErrorContainer = e.tone(10);

            surface = n.tone(98);
            onSurface = n.tone(10);
            onSurfaceVariant = nv.tone(30);
            surfaceVariant = nv.tone(90);
            surfaceDim = n.tone(87);
            surfaceBright = n.tone(98);
            surfaceContainerLowest = n.tone(100);
            surfaceContainerLow = n.tone(96);
            surfaceContainer = n.tone(94);
            surfaceContainerHigh = n.tone(92);
            surfaceContainerHighest = n.tone(90);
            inverseSurface = n.tone(20);
            inverseOnSurface = n.tone(95);
            inversePrimary = p.tone(80);
            outline = nv.tone(50);
            outlineVariant = nv.tone(80);
        }
        scrim = 0xFF000000;
        shadow = 0xFF000000;
    }

    /** Build a scheme from a single seed color, the Material You way. */
    public static Md3Scheme fromSeed(int seed, boolean dark, Variant variant) {
        return new Builder(seed, dark, variant).build();
    }

    /**
     * Surface tint at a given elevation level (0..5), MD3 "elevation is tone".
     * Level 0 returns {@link #surface} untouched.
     */
    public int surfaceAtElevation(int level) {
        switch (Math.max(0, Math.min(level, 5))) {
            case 0: return surface;
            case 1: return surfaceContainerLow;
            case 2: return surfaceContainer;
            case 3: return surfaceContainerHigh;
            case 4: return surfaceContainerHighest;
            default: return dark ? surfaceBright : surfaceContainerHighest;
        }
    }

    /** The guaranteed-legible content color for a container role. */
    public int contentFor(int containerColor) {
        return Argb.contrast(Argb.opaque(containerColor), onSurface)
                >= Argb.contrast(Argb.opaque(containerColor), surface)
                ? onSurface : surface;
    }

    private static final class Builder {
        final boolean dark;
        final Variant variant;
        final int seed;
        TonalPalette primaryPalette, secondaryPalette, tertiaryPalette,
                neutralPalette, neutralVariantPalette, errorPalette;

        Builder(int seed, boolean dark, Variant variant) {
            this.seed = seed;
            this.dark = dark;
            this.variant = variant;
            double[] lch = dev.md3ui.core.color.Cie.rgbToLch(seed);
            double hue = lch[2];
            double chroma = lch[1];

            switch (variant) {
                case NEUTRAL:
                    primaryPalette = TonalPalette.of(hue, 12);
                    secondaryPalette = TonalPalette.of(hue, 8);
                    tertiaryPalette = TonalPalette.of(hue + 60, 12);
                    neutralPalette = TonalPalette.of(hue, 2);
                    neutralVariantPalette = TonalPalette.of(hue, 4);
                    break;
                case VIBRANT:
                    primaryPalette = TonalPalette.of(hue, Math.max(48, chroma));
                    secondaryPalette = TonalPalette.of(hue, 24);
                    tertiaryPalette = TonalPalette.of(hue + 60, 32);
                    neutralPalette = TonalPalette.of(hue, 8);
                    neutralVariantPalette = TonalPalette.of(hue, 12);
                    break;
                case CONTENT:
                    primaryPalette = TonalPalette.of(hue, chroma);
                    secondaryPalette = TonalPalette.of(hue, Math.max(chroma / 3, 8));
                    tertiaryPalette = TonalPalette.of(hue + 180, Math.max(chroma / 2, 12));
                    neutralPalette = TonalPalette.of(hue, Math.min(chroma / 12, 4));
                    neutralVariantPalette = TonalPalette.of(hue, Math.min(chroma / 6, 8));
                    break;
                case TONAL_SPOT:
                default:
                    primaryPalette = TonalPalette.of(hue, 36);
                    secondaryPalette = TonalPalette.of(hue, 16);
                    tertiaryPalette = TonalPalette.of(hue + 60, 24);
                    neutralPalette = TonalPalette.of(hue, 6);
                    neutralVariantPalette = TonalPalette.of(hue, 8);
                    break;
            }
            errorPalette = TonalPalette.of(25, 84);
        }

        Md3Scheme build() { return new Md3Scheme(this); }
    }
}
