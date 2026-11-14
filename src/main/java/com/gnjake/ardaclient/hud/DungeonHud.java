package com.gnjake.ardaclient.hud;

import com.gnjake.ardaclient.config.ArdaStore;
import com.gnjake.ardaclient.dungeon.DungeonTracker;
import com.gnjake.ardaclient.gui.ArdaTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class DungeonHud extends HudPanel {
    public static final DungeonHud INSTANCE = new DungeonHud();
    private static final int HEADER = 12;
    private static final int ROW = 10;
    private static final int GAP = 8;

    /** Each tier's status, and the prompt below the table, worked out once a tick. */
    private static final PerTick<String[]> STATUSES = new PerTick<>();
    private static final PerTick<String> PROMPT = new PerTick<>();

    private DungeonHud() {
    }

    private static String status(int tier, long now) {
        return STATUSES.get(() -> {
            String[] statuses = new String[DungeonTracker.TIERS];
            for (int index = 1; index <= DungeonTracker.TIERS; index++) {
                statuses[index - 1] = DungeonTracker.status(DungeonTracker.entry(index), now);
            }

            return statuses;
        })[tier - 1];
    }

    private static String prompt(long now) {
        return PROMPT.get(() -> DungeonTracker.prompt(now));
    }

    @Override
    public boolean enabled() {
        return ArdaStore.data.dungeonHud;
    }

    @Override
    protected double scale() {
        return ArdaStore.data.dungeonScale;
    }

    private static int shownCount() {
        int count = 0;

        for (boolean shown : ArdaStore.data.shownDungeons) {
            if (shown) {
                count++;
            }
        }

        return count;
    }

    private static int numberWidth(Font font) {
        return font.width(String.valueOf(DungeonTracker.TIERS)) + 4;
    }

    private static int nameWidth(Font font) {
        int width = 0;

        for (int tier = 1; tier <= DungeonTracker.TIERS; tier++) {
            if (ArdaStore.data.shownDungeons[tier - 1]) {
                width = Math.max(width, font.width(DungeonTracker.NAMES[tier - 1]));
            }
        }

        return width;
    }

    private static int statusWidth(Font font, long now) {
        int width = font.width("Active 00:00");

        for (int tier = 1; tier <= DungeonTracker.TIERS; tier++) {
            if (ArdaStore.data.shownDungeons[tier - 1]) {
                width = Math.max(width, font.width(status(tier, now)));
            }
        }

        return width;
    }

    @Override
    protected int contentWidth(Font font, long now) {
        int table = shownCount() == 0
            ? font.width("No dungeons selected")
            : numberWidth(font) + nameWidth(font) + GAP + statusWidth(font, now);
        String prompt = prompt(now);
        return PAD * 2 + Math.max(Math.max(table, font.width(prompt)), font.width("Dungeons") + 7);
    }

    @Override
    protected int contentHeight(Font font, long now) {
        int rows = Math.max(1, shownCount());
        return HEADER + 3 + rows * ROW + (prompt(now).isEmpty() ? 0 : ROW + 2) + 2;
    }

    @Override
    protected int left(int screenWidth, int screenHeight, long now) {
        return ArdaStore.data.dungeonLeft;
    }

    @Override
    protected int top(int screenWidth, int screenHeight, long now) {
        return ArdaStore.data.dungeonTop;
    }

    @Override
    protected void draw(GuiGraphicsExtractor graphics, Font font, int width, int height, long now) {
        ArdaTheme.frame(graphics, 0, 0, width, height, opacity());
        ArdaTheme.header(graphics, 0, 0, width, HEADER, opacity());
        ArdaTheme.mark(graphics, PAD + 2, 4);
        ArdaTheme.titleText(graphics, font, "Dungeons", PAD + 7, 2);
        int rowY = HEADER + 3;
        if (shownCount() == 0) {
            graphics.text(font, "No dungeons selected", PAD, rowY + 1, ArdaTheme.MUTED);
            rowY += ROW;
        }

        int stripe = 0;

        for (int tier = 1; tier <= DungeonTracker.TIERS; tier++) {
            if (ArdaStore.data.shownDungeons[tier - 1]) {
                ArdaStore.DungeonTier entry = DungeonTracker.entry(tier);
                String status = status(tier, now);
                int color = switch (entry.state) {
                    case "OPEN" -> ArdaTheme.GREEN;
                    case "ACTIVE" -> ArdaTheme.accentBright();
                    case "COOLDOWN" -> ArdaTheme.AMBER;
                    default -> ArdaTheme.DIM;
                };
                if ((stripe++ & 1) == 1) {
                    graphics.fill(1, rowY - 1, width - 1, rowY + ROW - 1, ArdaTheme.fade(ArdaTheme.ROW_ALT, opacity()));
                }

                String number = String.valueOf(tier);
                graphics.text(font, number, PAD + numberWidth(font) - 4 - font.width(number), rowY, ArdaTheme.DIM);
                graphics.text(font, DungeonTracker.NAMES[tier - 1], PAD + numberWidth(font), rowY, ArdaTheme.TEXT);
                graphics.text(font, status, width - PAD - font.width(status), rowY, color);
                rowY += ROW;
            }
        }

        String prompt = prompt(now);
        if (!prompt.isEmpty()) {
            graphics.fill(1, rowY + 1, width - 1, rowY + 2, ArdaTheme.fade(ArdaTheme.DIVIDER, opacity()));
            graphics.text(font, prompt, PAD, rowY + 4, ArdaTheme.MUTED);
        }
    }

    @Override
    public void moveTo(int x, int y) {
        ArdaStore.data.dungeonLeft = Math.max(0, x);
        ArdaStore.data.dungeonTop = Math.max(0, y);
    }

    @Override
    public void reset() {
        ArdaStore.data.dungeonLeft = ArdaStore.DEFAULT_LEFT;
        ArdaStore.data.dungeonTop = ArdaStore.DEFAULT_TOP;
    }
}
