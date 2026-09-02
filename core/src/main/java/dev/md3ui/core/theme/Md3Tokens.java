package dev.md3ui.core.theme;

/**
 * MD3 shape, spacing, state and typography constants.
 *
 * <p>Values are in GUI-scaled pixels, already divided by 2 relative to the
 * Android dp figures in the published spec. Minecraft's default GUI scale of 2
 * on a 1080p display puts one logical pixel at roughly 2 physical pixels, which
 * makes Android dp values read as twice too large in-game; halving them keeps
 * the intended optical weight. Anything that must stay finger-sized (touch
 * targets) is kept explicit rather than scaled.
 */
public final class Md3Tokens {
    private Md3Tokens() {}

    // --- Shape scale (MD3 corner radii) ---
    public static final float SHAPE_NONE = 0f;
    public static final float SHAPE_XS = 2f;    // 4dp
    public static final float SHAPE_SM = 4f;    // 8dp
    public static final float SHAPE_MD = 6f;    // 12dp
    public static final float SHAPE_LG = 8f;    // 16dp
    public static final float SHAPE_XL = 14f;   // 28dp
    public static final float SHAPE_FULL = 9999f;

    /** MD3 Expressive superellipse exponent for "cookie" shapes. */
    public static final float SQUIRCLE_N = 3.6f;

    // --- Spacing ---
    public static final float SPACE_XS = 2f;
    public static final float SPACE_SM = 4f;
    public static final float SPACE_MD = 8f;
    public static final float SPACE_LG = 12f;
    public static final float SPACE_XL = 16f;
    public static final float SPACE_XXL = 24f;

    // --- Component metrics ---
    public static final float BUTTON_HEIGHT = 20f;
    public static final float BUTTON_HEIGHT_LARGE = 28f;
    public static final float BUTTON_PADDING_H = 12f;
    public static final float FAB_SIZE = 28f;
    public static final float FAB_SIZE_LARGE = 48f;
    public static final float ICON_BUTTON_SIZE = 20f;
    public static final float TOUCH_TARGET_MIN = 22f;
    public static final float LIST_ITEM_HEIGHT = 28f;
    public static final float LIST_ITEM_HEIGHT_TWO_LINE = 36f;
    public static final float SWITCH_WIDTH = 26f;
    public static final float SWITCH_HEIGHT = 16f;
    public static final float SLIDER_TRACK = 8f;
    public static final float TEXT_FIELD_HEIGHT = 28f;
    public static final float NAV_RAIL_WIDTH = 40f;
    public static final float NAV_BAR_HEIGHT = 32f;
    public static final float APP_BAR_HEIGHT = 32f;
    public static final float DIALOG_MIN_WIDTH = 140f;
    public static final float DIALOG_MAX_WIDTH = 280f;
    public static final float CARD_PADDING = 8f;

    /** Stroke widths. */
    public static final float STROKE_THIN = 1f;
    public static final float STROKE_FOCUS = 2f;

    // --- State layer opacities (MD3 spec) ---
    public static final float STATE_HOVER = 0.08f;
    public static final float STATE_FOCUS = 0.10f;
    public static final float STATE_PRESS = 0.10f;
    public static final float STATE_DRAG = 0.16f;
    public static final float STATE_SELECTED = 0.12f;

    /** Disabled content and container opacities. */
    public static final float DISABLED_CONTENT = 0.38f;
    public static final float DISABLED_CONTAINER = 0.12f;

    /** Scrim behind modal surfaces. */
    public static final float SCRIM_OPACITY = 0.32f;

    // --- Type scale: font scale factors applied to Minecraft's 9px font ---
    public enum Type {
        DISPLAY_LARGE(2.4f, 0f, true),
        DISPLAY_MEDIUM(2.0f, 0f, true),
        DISPLAY_SMALL(1.7f, 0f, true),
        HEADLINE_LARGE(1.55f, 0f, true),
        HEADLINE_MEDIUM(1.35f, 0f, true),
        HEADLINE_SMALL(1.2f, 0f, true),
        TITLE_LARGE(1.15f, 0f, false),
        TITLE_MEDIUM(1.0f, 0.15f, false),
        TITLE_SMALL(0.9f, 0.1f, false),
        BODY_LARGE(1.0f, 0.5f, false),
        BODY_MEDIUM(0.9f, 0.25f, false),
        BODY_SMALL(0.8f, 0.4f, false),
        LABEL_LARGE(0.9f, 0.1f, false),
        LABEL_MEDIUM(0.8f, 0.5f, false),
        LABEL_SMALL(0.75f, 0.5f, false);

        /** Multiplier over the base font height. */
        public final float scale;
        /** Extra tracking in pixels; MD3 body text has positive letter-spacing. */
        public final float tracking;
        /** Display and headline styles get tighter tracking and heavier presence. */
        public final boolean display;

        Type(float scale, float tracking, boolean display) {
            this.scale = scale;
            this.tracking = tracking;
            this.display = display;
        }
    }

    /** Elevation levels 0..5 mapped to shadow spread for floating surfaces only. */
    public static float shadowSpread(int level) {
        switch (Math.max(0, Math.min(level, 5))) {
            case 0: return 0f;
            case 1: return 1f;
            case 2: return 2f;
            case 3: return 3f;
            case 4: return 4f;
            default: return 6f;
        }
    }
}
