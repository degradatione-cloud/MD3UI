package dev.md3ui.core.widget;

import dev.md3ui.core.color.Argb;
import dev.md3ui.core.gfx.Md3Canvas;
import dev.md3ui.core.gfx.Shapes;
import dev.md3ui.core.theme.Md3Scheme;
import dev.md3ui.core.theme.Md3Tokens;

/**
 * MD3 card: the container every replaced screen is built out of.
 *
 * <p>Elevation is expressed as a step up the {@code surfaceContainer*} ladder,
 * not a drop shadow. That is the single most common mistake in "material" mods
 * and the reason they look like flat panels with blur behind them; stepping tone
 * is what actually separates a card from its background in MD3.
 */
public final class Md3Card extends Md3Widget {

    public enum Style {
        /** Tonal step up from the background. Default. */
        FILLED,
        /** Filled plus a real shadow. Use sparingly. */
        ELEVATED,
        /** Transparent with a hairline outline. */
        OUTLINED
    }

    private Style style = Style.FILLED;
    private float radius = Md3Tokens.SHAPE_MD;
    private int elevationLevel = 1;
    private String title;
    private String body;
    private Runnable onPress;
    private Integer containerOverride;

    public Md3Card(float x, float y, float w, float h) {
        super(x, y, w, h);
    }

    public Md3Card style(Style s) { this.style = s; return this; }
    public Md3Card radius(float r) { this.radius = r; return this; }
    public Md3Card elevation(int level) { this.elevationLevel = level; return this; }
    public Md3Card title(String t) { this.title = t; return this; }
    public Md3Card body(String b) { this.body = b; return this; }
    public Md3Card container(int argb) { this.containerOverride = argb; return this; }

    public Md3Card onPress(Runnable r) { this.onPress = r; return this; }

    public float radius() { return radius; }

    @Override
    protected boolean onClick(double mouseX, double mouseY, int button) {
        if (onPress == null || button != 0) return false;
        onPress.run();
        return true;
    }

    /** The resolved container colour, so children can pick matching content roles. */
    public int containerColor(Md3Scheme s) {
        if (containerOverride != null) return containerOverride;
        switch (style) {
            case OUTLINED: return 0x00000000;
            case ELEVATED: return s.surfaceContainerLow;
            case FILLED:
            default: return s.surfaceAtElevation(elevationLevel);
        }
    }

    @Override
    public void render(Md3Canvas c, Md3Scheme s) {
        if (!visible) return;

        int container = containerColor(s);

        if (style == Style.ELEVATED) {
            Shapes.softShadow(c, x, y, width, height, radius,
                    Md3Tokens.shadowSpread(elevationLevel), Argb.withAlpha(s.shadow, 80));
        }

        if (Argb.a(container) != 0) {
            Shapes.roundRect(c, x, y, width, height, radius, container);
        }

        if (style == Style.OUTLINED) {
            Shapes.roundRectOutline(c, x, y, width, height, radius,
                    Md3Tokens.STROKE_THIN, s.outlineVariant);
        }

        if (onPress != null) {
            state.draw(c, x, y, width, height, radius, s.onSurface, reducedMotion);
        }

        float cy = y + Md3Tokens.CARD_PADDING;
        if (title != null) {
            c.drawText(title, x + Md3Tokens.CARD_PADDING, cy, s.onSurface, false);
            cy += c.lineHeight() + 2f;
        }
        if (body != null) {
            c.drawText(body, x + Md3Tokens.CARD_PADDING, cy, s.onSurfaceVariant, false);
        }
    }
}
