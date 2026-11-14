package com.gnjake.ardaclient.hud;

import com.gnjake.ardaclient.config.ArdaStore;
import com.gnjake.ardaclient.currency.ArdaCurrency;
import com.gnjake.ardaclient.currency.CurrencyLedger;
import com.gnjake.ardaclient.currency.CurrencyTracker;
import com.gnjake.ardaclient.gui.ArdaTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Currency earned over the last hour, 3 hours, 12 hours, day and week. Uses the currency counter's scale. */
public final class TallyHud extends HudPanel {
    public static final TallyHud INSTANCE = new TallyHud();
    private static final int HEADER = 12;
    private static final int ROW = 10;
    private static final int GAP = 10;
    private static final String TITLE = "Earned";

    private TallyHud() {
    }

    private static long earned(int window, long now) {
        return CurrencyTracker.CURRENCY.earned(CurrencyLedger.WINDOWS[window], now);
    }

    /** {gems, change} for each period, worked out once a tick. */
    private static final PerTick<String[][]> ROWS = new PerTick<>();
    private static final PerTick<Integer> CHANGE_WIDTH = new PerTick<>();

    private static String[][] rows(long now) {
        return ROWS.get(() -> {
            String[][] rows = new String[CurrencyLedger.WINDOWS.length][];
            for (int window = 0; window < rows.length; window++) {
                long earned = earned(window, now);
                // The change is always shown, even at zero, so a Zircon or Forged Tungsten earned shows up in Bloodstones.
                rows[window] = new String[]{ArdaCurrency.BLACK_GEM.amount(earned / ArdaCurrency.BLOODSTONES_PER_GEM),
                    ArdaCurrency.BLOODSTONE.amount(earned % ArdaCurrency.BLOODSTONES_PER_GEM)};
            }

            return rows;
        });
    }

    private static String gems(int window, long now) {
        return rows(now)[window][0];
    }

    private static String change(int window, long now) {
        return rows(now)[window][1];
    }

    @Override
    public boolean enabled() {
        return ArdaStore.data.tallyHud;
    }

    @Override
    protected double scale() {
        return ArdaStore.data.currencyScale;
    }

    @Override
    protected int contentWidth(Font font, long now) {
        int columns = nameWidth(font) + GAP + gemsWidth(font, now) + GAP + changeWidth(font, now);
        return PAD * 2 + Math.max(columns, font.width(TITLE) + 7);
    }

    private static int nameWidth(Font font) {
        int width = 0;
        for (String name : CurrencyLedger.WINDOW_NAMES) {
            width = Math.max(width, font.width(name));
        }

        return width;
    }

    private static int gemsWidth(Font font, long now) {
        int width = 0;
        for (int window = 0; window < CurrencyLedger.WINDOWS.length; window++) {
            width = Math.max(width, font.width(gems(window, now)));
        }

        return width;
    }

    private static int changeWidth(Font font, long now) {
        return CHANGE_WIDTH.get(() -> measureChange(font, now));
    }

    private static int measureChange(Font font, long now) {
        int width = 0;
        for (int window = 0; window < CurrencyLedger.WINDOWS.length; window++) {
            width = Math.max(width, font.width(change(window, now)));
        }

        return width;
    }

    @Override
    protected int contentHeight(Font font, long now) {
        return HEADER + 3 + CurrencyLedger.WINDOWS.length * ROW + 1;
    }

    @Override
    protected int left(int screenWidth, int screenHeight, long now) {
        if (!ArdaStore.data.tallyDocked) {
            return ArdaStore.data.tallyLeft;
        }

        HudPanel above = CurrencyHud.INSTANCE.shown(now) ? CurrencyHud.INSTANCE : DungeonHud.INSTANCE;
        return dockedLeft(above, screenWidth, screenHeight, now);
    }

    @Override
    protected int top(int screenWidth, int screenHeight, long now) {
        if (!ArdaStore.data.tallyDocked) {
            return ArdaStore.data.tallyTop;
        }

        HudPanel above = CurrencyHud.INSTANCE.shown(now) ? CurrencyHud.INSTANCE : DungeonHud.INSTANCE;
        return dockedTop(above, screenWidth, screenHeight, now);
    }

    @Override
    protected void draw(GuiGraphicsExtractor graphics, Font font, int width, int height, long now) {
        ArdaTheme.frame(graphics, 0, 0, width, height, opacity());
        ArdaTheme.header(graphics, 0, 0, width, HEADER, opacity());
        ArdaTheme.mark(graphics, PAD + 2, 4);
        ArdaTheme.titleText(graphics, font, TITLE, PAD + 7, 2);
        int rowY = HEADER + 3;
        int changeX = width - PAD - changeWidth(font, now);
        for (int window = 0; window < CurrencyLedger.WINDOWS.length; window++) {
            if ((window & 1) == 1) {
                graphics.fill(1, rowY - 1, width - 1, rowY + ROW - 1, ArdaTheme.fade(ArdaTheme.ROW_ALT, opacity()));
            }

            String gems = gems(window, now);
            String change = change(window, now);
            graphics.text(font, CurrencyLedger.WINDOW_NAMES[window], PAD, rowY, ArdaTheme.MUTED);
            graphics.text(font, gems, changeX - GAP - font.width(gems), rowY, ArdaTheme.TEXT);
            graphics.text(font, change, width - PAD - font.width(change), rowY, ArdaTheme.MUTED);
            rowY += ROW;
        }
    }

    @Override
    public void moveTo(int x, int y) {
        ArdaStore.data.tallyDocked = false;
        ArdaStore.data.tallyLeft = Math.max(0, x);
        ArdaStore.data.tallyTop = Math.max(0, y);
    }

    @Override
    public void reset() {
        ArdaStore.data.tallyDocked = true;
        ArdaStore.data.tallyLeft = ArdaStore.DEFAULT_LEFT;
        ArdaStore.data.tallyTop = ArdaStore.DEFAULT_TOP;
    }
}
