package com.gnjake.ardaclient.wheel;

import com.gnjake.ardaclient.gui.ArdaTheme;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

/**
 * Draws the quick wheel, for the wheel itself and the wheel editor. A wheel with more than eight slots grows so the
 * labels keep their room.
 */
public final class WheelRenderer {
    private static final double OUTER_RADIUS = 112.0;
    private static final double INNER_RADIUS = 39.0;
    private static final double LABEL_RADIUS = 76.0;
    /** Gap between neighboring wedges, in radians on each side. */
    private static final double GAP = 0.035;

    private WheelRenderer() {
    }

    /** Where a wheel of {@code count} slots goes and how big it is. {@code scale} 1 is the default wheel size. */
    public static Layout layout(int centerX, int centerY, double scale, int count) {
        double growth = 1.0 + Math.max(0, count - 8) * 0.04;
        return new Layout(centerX, centerY, (int)(OUTER_RADIUS * scale * growth), (int)(INNER_RADIUS * scale * growth),
            LABEL_RADIUS * scale * growth, count, scale);
    }

    public static void draw(GuiGraphicsExtractor graphics, Font font, Layout layout, List<WheelScreen.Section> sections, int selected,
                            double opacity, boolean labels, boolean icons) {
        drawWedges(graphics, layout, selected, opacity);
        drawDisc(graphics, layout.centerX, layout.centerY, layout.inner - 3, opacity);
        float labelScale = layout.count > 10 ? 0.52F : 0.62F;
        for (int index = 0; index < sections.size(); index++) {
            WheelScreen.Section section = sections.get(index);
            if (section == null) {
                continue;
            }

            double angle = -Math.PI / 2 + index * layout.wedge();
            int x = (int)(layout.centerX + Math.cos(angle) * layout.labelRadius);
            int y = (int)(layout.centerY + Math.sin(angle) * layout.labelRadius);
            if (icons) {
                graphics.pose().pushMatrix();
                graphics.pose().translate(x - 8, y - 8);
                graphics.pose().scale((float)Math.max(0.7, layout.scale * 0.9));
                graphics.item(new ItemStack(section.icon()), 0, 0);
                graphics.pose().popMatrix();
            }

            if (labels) {
                small(graphics, font, section.name(), x, y + (icons ? 12 : 0), labelScale, index == selected);
            }
        }
    }

    /** The middle of the wheel: the Arda mark, or the hovered slot's name and description. */
    public static void drawCenter(GuiGraphicsExtractor graphics, Font font, Layout layout, WheelScreen.Section hover, String idle) {
        int centerX = layout.centerX;
        int centerY = layout.centerY;
        if (hover == null) {
            ArdaTheme.mark(graphics, centerX, centerY - 18);
            ArdaTheme.accentTextCentered(graphics, font, idle, centerX, centerY - 5);
            return;
        }

        graphics.centeredText(font, hover.name(), centerX, centerY - 5, ArdaTheme.TEXT);
        graphics.pose().pushMatrix();
        graphics.pose().translate(centerX, centerY + 8);
        graphics.pose().scale(0.55F);
        graphics.text(font, hover.subtitle(), -font.width(hover.subtitle()) / 2, 0, ArdaTheme.MUTED);
        graphics.pose().popMatrix();
    }

    private static void drawWedges(GuiGraphicsExtractor graphics, Layout layout, int selected, double opacity) {
        int outer = layout.outer;
        int innerSquared = layout.inner * layout.inner;
        int outerSquared = outer * outer;
        for (int dy = -outer; dy <= outer; dy++) {
            int run = -1;
            int section = -1;
            for (int dx = -outer; dx <= outer + 1; dx++) {
                int radius = dx * dx + dy * dy;
                int next = dx <= outer && radius >= innerSquared && radius <= outerSquared ? layout.sectionForAngle(Math.atan2(dy, dx)) : -1;
                if (next != section) {
                    if (section >= 0) {
                        graphics.fill(layout.centerX + run, layout.centerY + dy, layout.centerX + dx, layout.centerY + dy + 1,
                            wedgeColor(section == selected, (dy + outer) / (2.0 * outer), opacity));
                    }

                    run = dx;
                    section = next;
                }
            }
        }
    }

    private static void drawDisc(GuiGraphicsExtractor graphics, int centerX, int centerY, int radius, double opacity) {
        int color = (int)(255.0 * Math.max(0.0, Math.min(1.0, opacity)) * 0.94) << 24 | 0x0E0F12;
        for (int y = -radius; y <= radius; y++) {
            int half = (int)Math.sqrt(radius * radius - y * y);
            graphics.fill(centerX - half, centerY + y, centerX + half + 1, centerY + y + 1, color);
        }
    }

    /** {@code height} runs from 0 at the top of the wheel to 1 at the bottom, for the accent gradient. */
    private static int wedgeColor(boolean active, double height, double opacity) {
        int rgb = active ? ArdaTheme.mix(0x15171B, ArdaTheme.accentAt(height) & 0xFFFFFF, 0.2) : 0x15171B;
        int alpha = (int)(255.0 * Math.max(0.0, Math.min(1.0, opacity)) * (active ? 0.92 : 0.86));
        return alpha << 24 | rgb;
    }

    private static void small(GuiGraphicsExtractor graphics, Font font, String text, int x, int y, float scale, boolean selected) {
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale(scale);
        if (selected) {
            ArdaTheme.accentText(graphics, font, text, -font.width(text) / 2, 0, true);
        } else {
            graphics.text(font, text, -font.width(text) / 2, 0, ArdaTheme.TEXT, true);
        }

        graphics.pose().popMatrix();
    }

    public record Layout(int centerX, int centerY, int outer, int inner, double labelRadius, int count, double scale) {
        public double wedge() {
            return Math.PI * 2 / this.count;
        }

        /** The slot under a point, or -1 if it is off the wheel or in a gap. */
        public int sectionAt(double x, double y) {
            double distance = Math.hypot(x - this.centerX, y - this.centerY);
            return distance < this.inner || distance > this.outer ? -1 : this.sectionForAngle(Math.atan2(y - this.centerY, x - this.centerX));
        }

        /** The wedge an angle falls in, or -1 if it is in the gap between two wedges. Wedge 0 points straight up. */
        int sectionForAngle(double angle) {
            double wedge = this.wedge();
            int index = Math.floorMod((int)Math.floor((angle + Math.PI / 2 + wedge / 2) / wedge), this.count);
            double center = -Math.PI / 2 + index * wedge;
            double difference = Math.atan2(Math.sin(angle - center), Math.cos(angle - center));
            return Math.abs(difference) < wedge / 2 - GAP ? index : -1;
        }
    }
}
