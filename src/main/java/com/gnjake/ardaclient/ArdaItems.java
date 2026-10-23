package com.gnjake.ardaclient;

import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

/** Reads the data the Arda server attaches to its items. */
public final class ArdaItems {
    private ArdaItems() {
    }

    public static String id(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    /** The server's stable item type, e.g. {@code black_gem}, or an empty string for vanilla items. */
    public static String serverItemType(ItemStack stack) {
        return customString(stack, "arda:item_type");
    }

    public static String serverDisplayName(ItemStack stack) {
        return customString(stack, "arda:display_name");
    }

    /** Reads a string the server stored either directly in custom data or under Bukkit's {@code PublicBukkitValues}. */
    public static String customString(ItemStack stack, String key) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null || data.isEmpty()) {
            return "";
        }

        CompoundTag root = data.copyTag();
        String direct = root.getStringOr(key, "");
        return !direct.isBlank() ? direct : root.getCompoundOrEmpty("PublicBukkitValues").getStringOr(key, "");
    }

    public static List<String> lore(ItemStack stack) {
        ItemLore lore = stack.get(DataComponents.LORE);
        return lore == null ? List.of() : lore.lines().stream().map(Component::getString).toList();
    }
}
