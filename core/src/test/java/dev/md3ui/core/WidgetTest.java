package dev.md3ui.core;

import dev.md3ui.core.gfx.Md3Canvas;
import dev.md3ui.core.theme.Md3Scheme;
import dev.md3ui.core.widget.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Behavioural tests for the widgets: input handling, state, and render hygiene.
 *
 * <p>The clip-balance assertions matter more than they look. An unbalanced
 * scissor in-game does not throw &mdash; it silently clips the rest of the frame,
 * including other mods' overlays, and produces bug reports that are very hard to
 * trace back. Catching it here is far cheaper.
 */
class WidgetTest {

    private static final Md3Scheme SCHEME =
            Md3Scheme.fromSeed(0xFF6750A4, true, Md3Scheme.Variant.VIBRANT);

    /** Canvas that only counts calls and tracks stack balance. */
    private static final class Probe implements Md3Canvas {
        int quads;
        int clipDepth, transformDepth, alphaDepth;
        int maxClip;
        final List<String> texts = new ArrayList<>();

        @Override public void fillRect(float x, float y, float w, float h, int argb) {
            assertFalse(Float.isNaN(x) || Float.isNaN(y) || Float.isNaN(w) || Float.isNaN(h),
                    "NaN geometry emitted");
            assertTrue(w >= 0 && h >= 0, "negative size quad: " + w + "x" + h);
            quads++;
        }
        @Override public void fillGradientV(float x, float y, float w, float h, int a, int b) {
            quads++;
        }
        @Override public float drawText(String t, float x, float y, int c, boolean s) {
            assertFalse(Float.isNaN(x) || Float.isNaN(y), "NaN text position");
            if (t != null) texts.add(t);
            return textWidth(t);
        }
        @Override public float textWidth(String text) {
            return text == null ? 0 : text.length() * 6f;
        }
        @Override public float lineHeight() { return 9f; }
        @Override public void pushClip(float x, float y, float w, float h) {
            clipDepth++;
            maxClip = Math.max(maxClip, clipDepth);
        }
        @Override public void popClip() {
            clipDepth--;
            assertTrue(clipDepth >= 0, "popClip without a matching pushClip");
        }
        @Override public void pushTranslate(float dx, float dy) { transformDepth++; }
        @Override public void pushScale(float ox, float oy, float s) { transformDepth++; }
        @Override public void popTransform() { transformDepth--; }
        @Override public void pushAlpha(float a) { alphaDepth++; }
        @Override public void popAlpha() { alphaDepth--; }
        @Override public float width() { return 400f; }
        @Override public float height() { return 260f; }

        void assertBalanced() {
            assertEquals(0, clipDepth, "clip stack leaked");
            assertEquals(0, transformDepth, "transform stack leaked");
            assertEquals(0, alphaDepth, "alpha stack leaked");
        }
    }

    private static void frames(Md3Widget w, int n, double mx, double my, boolean down) {
        for (int i = 0; i < n; i++) w.tick(1.0 / 60.0, mx, my, down);
    }

    @Test
    @DisplayName("button fires exactly once per click and only inside its bounds")
    void buttonClicks() {
        AtomicInteger hits = new AtomicInteger();
        Md3Button b = Md3Button.filled(10, 10, 80, "Play", hits::incrementAndGet);

        assertTrue(b.mouseClicked(20, 15, 0), "click inside should be consumed");
        assertEquals(1, hits.get());

        assertFalse(b.mouseClicked(200, 200, 0), "click outside must be ignored");
        assertEquals(1, hits.get());

        assertFalse(b.mouseClicked(20, 15, 1), "right-click should not activate");
        assertEquals(1, hits.get());
    }

    @Test
    @DisplayName("a disabled button never fires")
    void disabledButtonInert() {
        AtomicInteger hits = new AtomicInteger();
        Md3Button b = Md3Button.filled(0, 0, 60, "Nope", hits::incrementAndGet);
        b.setEnabled(false);
        assertFalse(b.mouseClicked(10, 5, 0));
        assertEquals(0, hits.get());
    }

