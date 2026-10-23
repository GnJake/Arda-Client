package com.gnjake.ardaclient.encyclopedia;

import com.gnjake.ardaclient.ArdaItems;
import com.gnjake.ardaclient.currency.ArdaCurrency;
import java.math.BigDecimal;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/** The total Black Gem value of the player's inventory, shown at the top of the encyclopedia. */
public final class InventoryWorth {
    private static BigDecimal total = BigDecimal.ZERO;
    private static int priced;

    private InventoryWorth() {
    }

    public static void calculate(Minecraft mc) {
        total = BigDecimal.ZERO;
        priced = 0;
        if (mc.player == null) {
            return;
        }

        Inventory inv = mc.player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }

            ArdaCurrency currency = ArdaCurrency.of(stack);
            BigDecimal each = currency != null
                ? currency.inBlackGems()
                : PriceCatalog.book.match(ArdaItems.id(stack), ArdaItems.serverItemType(stack), ArdaItems.lore(stack)).unitPrice();
            if (each != null) {
                total = total.add(each.multiply(BigDecimal.valueOf(stack.getCount())));
                priced += stack.getCount();
            }
        }
    }

    public static String text() {
        return priced == 0 ? "Worth: unpriced" : "Worth: " + PriceBook.gems(total);
    }
}
