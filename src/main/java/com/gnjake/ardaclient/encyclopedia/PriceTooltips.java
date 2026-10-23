package com.gnjake.ardaclient.encyclopedia;

import com.gnjake.ardaclient.ArdaItems;
import com.gnjake.ardaclient.config.ArdaStore;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public final class PriceTooltips {
    public static void register() {
        ItemTooltipCallback.EVENT.register(PriceTooltips::append);
    }

    public static void append(ItemStack stack, TooltipContext context, TooltipFlag flag, List<Component> lines) {
        if (ArdaStore.data.pricingTooltips && !stack.isEmpty()) {
            PriceBook.Inspection result = PriceCatalog.book
                .inspect(ArdaItems.id(stack), ArdaItems.serverItemType(stack), ArdaItems.lore(stack));
            if (result.entry() != null && !result.ambiguous()) {
                if (result.entry().unique) {
                    BigDecimal upgrades = result.upgrades()
                        .stream()
                        .map(PriceBook.AppliedUpgrade::addPrice)
                        .filter(Objects::nonNull)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                    lines.add(Component.literal("UNIQUE").withStyle(ChatFormatting.LIGHT_PURPLE));
                    if (upgrades.signum() > 0 || !result.materials().isEmpty()) {
                        lines.add(
                            Component.literal("Price: ")
                                .withStyle(ChatFormatting.GRAY)
                                .append(Component.literal(total(upgrades, result.materials(), stack.getCount())).withStyle(ChatFormatting.GOLD))
                        );
                    }
                } else if (result.unitPrice() != null && (result.unitPrice().signum() != 0 || !result.materials().isEmpty())) {
                    lines.add(
                        Component.literal("Price: ")
                            .withStyle(ChatFormatting.GRAY)
                            .append(Component.literal(total(result.unitPrice(), result.materials(), stack.getCount())).withStyle(ChatFormatting.GOLD))
                    );
                }
            }
        }
    }

    private static String total(BigDecimal each, Map<String, Long> materials, int amount) {
        ArrayList<String> parts = new ArrayList<>();
        BigDecimal gems = each.multiply(BigDecimal.valueOf(amount));
        if (gems.signum() > 0) {
            parts.add(PriceBook.gems(gems));
        }

        for (Entry<String, Long> material : materials.entrySet()) {
            if (material.getValue() > 0L) {
                parts.add(material.getValue() * amount + " " + material.getKey());
            }
        }

        return String.join(" + ", parts);
    }
}
