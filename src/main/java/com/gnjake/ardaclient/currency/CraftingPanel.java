package com.gnjake.ardaclient.currency;

import com.gnjake.ardaclient.gui.ArdaTheme;
import com.gnjake.ardaclient.mixin.AbstractContainerScreenAccessor;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.inventory.CraftingMenu;

/**
 * The Currency panel beside a crafting table: how many Zircon, Forged Tungsten and Black Gems the player can make, with
 * a click to fill the grid, a shift-click to craft as many as possible, and All up. Shown only while carrying currency.
 */
public final class CraftingPanel extends AbstractWidget {
    private static final ArdaCurrency[] ROWS = {ArdaCurrency.ZIRCON, ArdaCurrency.FORGED_TUNGSTEN, ArdaCurrency.BLACK_GEM};
    private static final int WIDTH = 118;
    private static final int TITLE = 14;
    private static final int ROW = 12;
    private static final int BUTTON = 14;
    private static final int PAD = 4;
    private static final String HINT = "Shift-click crafts all";

    private final CraftingScreen screen;

    public CraftingPanel(CraftingScreen screen) {
        super(0, 0, WIDTH, 0, Component.literal("Currency"));
        this.screen = screen;
    }

    /** Registers the panel on every crafting table screen. */
    public static void attach(CraftingScreen screen, List<AbstractWidget> widgets) {
        widgets.add(new CraftingPanel(screen));
    }

    private CraftingMenu menu() {
        return this.screen.getMenu();
    }

    private boolean carryingCurrency() {
        for (ArdaCurrency currency : ArdaCurrency.values()) {
            if (CurrencyCrafter.count(this.menu(), currency) > 0) {
                return true;
            }
        }

        return CurrencyCrafter.busy();
    }

    private List<FormattedCharSequence> status(Font font) {
        String status = CurrencyCrafter.status(System.currentTimeMillis());
        return font.split(Component.literal(status.isEmpty() ? HINT : status), WIDTH - PAD * 2);
    }

    /** Places the panel to the right of the crafting table, or to its left when there's no room. */
    private void place(Font font) {
        AbstractContainerScreenAccessor container = (AbstractContainerScreenAccessor)this.screen;
        int right = container.ardaClient$left() + container.ardaClient$width() + 4;
        this.setX(right + WIDTH <= this.screen.width ? right : Math.max(0, container.ardaClient$left() - WIDTH - 4));
        this.setY(container.ardaClient$top());
        this.setHeight(TITLE + ROWS.length * ROW + 3 + BUTTON + 3 + this.status(font).size() * 9 + PAD);
    }

    private int rowTop(int index) {
        return this.getY() + TITLE + index * ROW;
    }

    private int buttonTop() {
        return this.rowTop(ROWS.length) + 3;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        Font font = Minecraft.getInstance().font;
        this.visible = this.carryingCurrency();
        if (!this.visible) {
            return;
        }

        this.place(font);
        int x = this.getX();
        int y = this.getY();
        ArdaTheme.frame(graphics, x, y, this.getWidth(), this.getHeight());
        ArdaTheme.mark(graphics, x + PAD + 2, y + 6);
        ArdaTheme.accentText(graphics, font, "Currency", x + PAD + 7, y + 4, false);
        boolean busy = CurrencyCrafter.busy();
        for (int index = 0; index < ROWS.length; index++) {
            ArdaCurrency currency = ROWS[index];
            int top = this.rowTop(index);
            int makeable = CurrencyCrafter.count(this.menu(), CurrencyCrafter.ingredient(currency)) / CurrencyCrafter.needed(currency);
            boolean hover = !busy && mouseX >= x && mouseX < x + this.getWidth() && mouseY >= top && mouseY < top + ROW;
            ArdaTheme.row(graphics, x + 1, top, this.getWidth() - 2, ROW, hover, false);
            graphics.text(font, currency.plural, x + PAD + 3, top + 2, makeable > 0 ? ArdaTheme.TEXT : ArdaTheme.MUTED);
            String count = String.valueOf(makeable);
            graphics.text(font, count, x + this.getWidth() - PAD - font.width(count), top + 2, makeable > 0 ? ArdaTheme.accentBrightAt(0.0) : ArdaTheme.DIM);
        }

        int buttonTop = this.buttonTop();
        boolean buttonHover = !busy && mouseX >= x + PAD && mouseX < x + this.getWidth() - PAD && mouseY >= buttonTop && mouseY < buttonTop + BUTTON;
        graphics.fill(x + PAD + 1, buttonTop + 1, x + this.getWidth() - PAD - 1, buttonTop + BUTTON - 1, busy ? ArdaTheme.DISABLED : buttonHover ? ArdaTheme.HOVER : ArdaTheme.ROW_ALT);
        graphics.outline(x + PAD, buttonTop, this.getWidth() - PAD * 2, BUTTON, buttonHover ? ArdaTheme.accent() : ArdaTheme.BORDER);
        if (buttonHover) {
            ArdaTheme.accentLine(graphics, x + PAD + 1, buttonTop + BUTTON - 2, x + this.getWidth() - PAD - 1, buttonTop + BUTTON - 1);
        }

        graphics.centeredText(font, "All up", x + this.getWidth() / 2, buttonTop + 3, busy ? ArdaTheme.MUTED : ArdaTheme.TEXT);
        int line = buttonTop + BUTTON + 3;
        boolean hint = CurrencyCrafter.status(System.currentTimeMillis()).isEmpty();
        int color = hint ? ArdaTheme.DIM : CurrencyCrafter.failed() ? ArdaTheme.AMBER : ArdaTheme.MUTED;
        for (FormattedCharSequence text : this.status(font)) {
            graphics.text(font, text, x + PAD + 3, line, color);
            line += 9;
        }
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        if (CurrencyCrafter.busy()) {
            return;
        }

        for (int index = 0; index < ROWS.length; index++) {
            int top = this.rowTop(index);
            if (event.y() >= top && event.y() < top + ROW) {
                CurrencyCrafter.craft(ROWS[index], event.hasShiftDown());
                return;
            }
        }

        int buttonTop = this.buttonTop();
        if (event.y() >= buttonTop && event.y() < buttonTop + BUTTON && event.x() >= this.getX() + PAD && event.x() < this.getX() + this.getWidth() - PAD) {
            CurrencyCrafter.allUp();
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        this.defaultButtonNarrationText(output);
    }
}
