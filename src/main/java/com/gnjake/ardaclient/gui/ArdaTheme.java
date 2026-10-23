package com.gnjake.ardaclient.gui;

import com.gnjake.ardaclient.config.ArdaStore;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Arda Client's colors and panel drawing. The accent color is the player's choice, set in HUD Settings. */
public final class ArdaTheme {
    public static final int PANEL = 0xE60E0F12;
    public static final int HEADER = 0xF014161A;
    public static final int ROW = 0xB0181A1F;
    public static final int ROW_ALT = 0xB01D1F25;
    public static final int HOVER = 0xD0262930;
    public static final int DISABLED = 0xAA0E0F12;
    public static final int BORDER = 0xFF2A2D34;
    public static final int DIVIDER = 0xFF202329;
    public static final int TEXT = 0xFFE8E8EA;
    public static final int MUTED = 0xFF9097A0;
    public static final int DIM = 0xFF5F656E;
    public static final int GREEN = 0xFF66BB6A;
    public static final int AMBER = 0xFFFFB74D;
    public static final int UNIQUE = 0xFFCE93D8;

    /** Accent colors offered in HUD Settings. The first is the default. */
    public static final int[] PRESETS = {
        0xC62828, 0xE65100, 0xF9A825, 0x2E7D32, 0x00897B, 0x1E88E5, 0x5E35B1, 0xD81B60, 0xB0BEC5
    };

    private ArdaTheme() {
    }

    public static int accent() {
        return 0xFF000000 | ArdaStore.data.accentColor;
    }

    /** The accent lightened for text and highlights. */
    public static int accentBright() {
        return 0xFF000000 | mix(ArdaStore.data.accentColor, 0xFFFFFF, 0.3);
    }

    /** Background for a selected row: the panel color tinted with the accent. */
    public static int active() {
        return 0xD0000000 | mix(0x16171B, ArdaStore.data.accentColor, 0.18);
    }

    /** Mixes two RGB colors; {@code amount} 0 gives {@code from}, 1 gives {@code to}. */
    public static int mix(int from, int to, double amount) {
        int r = (int)Math.round((from >> 16 & 0xFF) + ((to >> 16 & 0xFF) - (from >> 16 & 0xFF)) * amount);
        int g = (int)Math.round((from >> 8 & 0xFF) + ((to >> 8 & 0xFF) - (from >> 8 & 0xFF)) * amount);
        int b = (int)Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * amount);
        return r << 16 | g << 8 | b;
    }

    /** Scales a color's alpha, for the HUD transparency setting. */
    public static int fade(int argb, double opacity) {
        int alpha = (int)Math.round((argb >>> 24) * Math.max(0.0, Math.min(1.0, opacity)));
        return alpha << 24 | argb & 0xFFFFFF;
    }

    public static void frame(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        frame(g, x, y, w, h, 1.0);
    }

    public static void frame(GuiGraphicsExtractor g, int x, int y, int w, int h, double opacity) {
        g.fill(x, y, x + w, y + h, fade(PANEL, opacity));
        g.outline(x, y, w, h, fade(BORDER, opacity));
        corners(g, x, y, w, h, Math.min(8, Math.max(3, Math.min(w, h) / 6)), fade(accent(), opacity));
    }

    public static void corners(GuiGraphicsExtractor g, int x, int y, int w, int h, int size, int color) {
        g.fill(x, y, x + size, y + 1, color);
        g.fill(x, y, x + 1, y + size, color);
        g.fill(x + w - size, y, x + w, y + 1, color);
        g.fill(x + w - 1, y, x + w, y + size, color);
        g.fill(x, y + h - 1, x + size, y + h, color);
        g.fill(x, y + h - size, x + 1, y + h, color);
        g.fill(x + w - size, y + h - 1, x + w, y + h, color);
        g.fill(x + w - 1, y + h - size, x + w, y + h, color);
    }

    public static void header(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        header(g, x, y, w, h, 1.0);
    }

    public static void header(GuiGraphicsExtractor g, int x, int y, int w, int h, double opacity) {
        g.fill(x + 1, y + 1, x + w - 1, y + h, fade(HEADER, opacity));
        g.fill(x + 1, y + h, x + w - 1, y + h + 1, fade(DIVIDER, opacity));
    }

    public static void row(GuiGraphicsExtractor g, int x, int y, int w, int h, boolean hover, boolean active) {
        g.fill(x, y, x + w, y + h, active ? active() : (hover ? HOVER : ROW));
        if (active) {
            g.fill(x, y, x + 2, y + h, accent());
        }
    }

    /** The Arda Client mark: a small 5x5 diamond centered on {@code x}. */
    public static void mark(GuiGraphicsExtractor g, int x, int y, int color) {
        g.fill(x, y, x + 1, y + 1, color);
        g.fill(x - 1, y + 1, x + 2, y + 2, color);
        g.fill(x - 2, y + 2, x + 3, y + 3, color);
        g.fill(x - 1, y + 3, x + 2, y + 4, color);
        g.fill(x, y + 4, x + 1, y + 5, color);
    }

    public static void scrollbar(GuiGraphicsExtractor g, int x, int top, int height, int thumbTop, int thumbHeight) {
        g.fill(x, top, x + 2, top + height, DIVIDER);
        g.fill(x, thumbTop, x + 2, thumbTop + thumbHeight, accent());
    }
}
