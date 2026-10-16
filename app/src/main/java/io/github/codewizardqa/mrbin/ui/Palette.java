package io.github.codewizardqa.mrbin.ui;

/**
 * Every color of the app in one place, taken from Mr. Bin himself: very dark tweed-brown surfaces, the lime green
 * of his background for what you can do, mustard for what you read first, and green / tie red only for results.
 * Text pairs are at least 4.5:1 (WCAG AA) on the surfaces they are used on.
 * res/values/colors.xml repeats BG, SURFACE and ACCENT for the theme (status bar, dialogs, checkboxes).
 */
public final class Palette {
    private Palette() {}

    // Surfaces, from the back to the front.
    public static final int BG = 0xFF12100C;
    public static final int SURFACE = 0xFF1B1813;
    public static final int SURFACE_2 = 0xFF25211A;
    public static final int BORDER = 0xFF383126;

    // Text.
    public static final int TEXT = 0xFFF6F1E7;
    public static final int TEXT_2 = 0xFFC5BBAA;
    public static final int TEXT_3 = 0xFF9A8F7E;

    // Accents.
    public static final int ACCENT = 0xFFB5E24A;
    public static final int HIGHLIGHT = 0xFFF2C14E;
    /** Text on ACCENT / HIGHLIGHT fills. */
    public static final int ON_ACCENT = 0xFF172008;
    /** Background of the card with the main action: a dim glow of ACCENT. */
    public static final int GLOW = 0xFF1A2010;
    public static final int GLOW_BORDER = 0xFF3D5220;
    /** Paused state: the only cool color, so a paused extraction is easy to tell apart. */
    public static final int COOL = 0xFF7DD3FC;

    // Results.
    public static final int SUCCESS = 0xFF4ADE9A;
    public static final int SUCCESS_BG = 0xFF13261E;
    public static final int DANGER = 0xFFFF6461;
    public static final int DANGER_BG = 0xFF2C1615;
}
