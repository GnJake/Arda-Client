package com.gnjake.ardaclient.hud;

import com.gnjake.ardaclient.config.ArdaStore;
import com.gnjake.ardaclient.dungeon.DungeonTracker;
import com.gnjake.ardaclient.dungeon.WardTracker;
import com.gnjake.ardaclient.gui.ArdaTheme;
import com.gnjake.ardaclient.profit.ProfitEstimator;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Magical Ward charge, what the current dungeon level's hits took, what regenerated and what each food or potion gave, and the
 * last few levels.
 */
public final class WardHud extends HudPanel {
    public static final WardHud INSTANCE = new WardHud();
    private static final int ROW = 10;
    private static final int TITLE = 14;
    private static final int RECENT = 4;
    private static final String NAME = "Magical Ward";
    private static final PerTick<List<String[]>> LINES = new PerTick<>();
    /** Shown in the HUD editor outside a dungeon. */
    private boolean preview;

    private WardHud() {
    }

    @Override
    public boolean enabled() {
        return ArdaStore.data.wardHud;
    }

    @Override
    public boolean shown(long now) {
        return super.shown(now) && (this.preview || WardTracker.shown(now));
    }

    /** Shows the panel outside a dungeon, so it can be placed. */
    public void preview(boolean preview) {
        this.preview = preview;
    }

    /** The panel's rows: {left, right, color}, where a null row is a divider. Worked out once a tick. */
    private static List<String[]> lines() {
        return LINES.get(WardHud::build);
    }

    private static List<String[]> build() {
        List<String[]> lines = new ArrayList<>();
        double ward = WardTracker.current();
        String amount = Double.isNaN(ward) ? "Not shown" : ProfitEstimator.format(ward)
            + (WardTracker.max() > 0 ? " / " + ProfitEstimator.format(WardTracker.max()) : "");
        lines.add(new String[]{"Ward", amount, Double.isNaN(ward) ? "dim" : "text"});
        ArdaStore.WardRun run = WardTracker.run();
        lines.add(new String[]{"Dungeon", run == null ? "None" : run.tier + " " + DungeonTracker.NAMES[run.tier - 1], run == null ? "dim" : "accent"});
        if (run != null) {
            lines.add(new String[]{"Lost to hits", ProfitEstimator.format(run.lost), "lost"});
            lines.add(new String[]{"Regenerated", ProfitEstimator.format(run.regained), "text"});
            run.items.forEach((name, item) -> lines.add(new String[]{name, item.count + " (+" + ProfitEstimator.format(item.ward) + ")", "text"}));
        }

        List<ArdaStore.WardRun> runs = ArdaStore.data.wardRuns;
        if (!runs.isEmpty()) {
            lines.add(null);
            for (int index = runs.size() - 1; index >= Math.max(0, runs.size() - RECENT); index--) {
                ArdaStore.WardRun past = runs.get(index);
                double fromItems = past.items == null ? 0.0 : past.items.values().stream().mapToDouble(item -> item.ward).sum();
                lines.add(new String[]{"Dungeon " + past.tier, "-" + ProfitEstimator.format(past.lost) + "  +" + ProfitEstimator.format(past.regained)
                    + " regen  +" + ProfitEstimator.format(fromItems) + " items", "muted"});
            }
        }

        return lines;
    }

    @Override
    protected double scale() {
        return ArdaStore.data.wardScale;
    }

    @Override
    protected int contentWidth(Font font, long now) {
        int width = font.width(NAME);
        for (String[] line : lines()) {
            if (line != null) {
                width = Math.max(width, font.width(line[0]) + 12 + font.width(line[1]));
            }
        }

        return width + PAD * 2 + 7;
    }

    @Override
    protected int contentHeight(Font font, long now) {
        int height = TITLE + 3;
        for (String[] line : lines()) {
            height += line == null ? 4 : ROW;
        }

        return height;
    }

    @Override
    protected int left(int screenWidth, int screenHeight, long now) {
        return ArdaStore.data.wardDocked ? dockedLeft(above(now), screenWidth, screenHeight, now) : ArdaStore.data.wardLeft;
    }

    @Override
    protected int top(int screenWidth, int screenHeight, long now) {
        return ArdaStore.data.wardDocked ? dockedTop(above(now), screenWidth, screenHeight, now) : ArdaStore.data.wardTop;
    }

    /** The lowest panel above it in the default column. */
    private static HudPanel above(long now) {
        return TowersHud.INSTANCE.shown(now) ? TowersHud.INSTANCE : TowersHud.above(now);
    }

    @Override
    protected void draw(GuiGraphicsExtractor graphics, Font font, int width, int height, long now) {
        ArdaTheme.frame(graphics, 0, 0, width, height, opacity());
        ArdaTheme.mark(graphics, PAD + 2, 6);
        ArdaTheme.accentText(graphics, font, NAME, PAD + 7, 4, false);
        int y = TITLE;
        for (String[] line : lines()) {
            if (line == null) {
                graphics.fill(1, y, width - 1, y + 1, ArdaTheme.fade(ArdaTheme.DIVIDER, opacity()));
                y += 4;
                continue;
            }

            graphics.text(font, line[0], PAD + 7, y, line[2].equals("muted") ? ArdaTheme.MUTED : ArdaTheme.TEXT);
            int color = switch (line[2]) {
                case "dim" -> ArdaTheme.DIM;
                case "accent" -> ArdaTheme.accentBrightAt(0.0);
                case "lost" -> ArdaTheme.AMBER;
                case "muted" -> ArdaTheme.MUTED;
                default -> ArdaTheme.TEXT;
            };
            graphics.text(font, line[1], width - PAD - font.width(line[1]), y, color);
            y += ROW;
        }
    }

    @Override
    public void moveTo(int x, int y) {
        ArdaStore.data.wardDocked = false;
        ArdaStore.data.wardLeft = Math.max(0, x);
        ArdaStore.data.wardTop = Math.max(0, y);
    }

    @Override
    public void reset() {
        ArdaStore.data.wardDocked = true;
        ArdaStore.data.wardLeft = ArdaStore.DEFAULT_LEFT;
        ArdaStore.data.wardTop = ArdaStore.DEFAULT_TOP;
    }
}
