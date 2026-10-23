package com.gnjake.ardaclient.currency;

import com.gnjake.ardaclient.ArdaItems;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.math.BigDecimal;
import java.util.Locale;
import net.minecraft.core.Holder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;

/**
 * Arda's currencies, valued in Bloodstones: 8 Bloodstones = 1 Zircon, 4 Zircon = 1 Forged Tungsten, 2 Forged Tungsten = 1 Black Gem.
 * Currency is identified by the server's {@code arda:item_type} tag. Without the tag it must be a clock with the currency's
 * name and Knockback III, so an item renamed in an anvil doesn't count.
 */
public enum ArdaCurrency {
    BLOODSTONE("bloodstone", "Bloodstone", "Bloodstones", 1L),
    ZIRCON("zircon", "Zircon", "Zircon", 8L),
    FORGED_TUNGSTEN("forged_tungsten", "Forged Tungsten", "Forged Tungsten", 32L),
    BLACK_GEM("black_gem", "Black Gem", "Black Gems", 64L);

    public static final long BLOODSTONES_PER_GEM = BLACK_GEM.bloodstones;

    public final String serverType;
    public final String singular;
    public final String plural;
    public final long bloodstones;

    ArdaCurrency(String serverType, String singular, String plural, long bloodstones) {
        this.serverType = serverType;
        this.singular = singular;
        this.plural = plural;
        this.bloodstones = bloodstones;
    }

    public String amount(long count) {
        return count + " " + (count == 1L ? this.singular : this.plural);
    }

    public BigDecimal inBlackGems() {
        return BigDecimal.valueOf(this.bloodstones).divide(BigDecimal.valueOf(BLOODSTONES_PER_GEM));
    }

    public static ArdaCurrency of(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        } else {
            String type = ArdaItems.serverItemType(stack);
            if (!type.isBlank()) {
                for (ArdaCurrency currency : values()) {
                    if (currency.serverType.equals(type)) {
                        return currency;
                    }
                }

                return null;
            } else if (stack.is(Items.CLOCK) && hasKnockbackThree(stack)) {
                String name = stack.getHoverName().getString().strip().toLowerCase(Locale.ROOT);

                for (ArdaCurrency currency : values()) {
                    if (currency.singular.toLowerCase(Locale.ROOT).equals(name)) {
                        return currency;
                    }
                }

                return null;
            } else {
                return null;
            }
        }
    }

    private static boolean hasKnockbackThree(ItemStack stack) {
        for (Object2IntMap.Entry<Holder<Enchantment>> entry : stack.getEnchantments().entrySet()) {
            if (entry.getKey().is(Enchantments.KNOCKBACK) && entry.getIntValue() == 3) {
                return true;
            }
        }

        return false;
    }
}
