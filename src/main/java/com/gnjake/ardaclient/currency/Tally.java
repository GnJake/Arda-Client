package com.gnjake.ardaclient.currency;

import com.gnjake.ardaclient.ArdaItems;
import java.util.Locale;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** What a counter and tally track: currency, counted in Bloodstones, or Mithril, counted in Crude Mithril Ore. */
public enum Tally {
    CURRENCY("Currency"),
    MITHRIL("Mithril");

    private static final String MITHRIL_NAME = "crude mithril ore";

    public final String logName;

    Tally(String logName) {
        this.logName = logName;
    }

    /** How much one item of a stack is worth in this tally's unit, or 0 if it isn't counted here. */
    public long unitValue(ItemStack stack) {
        if (this == CURRENCY) {
            ArdaCurrency currency = ArdaCurrency.of(stack);
            return currency == null ? 0L : currency.bloodstones;
        }

        return isMithril(stack) ? 1L : 0L;
    }

    public String describe(long amount) {
        return this == CURRENCY ? ArdaCurrency.describe(amount) : amount + " Mithril";
    }

    /** Arda's Mithril is a clock named Crude Mithril Ore. */
    public static boolean isMithril(ItemStack stack) {
        if (stack.isEmpty() || !stack.is(Items.CLOCK)) {
            return false;
        }

        String type = ArdaItems.serverItemType(stack);
        if (type.equals("crude_mithril_ore")) {
            return true;
        }

        return (type.isBlank() || type.contains("mithril")) && stack.getHoverName().getString().strip().toLowerCase(Locale.ROOT).equals(MITHRIL_NAME);
    }
}
