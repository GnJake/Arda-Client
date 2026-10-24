package com.gnjake.ardaclient.currency;

import com.gnjake.ardaclient.gui.ArdaButton;
import com.gnjake.ardaclient.gui.ArdaPanelScreen;
import com.gnjake.ardaclient.gui.ArdaTheme;
import com.gnjake.ardaclient.profit.ProfitEstimator;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

/**
 * The currency or Mithril tally in full: totals and hourly rates for each window, the last 12 hours by hour, and recent
 * earnings.
 */
public final class CurrencyLogScreen extends ArdaPanelScreen {
    private static final int ROW = 10;
    private static final int HOURS = 12;
    private static final int TOTALS_WIDTH = 150;
    private static final int HOURLY_WIDTH = 96;
    private static final int GAP = 12;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);
    private static final DateTimeFormatter DAY_TIME = DateTimeFormatter.ofPattern("EEE HH:mm", Locale.ROOT);

    private int top;
    private int recentLeft;
    private int recentRows;
    private int scroll;
    private boolean confirmReset;
    private Tally showing = Tally.CURRENCY;

    public CurrencyLogScreen(Screen parent) {
        super("Currency Log", parent);
    }

    @Override
    protected void init() {
        this.layout(424, 204);
        this.top = this.py + 31;
        this.recentLeft = this.px + 10 + TOTALS_WIDTH + GAP + HOURLY_WIDTH + GAP;
        this.recentRows = Math.max(1, (this.py + this.ph - 30 - this.top - ROW - 2) / (ROW * 2));
        ArdaButton reset = this.button(this.resetLabel(), this.px + 10, this.py + this.ph - 26, 120, null);
        reset.setAction(() -> {
            if (this.confirmReset) {
                this.tracker().resetTally();
                this.confirmReset = false;
                this.scroll = 0;
                reset.setMessage(this.resetLabel());
            } else {
                this.confirmReset = true;
                reset.setMessage("Click to confirm");
            }
        });
        ArdaButton showing = this.button(this.showingLabel(), this.px + 136, this.py + this.ph - 26, 130, null);
        showing.setAction(() -> {
            this.showing = this.showing == Tally.CURRENCY ? Tally.MITHRIL : Tally.CURRENCY;
            this.scroll = 0;
            this.confirmReset = false;
            showing.setMessage(this.showingLabel());
            reset.setMessage(this.resetLabel());
        });
    }

    /** The reset button clears only the tally on show. */
    private String resetLabel() {
        return "Reset " + this.showing.logName;
    }

    private String showingLabel() {
        return "Showing: " + this.showing.logName;
    }

    private CurrencyTracker tracker() {
        return this.showing == Tally.CURRENCY ? CurrencyTracker.CURRENCY : CurrencyTracker.MITHRIL;
    }

    /** An amount in the hourly and per-hour columns: Black Gems for currency, a count for Mithril. */
    private String amount(double value) {
        return this.showing == Tally.CURRENCY ? gems(value) : ProfitEstimator.format(value);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        long now = System.currentTimeMillis();
        this.drawTotals(graphics, now);
        this.drawHourly(graphics, now);
        this.drawRecent(graphics, now);
    }

    private void drawTotals(GuiGraphicsExtractor graphics, long now) {
        int left = this.px + 10;
        int right = left + TOTALS_WIDTH;
        int y = this.top;
        graphics.text(this.font, "Earned", left, y, ArdaTheme.MUTED);
        this.right(graphics, "Per Hour", right, y, ArdaTheme.MUTED);
        y += ROW + 2;
        for (int window = 0; window < CurrencyLedger.WINDOWS.length; window++) {
            long earned = this.tracker().earned(CurrencyLedger.WINDOWS[window], now);
            double hours = CurrencyLedger.WINDOWS[window] / (double)CurrencyLedger.HOUR;
            if ((window & 1) == 1) {
                graphics.fill(left - 2, y - 1, right + 2, y + ROW * 2 - 1, ArdaTheme.ROW_ALT);
            }

            graphics.text(this.font, CurrencyLedger.WINDOW_NAMES[window], left, y, ArdaTheme.TEXT);
            this.right(graphics, this.amount(earned / hours), right, y, ArdaTheme.MUTED);
            graphics.text(this.font, this.font.plainSubstrByWidth(this.showing.describe(earned), TOTALS_WIDTH), left, y + ROW, ArdaTheme.MUTED);
            y += ROW * 2;
        }
    }

    private void drawHourly(GuiGraphicsExtractor graphics, long now) {
        int left = this.px + 10 + TOTALS_WIDTH + GAP;
        int right = left + HOURLY_WIDTH;
        int y = this.top;
        graphics.text(this.font, "By Hour", left, y, ArdaTheme.MUTED);
        this.right(graphics, this.showing == Tally.CURRENCY ? "Black Gems" : "Mithril", right, y, ArdaTheme.MUTED);
        y += ROW + 2;
        ZonedDateTime hour = ZonedDateTime.now(ZoneId.systemDefault()).truncatedTo(ChronoUnit.HOURS);
        for (int index = 0; index < HOURS; index++) {
            long start = hour.minusHours(index).toInstant().toEpochMilli();
            long end = index == 0 ? now + CurrencyLedger.MINUTE : hour.minusHours(index - 1).toInstant().toEpochMilli();
            long earned = this.tracker().earnedBetween(start, end);
            if ((index & 1) == 1) {
                graphics.fill(left - 2, y - 1, right + 2, y + ROW - 1, ArdaTheme.ROW_ALT);
            }

            if (index == 0) {
                ArdaTheme.accentText(graphics, this.font, hour.format(TIME), left, y);
            } else {
                graphics.text(this.font, hour.minusHours(index).format(TIME), left, y, ArdaTheme.TEXT);
            }
            this.right(graphics, this.amount(earned), right, y, earned > 0L ? ArdaTheme.TEXT : ArdaTheme.DIM);
            y += ROW;
        }
    }

    private void drawRecent(GuiGraphicsExtractor graphics, long now) {
        int left = this.recentLeft;
        int right = this.px + this.pw - 10;
        int width = right - left;
        int y = this.top;
        graphics.text(this.font, "Recent", left, y, ArdaTheme.MUTED);
        y += ROW + 2;
        List<CurrencyLedger.Entry> history = this.tracker().history();
        if (history.isEmpty()) {
            graphics.text(this.font, "Nothing earned yet.", left, y, ArdaTheme.DIM);
            return;
        }

        this.scroll = Math.max(0, Math.min(this.scroll, history.size() - this.recentRows));
        for (int row = 0; row < this.recentRows; row++) {
            int index = history.size() - 1 - this.scroll - row;
            if (index < 0) {
                break;
            }

            CurrencyLedger.Entry entry = history.get(index);
            if ((row & 1) == 1) {
                graphics.fill(left - 2, y - 1, right + 2, y + ROW * 2 - 1, ArdaTheme.ROW_ALT);
            }

            String time = time(entry.time, now);
            graphics.text(this.font, time, left, y, ArdaTheme.MUTED);
            int amountLeft = left + this.font.width(time) + 6;
            graphics.text(this.font, this.font.plainSubstrByWidth(this.showing.describe(entry.bloodstones), right - amountLeft), amountLeft, y, ArdaTheme.TEXT);
            String source = entry.source == null ? "Unknown" : entry.source;
            graphics.text(this.font, this.font.plainSubstrByWidth(source, width), left, y + ROW, ArdaTheme.DIM);
            y += ROW * 2;
        }

        if (history.size() > this.recentRows) {
            int height = this.recentRows * ROW * 2;
            int thumb = Math.max(8, height * this.recentRows / history.size());
            int thumbTop = this.top + ROW + 2 + (height - thumb) * this.scroll / (history.size() - this.recentRows);
            ArdaTheme.scrollbar(graphics, right + 4, this.top + ROW + 2, height, thumbTop, thumb);
        }
    }

    private static String time(long time, long now) {
        ZonedDateTime at = Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault());
        return (now - time < 20 * CurrencyLedger.HOUR ? at.format(TIME) : at.format(DAY_TIME));
    }

    /** Bloodstones as Black Gems with up to two decimals. */
    private static String gems(double bloodstones) {
        return ProfitEstimator.format(bloodstones / ArdaCurrency.BLOODSTONES_PER_GEM);
    }

    private void right(GuiGraphicsExtractor graphics, String text, int right, int y, int color) {
        graphics.text(this.font, text, right - this.font.width(text), y, color);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double dx, double dy) {
        this.scroll = Math.max(0, this.scroll - (int)Math.signum(dy));
        return true;
    }
}
