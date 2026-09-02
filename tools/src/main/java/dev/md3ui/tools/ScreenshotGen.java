package dev.md3ui.tools;

import dev.md3ui.core.color.Argb;
import dev.md3ui.core.gfx.Md3Canvas;
import dev.md3ui.core.gfx.Shapes;
import dev.md3ui.core.theme.Md3Scheme;
import dev.md3ui.core.theme.Md3Tokens;
import dev.md3ui.core.widget.*;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Renders the repository screenshots using the shipping widget code.
 *
 * <p>Run from CI via {@code :tools:screenshots}. Every image here is the real
 * renderer's output through {@link RasterCanvas}, so a visual regression in a
 * widget shows up in the docs images automatically instead of the docs quietly
 * going stale.
 *
 * <p>Animated components are advanced to a chosen phase before capture (a switch
 * mid-travel, a button mid-morph) so the stills actually show the motion design
 * rather than only resting states.
 */
public final class ScreenshotGen {

    private static final int SUPERSAMPLE = 3;

    public static void main(String[] args) throws Exception {
        File out = new File(args.length > 0 ? args[0] : "docs/screenshots");
        if (!out.exists() && !out.mkdirs()) {
            throw new IllegalStateException("cannot create " + out);
        }

        int seed = 0xFF6750A4;

        shoot(out, "components-dark", 420, 300, Md3Scheme.fromSeed(seed, true,
                Md3Scheme.Variant.VIBRANT), ScreenshotGen::componentGallery);
        shoot(out, "components-light", 420, 300, Md3Scheme.fromSeed(seed, false,
                Md3Scheme.Variant.VIBRANT), ScreenshotGen::componentGallery);
        shoot(out, "title-screen", 420, 260, Md3Scheme.fromSeed(seed, true,
                Md3Scheme.Variant.VIBRANT), ScreenshotGen::titleScreen);
        shoot(out, "options-screen", 420, 260, Md3Scheme.fromSeed(seed, true,
                Md3Scheme.Variant.TONAL_SPOT), ScreenshotGen::optionsScreen);
        shoot(out, "world-select", 420, 260, Md3Scheme.fromSeed(seed, true,
                Md3Scheme.Variant.VIBRANT), ScreenshotGen::worldSelect);
        shoot(out, "palette", 420, 220, Md3Scheme.fromSeed(seed, true,
                Md3Scheme.Variant.VIBRANT), ScreenshotGen::paletteSheet);
        shoot(out, "theme-teal", 420, 200, Md3Scheme.fromSeed(0xFF00897B, true,
                Md3Scheme.Variant.VIBRANT), ScreenshotGen::themeStrip);
        shoot(out, "theme-amber", 420, 200, Md3Scheme.fromSeed(0xFFFF8F00, false,
                Md3Scheme.Variant.VIBRANT), ScreenshotGen::themeStrip);

        System.out.println("screenshots written to " + out.getAbsolutePath());
    }

    private interface Painter {
        void paint(Md3Canvas c, Md3Scheme s);
    }

    private static void shoot(File dir, String name, int w, int h, Md3Scheme s,
                              Painter p) throws Exception {
        RasterCanvas c = new RasterCanvas(w, h, SUPERSAMPLE);
        c.fillRect(0, 0, w, h, s.surface);
        p.paint(c, s);
        BufferedImage img = c.result();
        File f = new File(dir, name + ".png");
        ImageIO.write(img, "PNG", f);
        System.out.printf("  %-20s %dx%d  %d bytes%n", name, w, h, f.length());
    }

    /** Advance a widget's springs to a given phase, then draw. */
    private static void settle(Md3Widget w, double seconds, double mx, double my,
                               boolean down) {
        int steps = Math.max(1, (int) (seconds * 60));
        for (int i = 0; i < steps; i++) {
            w.tick(1.0 / 60.0, mx, my, down);
        }
    }

    // ---------------------------------------------------------------- galleries