    @Test
    @DisplayName("keyboard activation works on a focused button")
    void buttonKeyboard() {
        AtomicInteger hits = new AtomicInteger();
        Md3Button b = Md3Button.filled(0, 0, 60, "Go", hits::incrementAndGet);
        assertFalse(b.keyPressed(257, 0, 0), "unfocused button must ignore Enter");
        b.setFocused(true);
        assertTrue(b.keyPressed(257, 0, 0), "focused button should take Enter");
        assertEquals(1, hits.get());
        assertTrue(b.keyPressed(32, 0, 0), "space should activate too");
        assertEquals(2, hits.get());
    }

    @Test
    @DisplayName("switch toggles and reports its new state")
    void switchToggles() {
        List<Boolean> seen = new ArrayList<>();
        Md3Switch sw = new Md3Switch(0, 0, false);
        sw.onChange(seen::add);

        sw.mouseClicked(5, 5, 0);
        assertTrue(sw.checked());
        sw.mouseClicked(5, 5, 0);
        assertFalse(sw.checked());
        assertEquals(List.of(true, false), seen);
    }

    @Test
    @DisplayName("slider quantises to its step and clamps to range")
    void sliderSteps() {
        Md3Slider s = new Md3Slider(0, 0, 100, 2, 32, 12, 1);

        // A click outside the track is not the slider's event at all.
        assertFalse(s.mouseClicked(-50, 5, 0), "click outside must not be consumed");
        assertEquals(12.0, s.realValue(), 0.001, "value must be untouched");

        // Clamping is exercised by dragging past the ends, which is what actually
        // happens when a user grabs the handle and keeps moving.
        s.mouseClicked(50, 5, 0);
        s.mouseDragged(-400, 5, 0, -450, 0);
        assertEquals(2.0, s.realValue(), 0.001, "drag past the left end clamps to min");

        s.mouseDragged(900, 5, 0, 1300, 0);
        assertEquals(32.0, s.realValue(), 0.001, "drag past the right end clamps to max");
        s.mouseReleased(900, 5, 0);

        // Every value must land on a whole step.
        for (int px = 0; px <= 100; px += 7) {
            s.mouseClicked(px, 5, 0);
            double v = s.realValue();
            assertEquals(Math.rint(v), v, 1e-6,
                    "step=1 slider produced a fractional value: " + v);
        }
    }

    @Test
    @DisplayName("slider arrow keys move by one step")
    void sliderKeyboard() {
        Md3Slider s = new Md3Slider(0, 0, 100, 0, 10, 5, 1);
        s.setFocused(true);
        assertTrue(s.keyPressed(262, 0, 0)); // right
        assertEquals(6.0, s.realValue(), 1e-6);
        assertTrue(s.keyPressed(263, 0, 0)); // left
        assertEquals(5.0, s.realValue(), 1e-6);
    }

    @Test
    @DisplayName("text field edits, validates and reports")
    void textFieldEditing() {
        List<String> changes = new ArrayList<>();
        Md3TextField tf = new Md3TextField(0, 0, 100, "Port");
        tf.onChange(changes::add);
        tf.validator(v -> v.isEmpty() || v.matches("\\d+"));
        tf.setFocused(true);

        for (char ch : "25565".toCharArray()) tf.charTyped(ch, 0);
        assertEquals("25565", tf.text());
        assertTrue(tf.valid(), "digits should validate");

        tf.charTyped('x', 0);
        assertEquals("25565x", tf.text());
        assertFalse(tf.valid(), "letters should fail the validator");

        tf.keyPressed(259, 0, 0); // backspace
        assertEquals("25565", tf.text());
        assertTrue(tf.valid(), "validity should recover");
        assertEquals(7, changes.size(), "every mutation should notify");
    }

    @Test
    @DisplayName("text field respects maxLength")
    void textFieldMaxLength() {
        Md3TextField tf = new Md3TextField(0, 0, 100, "Short");
        tf.maxLength(3);
        tf.setFocused(true);
        for (char ch : "abcdef".toCharArray()) tf.charTyped(ch, 0);
        assertEquals("abc", tf.text());
    }

    @Test
    @DisplayName("filter chip toggles, other kinds do not latch")
    void chipToggling() {
        Md3Chip filter = new Md3Chip(0, 0, "Survival", Md3Chip.Kind.FILTER);
        filter.setSize(60, 16);
        filter.mouseClicked(10, 8, 0);
        assertTrue(filter.isSelectedChip());
        filter.mouseClicked(10, 8, 0);
        assertFalse(filter.isSelectedChip());

        AtomicInteger assists = new AtomicInteger();
        Md3Chip assist = new Md3Chip(0, 0, "Help", Md3Chip.Kind.ASSIST);
        assist.setSize(60, 16);
        assist.onToggle(v -> assists.incrementAndGet());
        assist.mouseClicked(10, 8, 0);
        assist.mouseClicked(10, 8, 0);
        assertFalse(assist.isSelectedChip(), "assist chips do not latch");
        assertEquals(2, assists.get());
    }

