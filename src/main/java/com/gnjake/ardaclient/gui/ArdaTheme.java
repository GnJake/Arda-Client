package com.gnjake.ardaclient.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class ArdaTheme {
    public static final int PANEL = 0xE60E0F12;
    public static final int HEADER = 0xF014161A;
    public static final int ROW = 0xB0181A1F;
    public static final int ROW_ALT = 0xB01D1F25;
    public static final int HOVER = 0xD0262930;
    public static final int ACTIVE = 0xD02B1A1C;
    public static final int DISABLED = 0xAA0E0F12;
    public static final int BORDER = 0xFF2A2D34;
    public static final int DIVIDER = 0xFF202329;
    public static final int ACCENT = 0xFFC62828;
    public static final int ACCENT_BRIGHT = 0xFFEF5350;
    public static final int TEXT = 0xFFE8E8EA;
    public static final int MUTED = 0xFF9097A0;
    public static final int DIM = 0xFF5F656E;
    public static final int GREEN = 0xFF66BB6A;
    public static final int AMBER = 0xFFFFB74D;
    public static final int UNIQUE = 0xFFCE93D8;

    private ArdaTheme() {
    }

    public static void frame(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, PANEL);
        g.outline(x, y, w, h, BORDER);
        corners(g, x, y, w, h, Math.min(8, Math.max(3, Math.min(w, h) / 6)));
    }

    public static void corners(GuiGraphicsExtractor g, int x, int y, int w, int h, int size) {
        g.fill(x, y, x + size, y + 1, ACCENT);
        g.fill(x, y, x + 1, y + size, ACCENT);
        g.fill(x + w - size, y, x + w, y + 1, ACCENT);
        g.fill(x + w - 1, y, x + w, y + size, ACCENT);
        g.fill(x, y + h - 1, x + size, y + h, ACCENT);
        g.fill(x, y + h - size, x + 1, y + h, ACCENT);
        g.fill(x + w - size, y + h - 1, x + w, y + h, ACCENT);
        g.fill(x + w - 1, y + h - size, x + w, y + h, ACCENT);
    }

    public static void header(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fill(x + 1, y + 1, x + w - 1, y + h, HEADER);
        g.fill(x + 1, y + h, x + w - 1, y + h + 1, DIVIDER);
    }

    public static void row(GuiGraphicsExtractor g, int x, int y, int w, int h, boolean hover, boolean active) {
        g.fill(x, y, x + w, y + h, active ? ACTIVE : (hover ? HOVER : ROW));
        if (active) {
            g.fill(x, y, x + 2, y + h, ACCENT);
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
        g.fill(x, thumbTop, x + 2, thumbTop + thumbHeight, ACCENT);
    }
}