    private static void componentGallery(Md3Canvas c, Md3Scheme s) {
        heading(c, s, "MD3UI components", 12, 10);

        float x = 12, y = 26;

        // Buttons row: every variant, one caught mid-press to show shape morph.
        Md3Button filled = new Md3Button(x, y, 70, Md3Tokens.BUTTON_HEIGHT, "Filled",
                Md3Button.Style.FILLED);
        Md3Button tonal = new Md3Button(x + 76, y, 70, Md3Tokens.BUTTON_HEIGHT, "Tonal",
                Md3Button.Style.TONAL);
        Md3Button outlined = new Md3Button(x + 152, y, 70, Md3Tokens.BUTTON_HEIGHT,
                "Outlined", Md3Button.Style.OUTLINED);
        Md3Button textBtn = new Md3Button(x + 228, y, 60, Md3Tokens.BUTTON_HEIGHT, "Text",
                Md3Button.Style.TEXT);
        Md3Button danger = new Md3Button(x + 294, y, 70, Md3Tokens.BUTTON_HEIGHT, "Delete",
                Md3Button.Style.DANGER);

        // Hover on tonal, press on filled: both states visible in one still.
        settle(filled, 0.12, filled.x() + 20, filled.y() + 8, true);
        settle(tonal, 0.4, tonal.x() + 20, tonal.y() + 8, false);
        settle(outlined, 0.4, -100, -100, false);
        settle(textBtn, 0.4, -100, -100, false);
        settle(danger, 0.4, -100, -100, false);

        for (Md3Button b : new Md3Button[] {filled, tonal, outlined, textBtn, danger}) {
            b.render(c, s);
        }

        // Disabled example.
        y += 28;
        Md3Button disabled = new Md3Button(x, y, 70, Md3Tokens.BUTTON_HEIGHT, "Disabled",
                Md3Button.Style.FILLED);
        disabled.setEnabled(false);
        settle(disabled, 0.3, -100, -100, false);
        disabled.render(c, s);

        Md3Button elevated = new Md3Button(x + 76, y, 70, Md3Tokens.BUTTON_HEIGHT,
                "Elevated", Md3Button.Style.ELEVATED);
        settle(elevated, 0.4, -100, -100, false);
        elevated.render(c, s);

        // Switches: off, on, and one caught mid-travel.
        Md3Switch off = new Md3Switch(x + 158, y + 2, false);
        Md3Switch on = new Md3Switch(x + 194, y + 2, true);
        Md3Switch moving = new Md3Switch(x + 230, y + 2, false);
        moving.setChecked(true);
        settle(off, 0.3, -100, -100, false);
        settle(on, 0.3, -100, -100, false);
        settle(moving, 0.075, -100, -100, false); // caught mid-flight
        off.render(c, s);
        on.render(c, s);
        moving.render(c, s);

        label(c, s, "mid-travel", x + 230, y + 22);

        // Chips.
        y += 30;
        Md3Chip f1 = new Md3Chip(x, y, "Survival", Md3Chip.Kind.FILTER).selected(true);
        Md3Chip f2 = new Md3Chip(0, y, "Creative", Md3Chip.Kind.FILTER);
        Md3Chip f3 = new Md3Chip(0, y, "Modded", Md3Chip.Kind.FILTER);
        f1.measure(c);
        f2.measure(c).setPos(f1.x() + f1.width() + 6, y);
        f3.measure(c).setPos(f2.x() + f2.width() + 6, y);
        for (Md3Chip ch : new Md3Chip[] {f1, f2, f3}) {
            settle(ch, 0.3, -100, -100, false);
            ch.render(c, s);
        }

        // Slider with a value label.
        y += 26;
        Md3Slider sl = new Md3Slider(x, y + 8, 170, 0, 100, 62, 0);
        sl.label("Render distance").format(v -> String.format("%.0f chunks", v));
        settle(sl, 0.4, -100, -100, false);
        sl.render(c, s);

        // Slider being dragged: thicker track, narrower handle.
        Md3Slider sl2 = new Md3Slider(x + 190, y + 8, 170, 0, 100, 35, 0);
        sl2.label("Master volume").format(v -> String.format("%.0f%%", v));
        sl2.mouseClicked(sl2.x() + 60, sl2.y() + 6, 0);
        settle(sl2, 0.3, sl2.x() + 60, sl2.y() + 6, true);
        sl2.render(c, s);

        // Text fields: empty (label at rest) and focused with content.
        y += 34;
        Md3TextField tf1 = new Md3TextField(x, y, 120, "Server address");
        settle(tf1, 0.4, -100, -100, false);
        tf1.render(c, s);

        Md3TextField tf2 = new Md3TextField(x + 130, y, 120, "World name");
        tf2.setText("Hardcore run");
        tf2.setFocused(true);
        settle(tf2, 0.4, -100, -100, false);
        tf2.render(c, s);

        Md3TextField tf3 = new Md3TextField(x + 260, y, 120, "Port");
        tf3.validator(v -> v.isEmpty() || v.matches("\\d{1,5}"));
        tf3.setText("25 565x");
        settle(tf3, 0.4, -100, -100, false);
        tf3.render(c, s);

        // Cards.
        y += 40;
        new Md3Card(x, y, 120, 34).style(Md3Card.Style.FILLED)
                .title("Filled card").body("surfaceContainer").render(c, s);
        new Md3Card(x + 130, y, 120, 34).style(Md3Card.Style.ELEVATED)
                .title("Elevated").body("real shadow").render(c, s);
        new Md3Card(x + 260, y, 120, 34).style(Md3Card.Style.OUTLINED)
                .title("Outlined").body("hairline border").render(c, s);
    }

