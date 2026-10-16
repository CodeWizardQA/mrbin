package io.github.codewizardqa.mrbin.ui;

/**
 * Every color of the app in one place, taken from the Mr. Bin artwork: deep navy surfaces, violet glow around the
 * main action, the neon green of the .BIN file for what you can do, the yellow of the "Mr." of the logo for what
 * you read first (yellow → green is the gradient of the logo and of the main buttons), and green / tie red only for
 * results. Text pairs are at least 4.5:1 (WCAG AA) on the surfaces they are used on.
 * res/values/colors.xml repeats BG, SURFACE and ACCENT for the theme (status bar, dialogs, checkboxes).
 */
public final class Palette {
    private Palette() {}

    // Surfaces, from the back to the front.
    public static final int BG = 0xFF0B0F24;
    public static final int SURFACE = 0xFF131A36;
    public static final int SURFACE_2 = 0xFF1C2547;
    public static final int BORDER = 0xFF2C3766;

    // Text.
    public static final int TEXT = 0xFFF2F4FF;
    public static final int TEXT_2 = 0xFFBCC4E6;
    public static final int TEXT_3 = 0xFF8E99C7;

    // Accents.
    public static final int ACCENT = 0xFF8DEB3F;
    public static final int HIGHLIGHT = 0xFFFFD43B;
    /** Text on ACCENT / HIGHLIGHT fills. */
    public static final int ON_ACCENT = 0xFF0D1A06;
    /** Background of the card with the main action: the violet glow behind Mr. Bin. */
    public static final int GLOW = 0xFF1B1A45;
    public static final int GLOW_BORDER = 0xFF5A45C8;
    /** Paused state: the cyan of the bin, so a paused extraction is easy to tell apart. */
    public static final int COOL = 0xFF4CC9FF;

    // Results.
    public static final int SUCCESS = 0xFF4ADE9A;
    public static final int SUCCESS_BG = 0xFF10292B;
    public static final int DANGER = 0xFFFF5C64;
    public static final int DANGER_BG = 0xFF2E1530;
}
