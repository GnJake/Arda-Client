package com.gnjake.ardaclient.hud;

import com.gnjake.ardaclient.config.ArdaStore;
import com.gnjake.ardaclient.currency.CurrencyLedger;
import com.gnjake.ardaclient.currency.CurrencyTracker;
import com.gnjake.ardaclient.gui.ArdaTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Mithril earned over the last hour, 3 hours, 12 hours, day and week. Uses the Mithril counter's scale. */
public final class MithrilTallyHud extends HudPanel {
    public static final MithrilTallyHud INSTANCE = new MithrilTallyHud();
    private static final int HEADER = 12;
    private static final int ROW = 10;
    private static final int GAP = 10;
    private static final String TITLE = "Mithril Earned";

    private MithrilTallyHud() {
    }

    private static String amount(int window, long now) {
        return String.valueOf(CurrencyTracker.MITHRIL.earned(CurrencyLedger.WINDOWS[window], now));
    }

    private static HudPanel above(long now) {
        return MithrilHud.INSTANCE.shown(now) ? MithrilHud.INSTANCE : MithrilHud.above(now);
    }

    @Override
    public boolean enabled() {
        return ArdaStore.data.mithrilTallyHud;
    }

    @Override
    protected double scale() {
        return ArdaStore.data.mithrilScale;
    }

    @Override
    protected int contentWidth(Font font, long now) {
        return PAD * 2 + Math.max(nameWidth(font) + GAP + amountWidth(font, now), font.width(TITLE) + 7);
    }

    private static int nameWidth(Font font) {
        int width = 0;
        for (String name : CurrencyLedger.WINDOW_NAMES) {
            width = Math.max(width, font.width(name));
        }

        return width;
    }

    private static int amountWidth(Font font, long now) {
        int width = 0;
        for (int window = 0; window < CurrencyLedger.WINDOWS.length; window++) {
            width = Math.max(width, font.width(amount(window, now)));
        }

        return width;
    }

    @Override
    protected int contentHeight(Font font, long now) {
        return HEADER + 3 + CurrencyLedger.WINDOWS.length * ROW + 1;
    }

    @Override
    protected int left(int screenWidth, int screenHeight, long now) {
        return ArdaStore.data.mithrilTallyDocked ? dockedLeft(above(now), screenWidth, screenHeight, now) : ArdaStore.data.mithrilTallyLeft;
    }

    @Override
    protected int top(int screenWidth, int screenHeight, long now) {
        return ArdaStore.data.mithrilTallyDocked ? dockedTop(above(now), screenWidth, screenHeight, now) : ArdaStore.data.mithrilTallyTop;
    }

    @Override
    protected void draw(GuiGraphicsExtractor graphics, Font font, int width, int height, long now) {
        ArdaTheme.frame(graphics, 0, 0, width, height, opacity());
        ArdaTheme.header(graphics, 0, 0, width, HEADER, opacity());
        ArdaTheme.mark(graphics, PAD + 2, 4);
        ArdaTheme.titleText(graphics, font, TITLE, PAD + 7, 2);
        int rowY = HEADER + 3;
        for (int window = 0; window < CurrencyLedger.WINDOWS.length; window++) {
            if ((window & 1) == 1) {
                graphics.fill(1, rowY - 1, width - 1, rowY + ROW - 1, ArdaTheme.fade(ArdaTheme.ROW_ALT, opacity()));
            }

            String amount = amount(window, now);
            graphics.text(font, CurrencyLedger.WINDOW_NAMES[window], PAD, rowY, ArdaTheme.MUTED);
            graphics.text(font, amount, width - PAD - font.width(amount), rowY, ArdaTheme.TEXT);
            rowY += ROW;
        }
    }

    @Override
    public void moveTo(int x, int y) {
        ArdaStore.data.mithrilTallyDocked = false;
        ArdaStore.data.mithrilTallyLeft = Math.max(0, x);
        ArdaStore.data.mithrilTallyTop = Math.max(0, y);
    }

    @Override
    public void reset() {
        ArdaStore.data.mithrilTallyDocked = true;
        ArdaStore.data.mithrilTallyLeft = ArdaStore.DEFAULT_LEFT;
        ArdaStore.data.mithrilTallyTop = ArdaStore.DEFAULT_TOP;
    }
}
