package com.gnjake.ardaclient.dungeon;

import com.gnjake.ardaclient.config.ArdaStore;
import com.gnjake.ardaclient.gui.ArdaButton;
import com.gnjake.ardaclient.gui.ArdaPanelScreen;
import com.gnjake.ardaclient.gui.ArdaTheme;
import com.gnjake.ardaclient.profit.ProfitEstimator;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

/**
 * Recent dungeon levels and their Magical Ward: what hits took, what regenerated, and what each food or potion gave,
 * newest first, with the level being run now at the top.
 */
public final class WardLogScreen extends ArdaPanelScreen {
    private static final int ROW = 10;
    private static final int ENTRY = ROW * 3 + 4;
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("EEE HH:mm", Locale.ROOT);

    private int top;
    private int rows;
    private int scroll;
    private boolean confirmClear;

    public WardLogScreen(Screen parent) {
        super("Ward Log", parent);
    }

    @Override
    protected void init() {
        this.layout(424, 224);
        this.top = this.py + 31 + ROW + 6;
        this.rows = Math.max(1, (this.py + this.ph - 30 - this.top) / ENTRY);
        ArdaButton clear = this.button("Clear Log", this.px + 10, this.py + this.ph - 26, 100, null);
        clear.setAction(() -> {
            if (this.confirmClear) {
                ArdaStore.data.wardRuns.clear();
                ArdaStore.save();
                this.confirmClear = false;
                this.scroll = 0;
                clear.setMessage("Clear Log");
            } else {
                this.confirmClear = true;
                clear.setMessage("Click to confirm");
            }
        });
    }

    /** The current level, if one is being run, then saved levels, newest first. */
    private static List<ArdaStore.WardRun> runs() {
        List<ArdaStore.WardRun> runs = new ArrayList<>();
        if (WardTracker.run() != null) {
            runs.add(WardTracker.run());
        }

        List<ArdaStore.WardRun> saved = ArdaStore.data.wardRuns;
        for (int index = saved.size() - 1; index >= 0; index--) {
            runs.add(saved.get(index));
        }

        return runs;
    }

    private static double fromItems(ArdaStore.WardRun run) {
        return run.items == null ? 0.0 : run.items.values().stream().mapToDouble(item -> item.ward).sum();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        int left = this.px + 10;
        int right = this.px + this.pw - 10;
        List<ArdaStore.WardRun> runs = runs();
        this.drawSummary(graphics, runs, left, right, this.py + 31);
        if (runs.isEmpty()) {
            graphics.text(this.font, "No dungeon levels logged yet.", left, this.top, ArdaTheme.DIM);
            graphics.text(this.font, "Ward is logged from teleporting into a level until you finish it.", left, this.top + ROW, ArdaTheme.DIM);
            return;
        }

        this.scroll = Math.max(0, Math.min(this.scroll, runs.size() - this.rows));
        long now = System.currentTimeMillis();
        int y = this.top;
        for (int row = 0; row < this.rows && this.scroll + row < runs.size(); row++) {
            ArdaStore.WardRun run = runs.get(this.scroll + row);
            if ((row & 1) == 1) {
                graphics.fill(left - 2, y - 2, right + 2, y + ENTRY - 2, ArdaTheme.ROW_ALT);
            }

            this.drawRun(graphics, run, run == WardTracker.run(), left, right, y, now);
            y += ENTRY;
        }

        if (runs.size() > this.rows) {
            int height = this.rows * ENTRY;
            int thumb = Math.max(8, height * this.rows / runs.size());
            int thumbTop = this.top + (height - thumb) * this.scroll / (runs.size() - this.rows);
            ArdaTheme.scrollbar(graphics, right + 4, this.top, height, thumbTop, thumb);
        }
    }

    private void drawSummary(GuiGraphicsExtractor graphics, List<ArdaStore.WardRun> runs, int left, int right, int y) {
        double ward = WardTracker.current();
        String charge = Double.isNaN(ward) ? "Ward not shown" : "Ward " + ProfitEstimator.format(ward)
            + (WardTracker.max() > 0 ? " / " + ProfitEstimator.format(WardTracker.max()) : "");
        graphics.text(this.font, charge, left, y, Double.isNaN(ward) ? ArdaTheme.DIM : ArdaTheme.TEXT);
        double lost = 0.0;
        double regained = 0.0;
        double items = 0.0;
        for (ArdaStore.WardRun run : runs) {
            lost += run.lost;
            regained += run.regained;
            items += fromItems(run);
        }

        String totals = runs.size() + (runs.size() == 1 ? " level: " : " levels: ") + "lost " + ProfitEstimator.format(lost) + ", regenerated "
            + ProfitEstimator.format(regained) + ", from items " + ProfitEstimator.format(items);
        graphics.text(this.font, totals, right - this.font.width(totals), y, ArdaTheme.MUTED);
        graphics.fill(left - 2, y + ROW + 1, right + 2, y + ROW + 2, ArdaTheme.DIVIDER);
    }

    private void drawRun(GuiGraphicsExtractor graphics, ArdaStore.WardRun run, boolean current, int left, int right, int y, long now) {
        String name = "Dungeon " + run.tier + "  " + DungeonTracker.NAMES[Math.max(0, Math.min(DungeonTracker.NAMES.length, run.tier) - 1)];
        if (current) {
            ArdaTheme.accentText(graphics, this.font, name, left, y);
        } else {
            graphics.text(this.font, name, left, y, ArdaTheme.TEXT);
        }

        long end = current ? now : run.endedAt;
        String when = (current ? "Now" : Instant.ofEpochMilli(run.startedAt).atZone(ZoneId.systemDefault()).format(WHEN)) + "  " + duration(end - run.startedAt);
        graphics.text(this.font, when, right - this.font.width(when), y, ArdaTheme.MUTED);

        int x = left;
        x = this.stat(graphics, "Lost to hits", run.lost, ArdaTheme.AMBER, x, y + ROW);
        x = this.stat(graphics, "Regenerated", run.regained, ArdaTheme.TEXT, x, y + ROW);
        this.stat(graphics, "From items", fromItems(run), ArdaTheme.GREEN, x, y + ROW);

        StringBuilder items = new StringBuilder();
        if (run.items != null) {
            for (Map.Entry<String, ArdaStore.Consumed> item : run.items.entrySet()) {
                items.append(items.isEmpty() ? "" : "   ").append(item.getKey()).append(" x").append(item.getValue().count)
                    .append(" +").append(ProfitEstimator.format(item.getValue().ward));
            }
        }

        String line = items.isEmpty() ? "No food or potions used" : items.toString();
        graphics.text(this.font, this.font.plainSubstrByWidth(line, right - left), left, y + ROW * 2, ArdaTheme.DIM);
    }

    /** Draws "Label 123" and returns where the next one goes. */
    private int stat(GuiGraphicsExtractor graphics, String label, double value, int color, int x, int y) {
        graphics.text(this.font, label, x, y, ArdaTheme.MUTED);
        x += this.font.width(label) + 4;
        String amount = ProfitEstimator.format(value);
        graphics.text(this.font, amount, x, y, color);
        return x + this.font.width(amount) + 14;
    }

    private static String duration(long milliseconds) {
        long seconds = Math.max(0L, milliseconds / 1000L);
        return seconds >= 3600L ? seconds / 3600L + "h " + seconds % 3600L / 60L + "m" : seconds / 60L + "m " + seconds % 60L + "s";
    }

    @Override
    public boolean mouseScrolled(double x, double y, double dx, double dy) {
        this.scroll = Math.max(0, this.scroll - (int)Math.signum(dy));
        return true;
    }
}