    private static void titleScreen(Md3Canvas c, Md3Scheme s) {
        // A large expressive hero shape behind the title, clipped to the canvas.
        Shapes.squircle(c, -40, -70, 300, 190, Md3Tokens.SQUIRCLE_N,
                Argb.scaleAlpha(s.primaryContainer, 0.55f));
        Shapes.circle(c, c.width() - 30, 40, 70, Argb.scaleAlpha(s.tertiaryContainer, 0.4f));

        big(c, s, "MINECRAFT", 24, 44);
        label(c, s, "Material 3 Expressive UI", 26, 62);

        float bw = 150, bx = 26, by = 92;
        Md3Button single = Md3Button.filled(bx, by, bw, "Singleplayer", () -> {});
        Md3Button multi = Md3Button.tonal(bx, by + 26, bw, "Multiplayer", () -> {});
        Md3Button realms = Md3Button.outlined(bx, by + 52, bw, "Realms", () -> {});
        Md3Button opts = Md3Button.text(bx, by + 78, 72, "Options", () -> {});
        Md3Button quit = Md3Button.text(bx + 78, by + 78, 72, "Quit", () -> {});

        settle(single, 0.4, single.x() + 40, single.y() + 8, false);
        settle(multi, 0.4, -100, -100, false);
        settle(realms, 0.4, -100, -100, false);
        settle(opts, 0.4, -100, -100, false);
        settle(quit, 0.4, -100, -100, false);
        for (Md3Button b : new Md3Button[] {single, multi, realms, opts, quit}) b.render(c, s);

        // A card showing the current theme seed, the sort of thing vanilla has nowhere to put.
        Md3Card card = new Md3Card(220, 92, 170, 78).style(Md3Card.Style.FILLED)
                .radius(Md3Tokens.SHAPE_LG);
        card.render(c, s);
        label(c, s, "Theme", 232, 100);
        heading(c, s, "Dynamic", 232, 110);
        float sw = 20;
        int[] swatches = {s.primary, s.secondary, s.tertiary, s.primaryContainer,
                s.secondaryContainer, s.surfaceContainerHighest};
        for (int i = 0; i < swatches.length; i++) {
            Shapes.roundRect(c, 232 + i * (sw + 4), 126, sw, sw, Md3Tokens.SHAPE_SM,
                    swatches[i]);
        }
        label(c, s, "seed #6750A4 · VIBRANT", 232, 152);
    }

