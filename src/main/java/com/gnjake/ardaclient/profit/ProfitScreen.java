package com.gnjake.ardaclient.profit;

import com.gnjake.ardaclient.config.ArdaStore;
import com.gnjake.ardaclient.currency.ArdaCurrency;
import com.gnjake.ardaclient.dungeon.DungeonTracker;
import com.gnjake.ardaclient.gui.ArdaButton;
import com.gnjake.ardaclient.gui.ArdaPanelScreen;
import com.gnjake.ardaclient.gui.ArdaTheme;
import com.gnjake.ardaclient.waypoint.WaypointSettingsScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

/** Estimated profits, on two pages: every dungeon level and party size, and every skirmish level with its bonus. */
public final class ProfitScreen extends ArdaPanelScreen {
    private static final int ROW = 10;
    private static final int SKIRMISH_ROW = 11;
    private static final int NUMBER_WIDTH = 16;
    private static final int NAME_WIDTH = 120;
    private static final int PLAYER_WIDTH = 40;
    private static final int TAB_WIDTH = 80;

    private boolean skirmishes;
    private boolean mithril;
    private int tableTop;
    private ArdaButton dungeonTab;
    private ArdaButton skirmishTab;

    public ProfitScreen(Screen parent) {
        super("Profit Estimator", parent);
    }

    @Override
    protected void init() {
        this.layout(424, 204);
        int x = this.px + 10;
        int y = this.py + 31;
        this.dungeonTab = this.button("Dungeons", x, y, TAB_WIDTH, () -> this.showPage(false));
        this.skirmishTab = this.button("Skirmishes", x + TAB_WIDTH + 4, y, TAB_WIDTH, () -> this.showPage(true));
        x += TAB_WIDTH * 2 + 10;
        ArdaButton chat = this.button(chatLabel(), x, y, 116, null);
        chat.setAction(() -> {
            ArdaStore.data.profitChat = !ArdaStore.data.profitChat;
            chat.setMessage(chatLabel());
            ArdaStore.save();
        });
        x += 122;
        ArdaButton showing = this.button(this.showingLabel(), x, y, this.px + this.pw - 10 - x, null);
        showing.setAction(() -> {
            this.mithril = !this.mithril;
            showing.setMessage(this.showingLabel());
        });
        showing.visible = !this.skirmishes;
        ArdaButton waypoints = this.button("Waypoint Settings", x, y, this.px + this.pw - 10 - x,
            () -> this.minecraft.gui.setScreen(new WaypointSettingsScreen(this)));
        waypoints.visible = this.skirmishes;
        this.dungeonTab.active = this.skirmishes;
        this.skirmishTab.active = !this.skirmishes;
        this.tableTop = y + 28;
    }

    private void showPage(boolean skirmishes) {
        this.skirmishes = skirmishes;
        this.rebuildWidgets();
    }

    private static String chatLabel() {
        return "Chat Estimates: " + (ArdaStore.data.profitChat ? "On" : "Off");
    }

    private String showingLabel() {
        return "Showing: " + (this.mithril ? "~Mithril" : "~Black Gems");
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        ArdaButton current = this.skirmishes ? this.skirmishTab : this.dungeonTab;
        ArdaTheme.accentLine(graphics, current.getX() + 1, current.getBottom() - 2, current.getRight() - 1, current.getBottom() - 1);
        if (this.skirmishes) {
            this.drawSkirmishes(graphics);
        } else {
            this.drawDungeons(graphics);
        }
    }

