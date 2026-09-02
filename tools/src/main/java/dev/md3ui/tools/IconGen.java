package dev.md3ui.tools;

import dev.md3ui.core.color.Argb;
import dev.md3ui.core.gfx.Shapes;
import dev.md3ui.core.theme.Md3Scheme;
import dev.md3ui.core.theme.Md3Tokens;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Generates the mod icon from the same shape solver the UI uses.
 *
 * <p>Fabric wants a 128x128 PNG. Drawing it with {@link Shapes} rather than
 * shipping a hand-made asset means the icon is literally a sample of the
 * renderer: an Expressive superellipse, a tonal surface step, and the pill
 * indicator, all in the baseline palette.
 */
public final class IconGen {

    public static void main(String[] args) throws Exception {
        File out = new File(args.length > 0 ? args[0]
                : "mod/src/main/resources/assets/md3ui");
        if (!out.exists() && !out.mkdirs()) {
            throw new IllegalStateException("cannot create " + out);
        }

        Md3Scheme s = Md3Scheme.fromSeed(0xFF6750A4, true, Md3Scheme.Variant.VIBRANT);
        int size = 128;
        RasterCanvas c = new RasterCanvas(size, size, 4);

        // Background: the darkest container, so the icon reads on any launcher theme.
        Shapes.roundRect(c, 0, 0, size, size, 24, s.surfaceContainerLowest);

        // Expressive cookie shape as the hero, in primary container.
        Shapes.squircle(c, 14, 14, 100, 100, Md3Tokens.SQUIRCLE_N,
                Argb.scaleAlpha(s.primaryContainer, 0.9f));

        // A tonal step, offset, to show elevation-by-tone.
        Shapes.squircle(c, 26, 26, 76, 76, Md3Tokens.SQUIRCLE_N, s.primary);

        // The navigation pill indicator, the signature MD3 element.
        Shapes.pill(c, 40, 56, 48, 18, s.onPrimary);
        Shapes.circle(c, 49, 65, 4.5f, s.primary);
        Shapes.circle(c, 64, 65, 4.5f, Argb.scaleAlpha(s.primary, 0.45f));
        Shapes.circle(c, 79, 65, 4.5f, Argb.scaleAlpha(s.primary, 0.45f));

        // Tone ladder strip along the bottom: what the mod actually generates.
        int[] tones = {s.tertiary, s.secondary, s.primaryContainer, s.onPrimary};
        for (int i = 0; i < tones.length; i++) {
            Shapes.roundRect(c, 34 + i * 16, 92, 12, 6, 3, tones[i]);
        }

        BufferedImage img = c.result();
        File f = new File(out, "icon.png");
        ImageIO.write(img, "PNG", f);
        System.out.printf("icon written: %s (%dx%d, %d bytes)%n",
                f.getAbsolutePath(), img.getWidth(), img.getHeight(), f.length());
    }
}