    private static void optionsScreen(Md3Canvas c, Md3Scheme s) {
        // Navigation rail with the travelling pill indicator.
        List<Md3NavRail.Item> items = new ArrayList<>();
        items.add(new Md3NavRail.Item("[#]", "Video", () -> {}));
        items.add(new Md3NavRail.Item("[<]", "Sound", () -> {}));
        items.add(new Md3NavRail.Item("[K]", "Keys", () -> {}));
        items.add(new Md3NavRail.Item("[@]", "Skin", () -> {}));
        Md3NavRail rail = new Md3NavRail(0, Md3Tokens.APP_BAR_HEIGHT, 44,
                c.height() - Md3Tokens.APP_BAR_HEIGHT, items);
        rail.select(0);
        settle(rail, 0.5, -100, -100, false);

        c.fillRect(0, 0, c.width(), Md3Tokens.APP_BAR_HEIGHT, s.surfaceContainer);
        heading(c, s, "Options", 12, 11);

        rail.render(c, s);

        float x = 56, y = 42;
        heading(c, s, "Graphics", x, y);
        y += 14;

        Md3Slider render = new Md3Slider(x, y + 8, 200, 2, 32, 12, 1);
        render.label("Render distance").format(v -> String.format("%.0f chunks", v));
        settle(render, 0.4, -100, -100, false);
        render.render(c, s);
        y += 34;

        Md3Slider gui = new Md3Slider(x, y + 8, 200, 1, 4, 2, 1);
        gui.label("GUI scale").format(v -> String.format("%.0fx", v));
        settle(gui, 0.4, -100, -100, false);
        gui.render(c, s);
        y += 36;

        Md3Switch vsync = new Md3Switch(x, y, true);
        vsync.label("VSync");
        settle(vsync, 0.3, -100, -100, false);
        vsync.render(c, s);
        y += 22;

        Md3Switch bob = new Md3Switch(x, y, false);
        bob.label("View bobbing");
        settle(bob, 0.3, bob.x() + 10, bob.y() + 8, false);
        bob.render(c, s);
        y += 22;

        Md3Switch fs = new Md3Switch(x, y, true);
        fs.label("Fullscreen");
        settle(fs, 0.3, -100, -100, false);
        fs.render(c, s);

        // Right column: a card grouping quality presets as filter chips.
        Md3Card card = new Md3Card(280, 42, 128, 120).style(Md3Card.Style.FILLED)
                .radius(Md3Tokens.SHAPE_LG);
        card.render(c, s);
        label(c, s, "Preset", 290, 50);
        Md3Chip fast = new Md3Chip(290, 62, "Fast", Md3Chip.Kind.FILTER);
        Md3Chip fancy = new Md3Chip(290, 82, "Fancy", Md3Chip.Kind.FILTER).selected(true);
        Md3Chip custom = new Md3Chip(290, 102, "Custom", Md3Chip.Kind.FILTER);
        for (Md3Chip ch : new Md3Chip[] {fast, fancy, custom}) {
            ch.measure(c);
            settle(ch, 0.3, -100, -100, false);
            ch.render(c, s);
        }
        label(c, s, "Renderer: VulkanMod", 290, 126);
        label(c, s, "MD3UI: active", 290, 138);
    }