    private void drawDungeons(GuiGraphicsExtractor graphics) {
        int party = ProfitEstimator.partySize(this.minecraft);
        int left = this.px + 10;
        int right = this.px + this.pw - 10;
        int playersLeft = left + NUMBER_WIDTH + NAME_WIDTH;
        int y = this.tableTop;
        graphics.text(this.font, "Dungeon", left, y, ArdaTheme.MUTED);
        for (int players = 1; players <= ProfitEstimator.MAX_PARTY; players++) {
            String header = players == 1 ? "1 player" : String.valueOf(players);
            if (players == party) {
                ArdaTheme.accentTextRight(graphics, this.font, header, playersLeft + players * PLAYER_WIDTH, y);
            } else {
                this.right(graphics, header, playersLeft + players * PLAYER_WIDTH, y, ArdaTheme.MUTED);
            }
        }

        ArdaTheme.accentTextRight(graphics, this.font, "Party now: " + party, right, y);
        y += ROW + 2;
        for (int tier = 1; tier <= DungeonTracker.TIERS; tier++) {
            if ((tier & 1) == 0) {
                graphics.fill(left - 2, y - 1, playersLeft + ProfitEstimator.MAX_PARTY * PLAYER_WIDTH + 2, y + ROW - 1, ArdaTheme.ROW_ALT);
            }

            this.right(graphics, String.valueOf(tier), left + NUMBER_WIDTH - 5, y, ArdaTheme.accentBrightAt((tier - 1) / (double)(DungeonTracker.TIERS - 1)));
            graphics.text(this.font, this.font.plainSubstrByWidth(DungeonTracker.NAMES[tier - 1], NAME_WIDTH - 4), left + NUMBER_WIDTH, y, ArdaTheme.TEXT);
            for (int players = 1; players <= ProfitEstimator.MAX_PARTY; players++) {
                double value = this.mithril ? ProfitEstimator.dungeonMithril(tier, players) : ProfitEstimator.dungeonGems(tier, players);
                this.right(graphics, ProfitEstimator.format(value), playersLeft + players * PLAYER_WIDTH, y,
                    players == party ? ArdaTheme.TEXT : ArdaTheme.MUTED);
            }

            y += ROW;
        }
    }

    private void drawSkirmishes(GuiGraphicsExtractor graphics) {
        int left = this.px + 10;
        int right = this.px + this.pw - 10;
        int killsRight = left + 112;
        int chestsRight = left + 168;
        int baseRight = left + 246;
        int bonusRight = left + 322;
        int y = this.tableTop;
        graphics.text(this.font, "Skirmish", left, y, ArdaTheme.MUTED);
        this.right(graphics, "Bonus Kills", killsRight, y, ArdaTheme.MUTED);
        this.right(graphics, "Chests", chestsRight, y, ArdaTheme.MUTED);
        this.right(graphics, "~Base", baseRight, y, ArdaTheme.MUTED);
        this.right(graphics, "Max Bonus", bonusRight, y, ArdaTheme.MUTED);
        this.right(graphics, "~Max Total", right, y, ArdaTheme.MUTED);
        y += ROW + 4;
        for (int level = 1; level <= ProfitEstimator.SKIRMISH_LEVELS; level++) {
            if ((level & 1) == 0) {
                graphics.fill(left - 2, y - 2, right + 2, y + SKIRMISH_ROW - 2, ArdaTheme.ROW_ALT);
            }

            long bonus = ProfitEstimator.skirmishMaxBonus(level);
            double total = ProfitEstimator.skirmishGems(level) + bonus / (double)ArdaCurrency.BLOODSTONES_PER_GEM;
            graphics.text(this.font, "Level " + level, left, y, ArdaTheme.accentBrightAt((level - 1) / (double)(ProfitEstimator.SKIRMISH_LEVELS - 1)));
            this.right(graphics, String.valueOf(ProfitEstimator.skirmishBonusKills(level)), killsRight, y, ArdaTheme.MUTED);
            this.right(graphics, String.valueOf(level), chestsRight, y, ArdaTheme.MUTED);
            this.right(graphics, ProfitEstimator.format(ProfitEstimator.skirmishGems(level)), baseRight, y, ArdaTheme.TEXT);
            this.right(graphics, "+" + bonus, bonusRight, y, ArdaTheme.MUTED);
            this.right(graphics, ProfitEstimator.format(total), right, y, ArdaTheme.TEXT);
            y += SKIRMISH_ROW;
        }

        y += 6;
        String[] notes = {
            "Base and Max Total are in Black Gems; Max Bonus is in Bloodstones.",
            "Each bonus kill adds a Bloodstone to every chest; one chest per level.",
            "Skirmish pay doesn't change with party size."
        };
        for (String note : notes) {
            graphics.text(this.font, note, left, y, ArdaTheme.DIM);
            y += ROW;
        }
    }

    private void right(GuiGraphicsExtractor graphics, String text, int right, int y, int color) {
        graphics.text(this.font, text, right - this.font.width(text), y, color);
    }
}
