package com.gnjake.ardaclient.gui;

import com.gnjake.ardaclient.config.ArdaStore;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import java.util.function.DoubleToIntFunction;

/**
 * Arda Client's colors and panel drawing. The accent color is the player's choice, set in HUD Settings; with the
 * gradient on, accents blend from it to a second color, left to right and top to bottom.
 */
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

    /** Where a gradient ends: the second color, or the accent itself with the gradient off. */
    public static int accentEnd() {
        return accentAt(1.0);
    }

    /** The gradient's colors in order, starting with the accent. Just the accent with the gradient off. */
    public static int[] stops() {
        if (!ArdaStore.data.accentGradient) {
            return new int[]{ArdaStore.data.accentColor};
        }

        int[] stops = new int[ArdaStore.data.gradientColors.size() + 1];
        stops[0] = ArdaStore.data.accentColor;
        for (int index = 1; index < stops.length; index++) {
            stops[index] = ArdaStore.data.gradientColors.get(index - 1);
        }

        return stops;
    }

    /** The accent at {@code amount} of the way along the gradient, from 0 (start) to 1 (end). */
    public static int accentAt(double amount) {
        int[] stops = stops();
        if (stops.length == 1) {
            return 0xFF000000 | stops[0];
        }

        double position = Math.max(0.0, Math.min(1.0, amount)) * (stops.length - 1);
        int index = Math.min(stops.length - 2, (int)Math.floor(position));
        return 0xFF000000 | mix(stops[index], stops[index + 1], position - index);
    }

    /** The light accent used for text at {@code amount} of the way along the gradient. */
    public static int accentBrightAt(double amount) {
        return 0xFF000000 | mix(accentAt(amount) & 0xFFFFFF, 0xFFFFFF, 0.3);
    }

    /** Accent text: blended letter by letter with the gradient on, or the light accent with it off. */
    public static void accentText(GuiGraphicsExtractor g, Font font, String text, int x, int y) {
        accentText(g, font, text, x, y, false);
    }

    public static void accentText(GuiGraphicsExtractor g, Font font, String text, int x, int y, boolean shadow) {
        if (!ArdaStore.data.accentGradient || text.length() < 2) {
            g.text(font, text, x, y, accentBright(), shadow);
            return;
        }

        int[] letters = text.codePoints().toArray();
        for (int index = 0; index < letters.length; index++) {
            String letter = Character.toString(letters[index]);
            g.text(font, letter, x, y, accentBrightAt(index / (double)(letters.length - 1)), shadow);
            x += font.width(letter);
        }
    }

    public static void accentTextCentered(GuiGraphicsExtractor g, Font font, String text, int centerX, int y) {
        accentText(g, font, text, centerX - font.width(text) / 2, y);
    }

    public static void accentTextRight(GuiGraphicsExtractor g, Font font, String text, int right, int y) {
        accentText(g, font, text, right - font.width(text), y);
    }

    /** A panel or screen title: in the gradient when it is on, plain text otherwise. */
    public static void titleText(GuiGraphicsExtractor g, Font font, String text, int x, int y) {
        if (ArdaStore.data.accentGradient) {
            accentText(g, font, text, x, y);
        } else {
            g.text(font, text, x, y, TEXT);
        }
    }

    /** Chat text in the accent, blended letter by letter with the gradient on. */
    public static MutableComponent accentComponent(String text) {
        if (!ArdaStore.data.accentGradient || text.length() < 2) {
            return Component.literal(text).withColor(accentBright() & 0xFFFFFF);
        }

        MutableComponent out = Component.empty();
        int[] letters = text.codePoints().toArray();
        for (int index = 0; index < letters.length; index++) {
            out.append(Component.literal(Character.toString(letters[index])).withColor(accentBrightAt(index / (double)(letters.length - 1)) & 0xFFFFFF));
        }

        return out;
    }

    /** Fills a rectangle left to right, coloring each strip by how far along the rectangle it is. */
    public static void horizontalGradient(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, DoubleToIntFunction color) {
        int width = x2 - x1;
        if (width <= 1) {
            g.fill(x1, y1, x2, y2, color.applyAsInt(0.0));
            return;
        }

        int step = Math.max(1, width / 64);
        for (int x = x1; x < x2; x += step) {
            g.fill(x, y1, Math.min(x2, x + step), y2, color.applyAsInt((x - x1 + step / 2.0) / width));
        }
    }

    /** Fills a rectangle top to bottom, coloring each row by how far down the rectangle it is. */
    public static void verticalGradient(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, DoubleToIntFunction color) {
        int height = y2 - y1;
        for (int y = y1; y < y2; y++) {
            g.fill(x1, y, x2, y + 1, color.applyAsInt(height <= 1 ? 0.0 : (y - y1 + 0.5) / height));
        }
    }

    /** A left-to-right accent line, such as a hovered button's underline. */
    public static void accentLine(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2) {
        horizontalGradient(g, x1, y1, x2, y2, ArdaTheme::accentAt);
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
        int size = Math.min(8, Math.max(3, Math.min(w, h) / 6));
        // Each corner takes the gradient's color at its position: start at top left, end at bottom right.
        corners(g, x, y, w, h, size, fade(accent(), opacity), fade(accentAt(0.5), opacity), fade(accentEnd(), opacity));
    }

    public static void corners(GuiGraphicsExtractor g, int x, int y, int w, int h, int size, int color) {
        corners(g, x, y, w, h, size, color, color, color);
    }

    private static void corners(GuiGraphicsExtractor g, int x, int y, int w, int h, int size, int start, int middle, int end) {
        g.fill(x, y, x + size, y + 1, start);
        g.fill(x, y, x + 1, y + size, start);
        g.fill(x + w - size, y, x + w, y + 1, middle);
        g.fill(x + w - 1, y, x + w, y + size, middle);
        g.fill(x, y + h - 1, x + size, y + h, middle);
        g.fill(x, y + h - size, x + 1, y + h, middle);
        g.fill(x + w - size, y + h - 1, x + w, y + h, end);
        g.fill(x + w - 1, y + h - size, x + w, y + h, end);
    }

    public static void header(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        header(g, x, y, w, h, 1.0);
    }

    public static void header(GuiGraphicsExtractor g, int x, int y, int w, int h, double opacity) {
        g.fill(x + 1, y + 1, x + w - 1, y + h, fade(HEADER, opacity));
        if (ArdaStore.data.accentGradient) {
            // A faint wash of the gradient across the header, and the gradient itself along its bottom edge.
            horizontalGradient(g, x + 1, y + 1, x + w - 1, y + h, amount -> fade(0x26000000 | accentAt(amount) & 0xFFFFFF, opacity));
            horizontalGradient(g, x + 1, y + h, x + w - 1, y + h + 1, amount -> fade(accentAt(amount), opacity * 0.7));
        } else {
            g.fill(x + 1, y + h, x + w - 1, y + h + 1, fade(DIVIDER, opacity));
        }
    }

    public static void row(GuiGraphicsExtractor g, int x, int y, int w, int h, boolean hover, boolean active) {
        g.fill(x, y, x + w, y + h, active ? active() : (hover ? HOVER : ROW));
        if (active) {
            verticalGradient(g, x, y, x + 2, y + h, ArdaTheme::accentAt);
        }
    }

    /** The Arda Client mark in the accent, shading from the gradient's start at the top to its end at the bottom. */
    public static void mark(GuiGraphicsExtractor g, int x, int y) {
        g.fill(x, y, x + 1, y + 1, accentAt(0.0));
        g.fill(x - 1, y + 1, x + 2, y + 2, accentAt(0.25));
        g.fill(x - 2, y + 2, x + 3, y + 3, accentAt(0.5));
        g.fill(x - 1, y + 3, x + 2, y + 4, accentAt(0.75));
        g.fill(x, y + 4, x + 1, y + 5, accentAt(1.0));
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
        verticalGradient(g, x, thumbTop, x + 2, thumbTop + thumbHeight, ArdaTheme::accentAt);
    }
}