    private static void worldSelect(Md3Canvas c, Md3Scheme s) {
        c.fillRect(0, 0, c.width(), Md3Tokens.APP_BAR_HEIGHT, s.surfaceContainer);
        heading(c, s, "Select World", 12, 11);

        Md3TextField search = new Md3TextField(200, 6, 130, "Search");
        search.setText("hard");
        settle(search, 0.4, -100, -100, false);
        search.render(c, s);

        Md3List list = new Md3List(12, 42, 260, 175);
        list.add(new Md3List.TextRow("[W]", "Hardcore Run", "Survival · 12h 04m",
                "1.21.11", () -> {}));
        list.add(new Md3List.TextRow("[W]", "Creative Flats", "Creative · 3h 22m",
                "1.21.8", () -> {}));
        list.add(new Md3List.TextRow("[W]", "Skyblock", "Survival · 41h 10m",
                "1.21.4", () -> {}));
        list.add(new Md3List.TextRow("[W]", "Redstone Lab", "Creative · 8h 55m",
                "1.21.11", () -> {}));
        list.add(new Md3List.TextRow("[W]", "Nether Base", "Survival · 27h 33m",
                "1.21.9", () -> {}));
        list.mouseClicked(20, 50, 0);
        settle(list, 0.5, 20, 78, false);
        list.render(c, s);

        // Detail card for the selected world.
        Md3Card detail = new Md3Card(282, 42, 126, 130).style(Md3Card.Style.FILLED)
                .radius(Md3Tokens.SHAPE_LG);
        detail.render(c, s);
        label(c, s, "Selected", 292, 50);
        heading(c, s, "Hardcore Run", 292, 60);
        label(c, s, "Seed 4815162342", 292, 76);
        label(c, s, "Size 214 MB", 292, 88);
        label(c, s, "Cheats off", 292, 100);

        Md3Button play = Md3Button.filled(292, 116, 106, "Play", () -> {});
        settle(play, 0.4, play.x() + 40, play.y() + 8, false);
        play.render(c, s);
        Md3Button del = new Md3Button(292, 142, 106, Md3Tokens.BUTTON_HEIGHT, "Delete",
                Md3Button.Style.DANGER);
        settle(del, 0.4, -100, -100, false);
        del.render(c, s);

        // Bottom bar: compact navigation, shown to document the breakpoint.
        List<Md3NavRail.Item> items = new ArrayList<>();
        items.add(new Md3NavRail.Item("[W]", "Worlds", () -> {}));
        items.add(new Md3NavRail.Item("[S]", "Servers", () -> {}));
        items.add(new Md3NavRail.Item("[R]", "Realms", () -> {}));
        Md3NavRail bar = new Md3NavRail(0, c.height() - Md3Tokens.NAV_BAR_HEIGHT,
                c.width(), Md3Tokens.NAV_BAR_HEIGHT, items).horizontal(true);
        bar.select(0);
        items.get(1).badge(3);
        settle(bar, 0.5, -100, -100, false);
        bar.render(c, s);
    }

    private static void paletteSheet(Md3Canvas c, Md3Scheme s) {
        heading(c, s, "Generated scheme roles", 12, 10);
        label(c, s, "one seed color, CIELAB tone ladder, WCAG-checked pairs", 12, 22);

        String[][] roles = {
                {"primary", "onPrimary"},
                {"primaryContainer", "onPrimaryContainer"},
                {"secondary", "onSecondary"},
                {"secondaryContainer", "onSecondaryContainer"},
                {"tertiary", "onTertiary"},
                {"tertiaryContainer", "onTertiaryContainer"},
                {"error", "onError"},
                {"surface", "onSurface"},
                {"surfaceContainerHigh", "onSurfaceVariant"},
        };
        int[][] colors = {
                {s.primary, s.onPrimary},
                {s.primaryContainer, s.onPrimaryContainer},
                {s.secondary, s.onSecondary},
                {s.secondaryContainer, s.onSecondaryContainer},
                {s.tertiary, s.onTertiary},
                {s.tertiaryContainer, s.onTertiaryContainer},
                {s.error, s.onError},
                {s.surface, s.onSurface},
                {s.surfaceContainerHigh, s.onSurfaceVariant},
        };

        float y = 36, rowH = 19;
        for (int i = 0; i < roles.length; i++) {
            float bx = i < 5 ? 12 : 216;
            float by = y + (i % 5) * (rowH + 3);
            float bw = 192;
            Shapes.roundRect(c, bx, by, bw, rowH, Md3Tokens.SHAPE_SM, colors[i][0]);
            c.drawText(roles[i][0], bx + 6, by + 3, colors[i][1], false);
            String ratio = String.format("%.1f:1",
                    Argb.contrast(Argb.opaque(colors[i][0]), Argb.opaque(colors[i][1])));
            float rw = c.textWidth(ratio);
            c.drawText(ratio, bx + bw - rw - 6, by + 3, colors[i][1], false);
        }

        // Tone ladder strip.
        float ty = 146;
        label(c, s, "primary tonal palette, tones 0-100", 12, ty - 12);
        int[] tones = {0, 10, 20, 30, 40, 50, 60, 70, 80, 90, 95, 99, 100};
        dev.md3ui.core.color.TonalPalette pal =
                dev.md3ui.core.color.TonalPalette.fromColor(s.seed);
        float sw = (c.width() - 24f) / tones.length;
        for (int i = 0; i < tones.length; i++) {
            int col = pal.tone(tones[i]);
            Shapes.roundRect(c, 12 + i * sw, ty, sw - 2, 34, Md3Tokens.SHAPE_XS, col);
            int textColor = tones[i] > 55 ? pal.tone(10) : pal.tone(95);
            String t = String.valueOf(tones[i]);
            c.drawText(t, 12 + i * sw + (sw - 2 - c.textWidth(t)) / 2f, ty + 12,
                    textColor, false);
        }
        label(c, s, "t40 " + Argb.hex(pal.tone(40)) + "  t80 " + Argb.hex(pal.tone(80))
                + "  t90 " + Argb.hex(pal.tone(90)), 12, ty + 40);
    }

