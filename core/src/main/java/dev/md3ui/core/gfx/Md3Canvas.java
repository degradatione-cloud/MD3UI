package dev.md3ui.core.gfx;

/**
 * The only drawing surface MD3UI widgets ever touch.
 *
 * <p><b>Why this exists.</b> The mod must not fight whatever renderer the user
 * installed. VulkanMod replaces Minecraft's GL backend wholesale; Sodium
 * rewrites terrain and touches {@code RenderType}. Any mod that ships custom
 * shaders, its own {@code RenderPipeline}, or raw GL calls becomes a
 * compatibility liability the moment those mods change.
 *
 * <p>So the contract here is deliberately tiny: axis-aligned quads, text, and
 * scissor. Every rounded corner, shadow, ripple and gradient in MD3UI is
 * decomposed into those primitives by {@link Shapes} before it reaches a
 * backend. That means the game-facing implementation only ever calls
 * {@code GuiGraphics.fill}/{@code drawString} — API that is stable across
 * 1.21.4&ndash;1.21.11 and untouched by Vulkan/Sodium.
 *
 * <p>The second implementation, {@code RasterCanvas} in the tools module, draws
 * into a {@code BufferedImage}. Both run the same widget code, so the PNGs in
 * the repository are produced by the shipping renderer, not mocked up.
 */
public interface Md3Canvas {

    /** Filled axis-aligned rectangle. Coordinates are in GUI-scaled pixels. */
    void fillRect(float x, float y, float w, float h, int argb);

    /** Vertical linear gradient, {@code topArgb} at {@code y}. */
    void fillGradientV(float x, float y, float w, float h, int topArgb, int bottomArgb);

    /**
     * Draw text. Returns the advance width so callers can lay out inline runs.
     *
     * @param shadow legacy Minecraft drop-shadow; MD3 wants this off almost
     *               everywhere, since elevation is expressed as tone.
     */
    float drawText(String text, float x, float y, int argb, boolean shadow);

    /** Width of {@code text} in the current font, in GUI-scaled pixels. */
    float textWidth(String text);

    /** Line height of the current font. */
    float lineHeight();

    /** Push a clip rectangle; must be paired with {@link #popClip()}. */
    void pushClip(float x, float y, float w, float h);

    void popClip();

    /** Translate subsequent drawing. Paired with {@link #popTransform()}. */
    void pushTranslate(float dx, float dy);

    /**
     * Push a uniform scale about {@code (ox, oy)}. Used by shape-morph and the
     * emphasised container transform.
     */
    void pushScale(float ox, float oy, float scale);

    void popTransform();

    /** Multiply subsequent color alpha by {@code alpha} (0..1). */
    void pushAlpha(float alpha);

    void popAlpha();

    /** Viewport width in GUI-scaled pixels. */
    float width();

    /** Viewport height in GUI-scaled pixels. */
    float height();
}
