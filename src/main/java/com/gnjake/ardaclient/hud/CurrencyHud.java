package com.gnjake.ardaclient.hud;

import com.gnjake.ardaclient.config.ArdaStore;
import com.gnjake.ardaclient.currency.ArdaCurrency;
import com.gnjake.ardaclient.gui.ArdaTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/** Counts the currency in the player's inventory, shown as whole Black Gems plus change in Bloodstones. */
public final class CurrencyHud extends HudPanel {
    public static final CurrencyHud INSTANCE = new CurrencyHud();
    private static final int HEIGHT = 16;
    private static final int GAP = 8;
    private static long bloodstones;
    private static int tick;

    private CurrencyHud() {
    }

    public static void update(Minecraft minecraft) {
        if (minecraft.player == null) {
            bloodstones = 0L;
        } else if (++tick % 10 == 0) {
            long total = 0L;
            Inventory inventory = minecraft.player.getInventory();

            for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                ItemStack stack = inventory.getItem(slot);
                ArdaCurrency currency = ArdaCurrency.of(stack);
                if (currency != null) {
                    total += currency.bloodstones * stack.getCount();
                }
            }

            bloodstones = total;
        }
    }

    private static String gems() {
        return ArdaCurrency.BLACK_GEM.amount(bloodstones / ArdaCurrency.BLOODSTONES_PER_GEM);
    }

    private static String change() {
        return ArdaCurrency.BLOODSTONE.amount(bloodstones % ArdaCurrency.BLOODSTONES_PER_GEM);
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
        return PAD * 2 + 7 + font.width(gems()) + GAP + font.width(change());
    }

    @Override
    protected int contentHeight(Font font, long now) {
        return HEIGHT;
    }

    @Override
    protected int left(int screenWidth, int screenHeight, long now) {
        if (ArdaStore.data.currencyDocked) {
            return DungeonHud.INSTANCE.enabled() ? (int)DungeonHud.INSTANCE.bounds(screenWidth, screenHeight, now).x() : ArdaStore.data.dungeonLeft;
        } else {
            return ArdaStore.data.currencyLeft;
        }
    }

    @Override
    protected int top(int screenWidth, int screenHeight, long now) {
        if (ArdaStore.data.currencyDocked) {
            if (DungeonHud.INSTANCE.enabled()) {
                HudPanel.Bounds dungeons = DungeonHud.INSTANCE.bounds(screenWidth, screenHeight, now);
                return (int)Math.ceil(dungeons.y() + dungeons.height()) + 2;
            } else {
                return ArdaStore.data.dungeonTop;
            }
        } else {
            return ArdaStore.data.currencyTop;
        }
    }

    @Override
    protected void draw(GuiGraphicsExtractor graphics, Font font, int width, int height, long now) {
        ArdaTheme.frame(graphics, 0, 0, width, height);
        ArdaTheme.mark(graphics, PAD + 2, 6, ArdaTheme.ACCENT);
        int x = PAD + 7;
        graphics.text(font, gems(), x, 4, ArdaTheme.TEXT);
        graphics.text(font, change(), x + font.width(gems()) + GAP, 4, ArdaTheme.MUTED);
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