    private static void themeStrip(Md3Canvas c, Md3Scheme s) {
        heading(c, s, "Theme preview", 12, 10);
        label(c, s, "seed " + Argb.hex(s.seed) + (s.dark ? " · dark" : " · light"), 12, 22);

        float y = 40;
        Md3Button b1 = Md3Button.filled(12, y, 100, "Play", () -> {});
        Md3Button b2 = Md3Button.tonal(120, y, 100, "Settings", () -> {});
        Md3Button b3 = Md3Button.outlined(228, y, 100, "Quit", () -> {});
        for (Md3Button b : new Md3Button[] {b1, b2, b3}) {
            settle(b, 0.4, -100, -100, false);
            b.render(c, s);
        }

        y += 30;
        Md3Slider sl = new Md3Slider(12, y + 8, 180, 0, 100, 70, 0);
        sl.label("Brightness").format(v -> String.format("%.0f%%", v));
        settle(sl, 0.4, -100, -100, false);
        sl.render(c, s);

        Md3Switch sw = new Md3Switch(220, y + 6, true);
        sw.label("Smooth lighting");
        settle(sw, 0.3, -100, -100, false);
        sw.render(c, s);

        y += 38;
        new Md3Card(12, y, 180, 44).style(Md3Card.Style.FILLED)
                .title("Elevation by tone").body("no drop shadows").render(c, s);
        new Md3Card(202, y, 180, 44).style(Md3Card.Style.OUTLINED)
                .title("Outlined variant").body("hairline, 3:1 contrast").render(c, s);

        y += 52;
        int[] swatches = {s.primary, s.secondary, s.tertiary, s.error,
                s.surfaceContainerLow, s.surfaceContainerHighest, s.outline};
        float bw = (c.width() - 24f) / swatches.length;
        for (int i = 0; i < swatches.length; i++) {
            Shapes.roundRect(c, 12 + i * bw, y, bw - 3, 16, Md3Tokens.SHAPE_XS,
                    swatches[i]);
        }
    }

    // ------------------------------------------------------------------ helpers

    private static void heading(Md3Canvas c, Md3Scheme s, String text, float x, float y) {
        c.drawText(text, x, y, s.onSurface, false);
    }

    private static void big(Md3Canvas c, Md3Scheme s, String text, float x, float y) {
        // Poor man's display type: draw the run twice with a 1px offset for weight.
        c.drawText(text, x, y, s.primary, false);
        c.drawText(text, x + 0.6f, y, s.primary, false);
    }

    private static void label(Md3Canvas c, Md3Scheme s, String text, float x, float y) {
        c.drawText(text, x, y, s.onSurfaceVariant, false);
    }
}
