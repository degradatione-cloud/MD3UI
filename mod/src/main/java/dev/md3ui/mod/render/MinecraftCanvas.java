package dev.md3ui.mod.render;

import dev.md3ui.core.color.Argb;
import dev.md3ui.core.gfx.Md3Canvas;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Bridges {@link Md3Canvas} onto Minecraft's {@code GuiGraphics}.
 *
 * <p><b>Only three vanilla calls are used:</b> {@code fill}, {@code drawString} and
 * {@code enableScissor}/{@code disableScissor}. That restriction is what makes one
 * source tree compile and run across 1.21.4&ndash;1.21.11 and stay compatible
 * with VulkanMod and Sodium:
 *
 * <ul>
 *   <li>{@code GuiGraphics.pose()} is deliberately never touched. It returns a
 *       {@code PoseStack} up to 1.21.5 and a {@code Matrix3x2fStack} from 1.21.6,
 *       so any code using it needs per-version source. Translation and scale are
 *       therefore applied in software here, to coordinates, before they reach
 *       vanilla.</li>
 *   <li>No {@code RenderType}, {@code RenderPipeline}, shader or buffer access.
 *       Those are exactly the classes VulkanMod replaces; mods that reach into
 *       them break whenever it updates. {@code fill} is renderer-agnostic and
 *       VulkanMod implements it natively.</li>
 *   <li>No texture atlas or sprite lookups, whose identifiers moved repeatedly
 *       across this version range.</li>
 * </ul>
 *
 * <p>The cost of software transforms is negligible: transforms are applied to a
 * handful of floats per quad, not per pixel.
 */
public final class MinecraftCanvas implements Md3Canvas {

    private GuiGraphics gg;
    private Font font;
    private float vw, vh;

    /** {dx, dy, scale, originX, originY} */
    private final Deque<float[]> transforms = new ArrayDeque<>();
    private final Deque<Float> alphas = new ArrayDeque<>();
    private int scissorDepth;

    public MinecraftCanvas() {
        transforms.push(new float[] {0, 0, 1, 0, 0});
        alphas.push(1f);
    }

    /** Rebind for the current frame. Reusing one instance avoids per-frame garbage. */
    public MinecraftCanvas begin(GuiGraphics graphics, float width, float height) {
        this.gg = graphics;
        this.font = Minecraft.getInstance().font;
        this.vw = width;
        this.vh = height;
        transforms.clear();
        transforms.push(new float[] {0, 0, 1, 0, 0});
        alphas.clear();
        alphas.push(1f);
        scissorDepth = 0;
        return this;
    }

    /** Close any scissor left open by a widget that threw mid-render. */
    public void end() {
        while (scissorDepth > 0) {
            gg.disableScissor();
            scissorDepth--;
        }
    }

    private float[] tf() { return transforms.peek(); }

    private float mapX(float x) {
        float[] t = tf();
        return t[3] + (x + t[0] - t[3]) * t[2];
    }

    private float mapY(float y) {
        float[] t = tf();
        return t[4] + (y + t[1] - t[4]) * t[2];
    }

    private float mapS(float v) { return v * tf()[2]; }

    private float alpha() { return alphas.peek(); }

    @Override
    public void fillRect(float x, float y, float w, float h, int argb) {
        if (gg == null || w <= 0 || h <= 0) return;
        int col = Argb.scaleAlpha(argb, alpha());
        if (Argb.a(col) == 0) return;

        float x0 = mapX(x), y0 = mapY(y);
        float x1 = x0 + mapS(w), y1 = y0 + mapS(h);

        int ix0 = Math.round(x0), iy0 = Math.round(y0);
        int ix1 = Math.round(x1), iy1 = Math.round(y1);

        // Sub-pixel geometry (a 1px scanline at scale 1) would round away to
        // nothing; keep at least one pixel so thin strokes and corner scanlines
        // survive. Alpha is scaled by the lost coverage instead.
        if (ix1 <= ix0) {
            float cov = Math.max(0.15f, x1 - x0);
            ix1 = ix0 + 1;
            col = Argb.scaleAlpha(col, Math.min(1f, cov));
        }
        if (iy1 <= iy0) {
            float cov = Math.max(0.15f, y1 - y0);
            iy1 = iy0 + 1;
            col = Argb.scaleAlpha(col, Math.min(1f, cov));
        }
        if (Argb.a(col) == 0) return;

        gg.fill(ix0, iy0, ix1, iy1, col);
    }

    @Override
    public void fillGradientV(float x, float y, float w, float h, int top, int bottom) {
        // Emitted as banded fills rather than vanilla's fillGradient, whose
        // signature and backing render type shift across the version range.
        int rows = Math.max(1, Math.round(mapS(h)));
        rows = Math.min(rows, 256);
        for (int i = 0; i < rows; i++) {
            float t = rows == 1 ? 0f : i / (float) (rows - 1);
            fillRect(x, y + h * i / rows, w, h / rows + 0.5f, Argb.lerp(top, bottom, t));
        }
    }

    @Override
    public float drawText(String text, float x, float y, int argb, boolean shadow) {
        if (gg == null || text == null || text.isEmpty()) return 0;
        int col = Argb.scaleAlpha(argb, alpha());
        if (Argb.a(col) == 0) return textWidth(text);

        float sc = tf()[2];
        int px = Math.round(mapX(x));
        int py = Math.round(mapY(y));

        if (Math.abs(sc - 1f) < 0.02f) {
            gg.drawString(font, text, px, py, col, shadow);
        } else {
            // Scaled text without pose(): draw at integer scale by repeating the
            // string offset by sub-pixels is not viable, so fall back to placing
            // unscaled text at the mapped origin. Callers that need large type
            // compose it from several runs instead.
            gg.drawString(font, text, px, py, col, shadow);
        }
        return textWidth(text);
    }

    @Override
    public float textWidth(String text) {
        if (font == null || text == null) return 0;
        return font.width(text);
    }

    @Override
    public float lineHeight() {
        return font == null ? 9 : font.lineHeight;
    }

    @Override
    public void pushClip(float x, float y, float w, float h) {
        if (gg == null) return;
        int x0 = Math.round(mapX(x));
        int y0 = Math.round(mapY(y));
        int x1 = Math.round(mapX(x) + mapS(w));
        int y1 = Math.round(mapY(y) + mapS(h));
        gg.enableScissor(x0, y0, Math.max(x0, x1), Math.max(y0, y1));
        scissorDepth++;
    }

    @Override
    public void popClip() {
        if (gg == null || scissorDepth == 0) return;
        gg.disableScissor();
        scissorDepth--;
    }

    @Override
    public void pushTranslate(float dx, float dy) {
        float[] t = tf();
        transforms.push(new float[] {t[0] + dx, t[1] + dy, t[2], t[3], t[4]});
    }

    @Override
    public void pushScale(float ox, float oy, float scale) {
        float[] t = tf();
        transforms.push(new float[] {t[0], t[1], t[2] * scale, ox, oy});
    }

    @Override
    public void popTransform() {
        if (transforms.size() > 1) transforms.pop();
    }

    @Override
    public void pushAlpha(float a) { alphas.push(alpha() * Argb.clamp01(a)); }

    @Override
    public void popAlpha() { if (alphas.size() > 1) alphas.pop(); }

    @Override
    public float width() { return vw; }

    @Override
    public float height() { return vh; }
}
