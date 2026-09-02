package dev.md3ui.core;

import dev.md3ui.core.gfx.Md3Canvas;
import dev.md3ui.core.gfx.Shapes;
import dev.md3ui.core.theme.Md3Tokens;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the scanline shape decomposition through a recording canvas.
 *
 * <p>These assertions are about geometry, not looks: quads must stay inside the
 * requested bounds, corners must actually be inset, and no shape may emit an
 * unbounded number of quads. The last one matters because every quad becomes a
 * draw call in-game.
 */
class ShapesTest {

    /** Canvas that records rectangles instead of drawing them. */
    private static final class Recorder implements Md3Canvas {
        record Quad(float x, float y, float w, float h, int argb) {}

        final List<Quad> quads = new ArrayList<>();
        int clipDepth;
        int maxClipDepth;

        @Override public void fillRect(float x, float y, float w, float h, int argb) {
            quads.add(new Quad(x, y, w, h, argb));
        }
        @Override public void fillGradientV(float x, float y, float w, float h, int a, int b) {
            fillRect(x, y, w, h, a);
        }
        @Override public float drawText(String t, float x, float y, int c, boolean s) {
            return textWidth(t);
        }
        @Override public float textWidth(String text) {
            return text == null ? 0 : text.length() * 6f;
        }
        @Override public float lineHeight() { return 9f; }
        @Override public void pushClip(float x, float y, float w, float h) {
            clipDepth++;
            maxClipDepth = Math.max(maxClipDepth, clipDepth);
        }
        @Override public void popClip() { clipDepth--; }
        @Override public void pushTranslate(float dx, float dy) {}
        @Override public void pushScale(float ox, float oy, float s) {}
        @Override public void popTransform() {}
        @Override public void pushAlpha(float a) {}
        @Override public void popAlpha() {}
        @Override public float width() { return 400f; }
        @Override public float height() { return 300f; }

        float minX() { return quads.stream().map(Quad::x).min(Float::compare).orElse(0f); }
        float minY() { return quads.stream().map(Quad::y).min(Float::compare).orElse(0f); }
        float maxX() {
            return quads.stream().map(q -> q.x() + q.w()).max(Float::compare).orElse(0f);
        }
        float maxY() {
            return quads.stream().map(q -> q.y() + q.h()).max(Float::compare).orElse(0f);
        }
    }

    @Test
    @DisplayName("roundRect stays strictly inside its bounds")
    void roundRectRespectsBounds() {
        Recorder r = new Recorder();
        Shapes.roundRect(r, 10, 20, 100, 40, 8, 0xFF112233);
        assertFalse(r.quads.isEmpty(), "must emit geometry");
        assertTrue(r.minX() >= 10 - 0.01f, "left edge escaped: " + r.minX());
        assertTrue(r.minY() >= 20 - 0.01f, "top edge escaped: " + r.minY());
        assertTrue(r.maxX() <= 110 + 0.01f, "right edge escaped: " + r.maxX());
        assertTrue(r.maxY() <= 60 + 0.01f, "bottom edge escaped: " + r.maxY());
    }

    @Test
    @DisplayName("corners are actually inset, not square")
    void cornersAreRounded() {
        Recorder r = new Recorder();
        Shapes.roundRect(r, 0, 0, 60, 60, 16, 0xFFFFFFFF);
        // The topmost scanline must be narrower than the full width.
        Recorder.Quad top = r.quads.stream()
                .min((a, b) -> Float.compare(a.y(), b.y())).orElseThrow();
        assertTrue(top.w() < 60f - 8f,
                "top scanline should be inset by the corner radius, width=" + top.w());
        assertTrue(top.x() > 0.5f, "top scanline should start inside the left edge");
    }

    @Test
    @DisplayName("radius is clamped so opposite corners cannot cross")
    void radiusClamped() {
        Recorder r = new Recorder();
        // Radius far larger than the box: must degrade to a pill, not invert.
        Shapes.roundRect(r, 0, 0, 40, 20, 500, 0xFF000000);
        assertTrue(r.minX() >= -0.01f && r.maxX() <= 40.01f,
                "clamped shape escaped horizontally");
        assertTrue(r.minY() >= -0.01f && r.maxY() <= 20.01f,
                "clamped shape escaped vertically");
        for (Recorder.Quad q : r.quads) {
            assertTrue(q.w() >= 0, "negative width quad emitted: " + q);
        }
    }