    @Test
    @DisplayName("list selects rows and scrolls without escaping bounds")
    void listInteraction() {
        AtomicInteger opened = new AtomicInteger();
        Md3List list = new Md3List(0, 0, 200, 60);
        for (int i = 0; i < 20; i++) {
            list.add(new Md3List.TextRow("[W]", "World " + i, "Survival", "1.21.11",
                    opened::incrementAndGet));
        }
        assertTrue(list.mouseClicked(10, 5, 0), "first row should be clickable");
        assertEquals(0, list.selectedIndex());
        assertEquals(1, opened.get());

        // Scroll down a long way, then back up past the top.
        for (int i = 0; i < 50; i++) list.mouseScrolled(10, 10, -1);
        frames(list, 60, 10, 10, false);
        for (int i = 0; i < 100; i++) list.mouseScrolled(10, 10, 1);
        frames(list, 60, 10, 10, false);

        Probe p = new Probe();
        list.render(p, SCHEME);
        p.assertBalanced();
    }

    @Test
    @DisplayName("every widget renders with balanced stacks in all states")
    void renderHygiene() {
        List<Md3Widget> all = new ArrayList<>();
        for (Md3Button.Style st : Md3Button.Style.values()) {
            all.add(new Md3Button(4, 4, 70, 20, "Label", st));
        }
        all.add(new Md3Switch(4, 4, true));
        all.add(new Md3Switch(4, 4, false));
        all.add(new Md3Slider(4, 20, 120, 0, 100, 50, 0).label("Vol"));
        all.add(new Md3Slider(4, 20, 120, 0, 10, 3, 1).label("Steps"));
        all.add(new Md3TextField(4, 20, 120, "Name").placeholder("type here"));
        all.add(new Md3Card(4, 4, 100, 40).title("T").body("B"));
        all.add(new Md3Chip(4, 4, "Chip", Md3Chip.Kind.FILTER).selected(true));
        all.add(new Md3Chip(4, 4, "Input", Md3Chip.Kind.INPUT).onRemove(() -> {}));

        List<Md3NavRail.Item> items = List.of(
                new Md3NavRail.Item("[A]", "One", () -> {}),
                new Md3NavRail.Item("[B]", "Two", () -> {}).badge(5),
                new Md3NavRail.Item("[C]", "Three", () -> {}));
        all.add(new Md3NavRail(0, 0, 44, 200, items));
        all.add(new Md3NavRail(0, 0, 300, 32, items).horizontal(true));

        for (Md3Widget w : all) {
            String name = w.getClass().getSimpleName();
            // Resting, hovered, pressed, focused, disabled, and reduced-motion.
            for (boolean disabled : new boolean[] {false, true}) {
                for (boolean rm : new boolean[] {false, true}) {
                    w.setEnabled(!disabled);
                    w.setReducedMotion(rm);
                    w.setFocused(!disabled);
                    frames(w, 12, w.x() + 5, w.y() + 5, true);
                    Probe p = new Probe();
                    w.render(p, SCHEME);
                    p.assertBalanced();
                    assertTrue(p.quads > 0 || !p.texts.isEmpty(),
                            name + " drew nothing (disabled=" + disabled + ")");
                }
            }
        }
    }

    @Test
    @DisplayName("nav rail selects the clicked item")
    void navRailSelection() {
        AtomicInteger second = new AtomicInteger();
        List<Md3NavRail.Item> items = List.of(
                new Md3NavRail.Item("[A]", "One", () -> {}),
                new Md3NavRail.Item("[B]", "Two", second::incrementAndGet),
                new Md3NavRail.Item("[C]", "Three", () -> {}));
        Md3NavRail rail = new Md3NavRail(0, 0, 300, 32, items).horizontal(true);
        // Second slot of three across 300px.
        assertTrue(rail.mouseClicked(150, 16, 0));
        assertEquals(1, rail.selected());
        assertEquals(1, second.get());
    }
}
