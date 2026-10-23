package com.gnjake.ardaclient.hud;

import com.gnjake.ardaclient.config.ArdaStore;
import com.gnjake.ardaclient.currency.ArdaCurrency;
import com.gnjake.ardaclient.currency.CurrencyTracker;
import com.gnjake.ardaclient.gui.ArdaTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * The player's currency as whole Black Gems plus change in Bloodstones. With the ender chest option on, the ender
 * chest's contents from the last time it was opened are included.
 */
public final class CurrencyHud extends HudPanel {
    public static final CurrencyHud INSTANCE = new CurrencyHud();
    private static final int HEIGHT = 16;
    private static final int GAP = 8;
    private static final String ENDER_CHEST_HINT = "Open your ender chest";

    private CurrencyHud() {
    }

    private static long bloodstones() {
        return CurrencyTracker.total(ArdaStore.data.currencyEnderChest);
    }

    private static String gems() {
        return ArdaCurrency.BLACK_GEM.amount(bloodstones() / ArdaCurrency.BLOODSTONES_PER_GEM);
    }

    private static String change() {
        return ArdaCurrency.BLOODSTONE.amount(bloodstones() % ArdaCurrency.BLOODSTONES_PER_GEM);
    }

    /** Shown in place of the change until the ender chest has been opened once. */
    private static boolean waitingForEnderChest() {
        return ArdaStore.data.currencyEnderChest && !CurrencyTracker.enderChestSeen();
    }

    private static String secondary() {
        return waitingForEnderChest() ? ENDER_CHEST_HINT : change();
    }

    @Override
    public boolean enabled() {
        return ArdaStore.data.currencyHud;
    }

    @Override
    protected double scale() {
        return ArdaStore.data.currencyScale;
    }

    @Override
    protected int contentWidth(Font font, long now) {
        return PAD * 2 + 7 + font.width(gems()) + GAP + font.width(secondary());
    }

    @Override
    protected int contentHeight(Font font, long now) {
        return HEIGHT;
    }

    @Override
    protected int left(int screenWidth, int screenHeight, long now) {
        return ArdaStore.data.currencyDocked ? dockedLeft(DungeonHud.INSTANCE, screenWidth, screenHeight, now) : ArdaStore.data.currencyLeft;
    }

    @Override
    protected int top(int screenWidth, int screenHeight, long now) {
        return ArdaStore.data.currencyDocked ? dockedTop(DungeonHud.INSTANCE, screenWidth, screenHeight, now) : ArdaStore.data.currencyTop;
    }

    @Override
    protected void draw(GuiGraphicsExtractor graphics, Font font, int width, int height, long now) {
        ArdaTheme.frame(graphics, 0, 0, width, height, opacity());
        ArdaTheme.mark(graphics, PAD + 2, 6, ArdaTheme.accent());
        int x = PAD + 7;
        graphics.text(font, gems(), x, 4, ArdaTheme.TEXT);
        graphics.text(font, secondary(), x + font.width(gems()) + GAP, 4, waitingForEnderChest() ? ArdaTheme.DIM : ArdaTheme.MUTED);
    }

    @Override
    public void moveTo(int x, int y) {
        ArdaStore.data.currencyDocked = false;
        ArdaStore.data.currencyLeft = Math.max(0, x);
        ArdaStore.data.currencyTop = Math.max(0, y);
    }

    @Override
    public void reset() {
        ArdaStore.data.currencyDocked = true;
        ArdaStore.data.currencyLeft = ArdaStore.DEFAULT_LEFT;
        ArdaStore.data.currencyTop = ArdaStore.DEFAULT_TOP;
    }
}
