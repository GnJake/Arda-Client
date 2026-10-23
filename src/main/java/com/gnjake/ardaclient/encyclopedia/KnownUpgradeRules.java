package com.gnjake.ardaclient.encyclopedia;

import com.gnjake.ardaclient.ArdaItems;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.ItemStack;

/** Upgrade prices for Arda's enchantments and unique items, applied to drafts the player captures. */
public final class KnownUpgradeRules {
    private KnownUpgradeRules() {
    }

    public static void apply(PriceBook.Entry entry, ItemStack stack) {
        if (entry.levelUpgrades == null) {
            entry.levelUpgrades = new ArrayList<>();
        }

        if (entry.upgrades == null) {
            entry.upgrades = new ArrayList<>();
        }

        List<String> lore = ArdaItems.lore(stack);
        known(entry, lore, "Bane of Arthropods", 0, 1L, 0L);
        known(entry, lore, "Smite", 0, 1L, 0L);
        known(entry, lore, "Sweeping Edge", 0, 2L, 0L);
        known(entry, lore, "Sharpness", 30, 0L, 8L);
        known(entry, lore, "Unbreaking", 0, 1L, 0L);
        known(entry, lore, "Looting", 0, 3L, 0L);
        known(entry, lore, "Fortune", 0, 1L, 0L);
        known(entry, lore, "Carnage", 100, 2L, 0L);
        if (armor(entry.itemId)) {
            put(entry, protection("Protection", Math.max(0, PriceBook.loreLevel(lore, "Protection"))));
            put(entry, protection("Projectile Protection", Math.max(0, PriceBook.loreLevel(lore, "Projectile Protection"))));
            put(entry, protection("Blast Protection", Math.max(0, PriceBook.loreLevel(lore, "Blast Protection"))));
            put(entry, simple("Unbreaking", Math.max(0, PriceBook.loreLevel(lore, "Unbreaking")), 0, 1L, 0L));
            put(entry, ward("Magical Ward"));
            put(entry, ward("Ward Regen"));
        }

        if ("sting".equals(entry.serverItemType)) {
            put(entry, simple("Sweeping Edge", 0, 0, 2L, 0L));
        }

        if ("mithril_battle_axe".equals(entry.serverItemType)) {
            put(entry, simple("Steal Saturation", 0, 0, 2L, 0L));
        }

        if ("primordial_chest".equals(entry.serverItemType)) {
            put(entry, simple("Carnage", 1, 100, 8L, 0L));
        }

        fixedItemRules(entry);
        if ("wizards_staff".equals(entry.serverItemType)) {
            fixed(entry, "Longer Binds", 4L);
            fixed(entry, "Binds Totems", 4L);

            for (String marker : List.of(
                "Binds Potions",
                "Binds Golden Carrots",
                "Binds Cooked Chicken",
                "Binds Cooked Cod",
                "Binds Porkchops",
                "Binds Cooked Mutton",
                "Binds Cooked Salmon",
                "Binds Cooked Steak"
            )) {
                fixed(entry, marker, 2L);
            }
        }
    }

    public static void repair(PriceBook.Entry entry) {
        if (entry.levelUpgrades == null) {
            entry.levelUpgrades = new ArrayList<>();
        }

        if (entry.upgrades == null) {
            entry.upgrades = new ArrayList<>();
        }

        if ("primordial_chest".equals(entry.serverItemType)) {
            put(entry, protection("Protection", 5));
            put(entry, protection("Projectile Protection", 5));
            put(entry, protection("Blast Protection", 5));
            put(entry, simple("Unbreaking", 3, 0, 1L, 0L));
            put(entry, ward("Magical Ward"));
            put(entry, ward("Ward Regen"));
            put(entry, simple("Carnage", 1, 100, 8L, 0L));
        } else if ("sting".equals(entry.serverItemType)) {
            put(entry, simple("Carnage", 1, 100, 2L, 0L));
            put(entry, simple("Bane of Arthropods", 3, 0, 1L, 0L));
            put(entry, simple("Smite", 3, 0, 1L, 0L));
            put(entry, simple("Sweeping Edge", 0, 0, 2L, 0L));
            put(entry, simple("Sharpness", 20, 30, 0L, 8L));
            put(entry, simple("Unbreaking", 5, 0, 1L, 0L));
            put(entry, simple("Looting", 4, 0, 3L, 0L));
        }

        fixedItemRules(entry);
    }

