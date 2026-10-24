package com.gnjake.ardaclient.hud;

import com.gnjake.ardaclient.config.ArdaStore;
import com.gnjake.ardaclient.currency.CurrencyTracker;
import com.gnjake.ardaclient.gui.ArdaTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** The player's Mithril (Crude Mithril Ore), including the ender chest when the currency counter's option is on. */
public final class MithrilHud extends HudPanel {
    public static final MithrilHud INSTANCE = new MithrilHud();
    private static final int HEIGHT = 16;

    private MithrilHud() {
    }

    private static String text() {
        return CurrencyTracker.MITHRIL.total(ArdaStore.data.currencyEnderChest) + " Mithril";
    }

    /** The lowest currency panel showing, which the Mithril panels dock under. */
    static HudPanel above(long now) {
        if (TallyHud.INSTANCE.shown(now)) {
            return TallyHud.INSTANCE;
        }

        return CurrencyHud.INSTANCE.shown(now) ? CurrencyHud.INSTANCE : DungeonHud.INSTANCE;
    }

    @Override
    public boolean enabled() {
        return ArdaStore.data.mithrilHud;
    }

    @Override
    protected double scale() {
        return ArdaStore.data.mithrilScale;
    }

    @Override
    protected int contentWidth(Font font, long now) {
        return PAD * 2 + 7 + font.width(text());
    }

    @Override
    protected int contentHeight(Font font, long now) {
        return HEIGHT;
    }

    @Override
    protected int left(int screenWidth, int screenHeight, long now) {
        return ArdaStore.data.mithrilDocked ? dockedLeft(above(now), screenWidth, screenHeight, now) : ArdaStore.data.mithrilLeft;
    }

    @Override
    protected int top(int screenWidth, int screenHeight, long now) {
        return ArdaStore.data.mithrilDocked ? dockedTop(above(now), screenWidth, screenHeight, now) : ArdaStore.data.mithrilTop;
    }

    @Override
    protected void draw(GuiGraphicsExtractor graphics, Font font, int width, int height, long now) {
        ArdaTheme.frame(graphics, 0, 0, width, height, opacity());
        ArdaTheme.mark(graphics, PAD + 2, 6);
        graphics.text(font, text(), PAD + 7, 4, ArdaTheme.TEXT);
    }

    @Override
    public void moveTo(int x, int y) {
        ArdaStore.data.mithrilDocked = false;
        ArdaStore.data.mithrilLeft = Math.max(0, x);
        ArdaStore.data.mithrilTop = Math.max(0, y);
    }

    @Override
    public void reset() {
        ArdaStore.data.mithrilDocked = true;
        ArdaStore.data.mithrilLeft = ArdaStore.DEFAULT_LEFT;
        ArdaStore.data.mithrilTop = ArdaStore.DEFAULT_TOP;
    }
}