    @Test
    @DisplayName("quad count scales with size, not unboundedly")
    void quadCountIsBounded() {
        Recorder small = new Recorder();
        Shapes.roundRect(small, 0, 0, 60, 20, Md3Tokens.SHAPE_MD, 0xFF000000);
        // A typical button: a handful of scanlines plus the middle band.
        assertTrue(small.quads.size() <= 32,
                "a button should stay cheap, got " + small.quads.size() + " quads");

        Recorder dialog = new Recorder();
        Shapes.roundRect(dialog, 0, 0, 280, 160, Md3Tokens.SHAPE_XL, 0xFF000000);
        assertTrue(dialog.quads.size() <= 64,
                "a dialog should stay cheap, got " + dialog.quads.size() + " quads");
    }

    @Test
    @DisplayName("zero-size and transparent shapes emit nothing")
    void degenerateShapesAreNoOps() {
        Recorder r = new Recorder();
        Shapes.roundRect(r, 0, 0, 0, 20, 4, 0xFF000000);
        Shapes.roundRect(r, 0, 0, 20, 0, 4, 0xFF000000);
        Shapes.roundRect(r, 0, 0, 20, 20, 4, 0x00FFFFFF);
        Shapes.circle(r, 5, 5, 0, 0xFF000000);
        assertTrue(r.quads.isEmpty(), "degenerate input should not draw: " + r.quads.size());
    }

    @Test
    @DisplayName("circle is symmetric about its centre")
    void circleIsSymmetric() {
        Recorder r = new Recorder();
        Shapes.circle(r, 20, 20, 10, 0xFFFFFFFF);
        for (Recorder.Quad q : r.quads) {
            float centre = q.x() + q.w() / 2f;
            assertEquals(20f, centre, 0.51f,
                    "scanline should be centred on cx, got " + centre);
        }
        assertTrue(r.maxY() - r.minY() <= 20.5f, "circle taller than its diameter");
    }

    @Test
    @DisplayName("ring leaves a hole")
    void ringIsHollow() {
        Recorder r = new Recorder();
        Shapes.ring(r, 30, 30, 12, 3, 0xFFFFFFFF);
        // The equator scanline must be split into two spans, one per side of the
        // hole. Match a single row exactly: a +-1 window catches two adjacent
        // rows and would report four spans.
        float equator = r.quads.stream()
                .map(Recorder.Quad::y)
                .min((a, b) -> Float.compare(Math.abs(a + 0.5f - 30f),
                        Math.abs(b + 0.5f - 30f)))
                .orElseThrow();
        long midRow = r.quads.stream()
                .filter(q -> q.y() == equator)
                .count();
        assertEquals(2, midRow,
                "the equator of a ring should be two spans, got " + midRow);
    }

    @Test
    @DisplayName("squircle is wider than a circle at the same size")
    void squircleIsFuller() {
        Recorder circle = new Recorder();
        Shapes.circle(circle, 30, 30, 30, 0xFFFFFFFF);
        Recorder sq = new Recorder();
        Shapes.squircle(sq, 0, 0, 60, 60, Md3Tokens.SQUIRCLE_N, 0xFFFFFFFF);

        double circleArea = circle.quads.stream().mapToDouble(q -> q.w() * q.h()).sum();
        double squircleArea = sq.quads.stream().mapToDouble(q -> q.w() * q.h()).sum();
        assertTrue(squircleArea > circleArea,
                "superellipse should enclose more area than a circle: "
                        + squircleArea + " vs " + circleArea);
        assertTrue(squircleArea < 60 * 60,
                "but still less than the full square");
    }

    @Test
    @DisplayName("outline draws a frame, not a filled box")
    void outlineIsHollow() {
        Recorder r = new Recorder();
        Shapes.roundRectOutline(r, 0, 0, 80, 40, 8, 1f, 0xFFFFFFFF);
        double covered = r.quads.stream().mapToDouble(q -> q.w() * q.h()).sum();
        assertTrue(covered < 80 * 40 * 0.4,
                "an outline should not fill the box, covered " + covered);
        assertTrue(covered > 0, "an outline must draw something");
    }

    @Test
    @DisplayName("clip stack is balanced by every shape helper")
    void clipsAreBalanced() {
        Recorder r = new Recorder();
        Shapes.roundRect(r, 0, 0, 40, 20, 6, 0xFF000000);
        Shapes.circle(r, 10, 10, 5, 0xFF000000);
        Shapes.ring(r, 10, 10, 6, 2, 0xFF000000);
        Shapes.squircle(r, 0, 0, 20, 20, 3.6f, 0xFF000000);
        Shapes.softShadow(r, 0, 0, 40, 20, 6, 3, 0x66000000);
        assertEquals(0, r.clipDepth, "shape helpers must not leak a clip");
    }
}