    private static void fixedItemRules(PriceBook.Entry entry) {
        if ("gauntlet".equals(entry.serverItemType)) {
            fixed(entry, "More Grapples", 6L);
            fixed(entry, "Stronger Pulls", 6L);
        } else if ("anglachel".equals(entry.serverItemType)) {
            fixed(entry, "Stronger Charges", 6L);
            fixed(entry, "More Charges", 6L);
        }
    }

    private static void known(PriceBook.Entry entry, List<String> lore, String name, int cap, long multiplier, long flat) {
        int level = PriceBook.loreLevel(lore, name);
        if (level >= 0) {
            put(entry, simple(name, level, cap, multiplier, flat));
        }
    }

    private static boolean armor(String id) {
        String path = id == null ? "" : id.substring(id.indexOf(':') + 1);
        return path.endsWith("helmet") || path.endsWith("chestplate") || path.endsWith("leggings") || path.endsWith("boots");
    }

    private static PriceBook.LevelUpgrade simple(String name, int start, int cap, long multiplier, long flat) {
        PriceBook.LevelUpgrade rule = new PriceBook.LevelUpgrade();
        rule.name = name;
        rule.startLevel = start;
        rule.maxLevel = cap;
        rule.levelMultiplier = BigDecimal.valueOf(multiplier);
        rule.pricePerLevel = BigDecimal.valueOf(flat);
        return rule;
    }

    private static PriceBook.LevelUpgrade protection(String name, int start) {
        PriceBook.LevelUpgrade rule = simple(name, start, 0, 0L, 0L);
        rule.bands.add(band(1, 10, 1L, 0L, "", 0L));
        rule.bands.add(band(11, 15, 2L, 0L, "", 0L));
        rule.bands.add(band(16, 10000, 3L, 0L, "", 0L));
        return rule;
    }

    private static PriceBook.LevelUpgrade ward(String name) {
        PriceBook.LevelUpgrade rule = simple(name, 0, 0, 0L, 0L);
        rule.bands.add(band(1, 1, 0L, 8L, "", 0L));
        rule.bands.add(band(2, 10, 0L, 0L, "Crude Mithril Ore", 8L));
        rule.bands.add(band(11, 10000, 0L, 0L, "Mithril Ore", 8L));
        return rule;
    }

    private static PriceBook.LevelBand band(int from, int to, long multiplier, long flat, String material, long materialMultiplier) {
        PriceBook.LevelBand band = new PriceBook.LevelBand();
        band.fromLevel = from;
        band.toLevel = to;
        band.levelMultiplier = BigDecimal.valueOf(multiplier);
        band.pricePerLevel = BigDecimal.valueOf(flat);
        band.material = material;
        band.materialLevelMultiplier = materialMultiplier;
        return band;
    }

    private static void put(PriceBook.Entry entry, PriceBook.LevelUpgrade rule) {
        entry.levelUpgrades.removeIf(old -> old.name.equalsIgnoreCase(rule.name));
        entry.levelUpgrades.add(rule);
    }

    private static void fixed(PriceBook.Entry entry, String marker, long gems) {
        entry.upgrades.removeIf(old -> old.loreContains.equalsIgnoreCase(marker));
        PriceBook.Upgrade rule = new PriceBook.Upgrade();
        rule.loreContains = marker;
        rule.addPrice = BigDecimal.valueOf(gems);
        entry.upgrades.add(rule);
    }
}
