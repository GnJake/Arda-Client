package com.gnjake.ardaclient.encyclopedia;

import com.gnjake.ardaclient.currency.ArdaCurrency;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class PriceBook {
    public int schema = 1;
    public String currency = "Black Gems";
    public List<Entry> entries = new ArrayList<>();
    public List<Upgrade> globalUpgrades = new ArrayList<>();

    private static final int MAX_COUNT = 1000000000;

    /** Checks every rule and fills in defaults; throws {@link IllegalArgumentException} on the first invalid one. */
    public void validate() {
        if (this.schema != 1
            || this.currency == null
            || this.currency.isBlank()
            || this.currency.length() > 24
            || this.entries == null
            || this.entries.size() > 2048
            || this.globalUpgrades == null
            || this.globalUpgrades.size() > 64) {
            throw new IllegalArgumentException("Invalid catalog header or entry limit");
        }

        for (Upgrade upgrade : this.globalUpgrades) {
            validateUpgrade(upgrade);
        }

        for (Entry e : this.entries) {
            validateEntry(e);
        }
    }

    private static void validateEntry(Entry e) {
        if (e == null
            || e.itemId == null
            || !e.itemId.matches("[a-z0-9_.-]+:[a-z0-9_./-]*\\*?[a-z0-9_./-]*")
            || e.itemId.endsWith(":")
            || e.name == null
            || e.name.length() > 256
            || e.loreContains == null
            || e.loreContains.size() > 64
            || e.requirements == null
            || e.requirements.size() > 8
            || e.upgrades == null
            || e.upgrades.size() > 64
            || e.levelUpgrades == null
            || e.levelUpgrades.size() > 64) {
            throw new IllegalArgumentException("Invalid item rule");
        }

        if (e.tradeAmount == 0) {
            e.tradeAmount = 1;
        }

        if (e.tradeAmount < 1 || e.tradeAmount > MAX_COUNT) {
            throw new IllegalArgumentException("Invalid trader output amount");
        }

        amount(e.unitPrice);
        cost(e.cost);
        if (value(e) == null) {
            e.unique = true;
        }

        for (String line : e.loreContains) {
            if (line == null || line.isBlank() || line.length() > 512) {
                throw new IllegalArgumentException("Invalid lore rule");
            }
        }

        for (Requirement r : e.requirements) {
            if (r == null
                || r.itemId == null
                || !r.itemId.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")
                || r.name == null
                || r.name.length() > 256
                || r.count < 1
                || r.count > MAX_COUNT) {
                throw new IllegalArgumentException("Invalid trade requirement");
            }
        }

        for (Upgrade u : e.upgrades) {
            validateUpgrade(u);
        }

        for (LevelUpgrade u : e.levelUpgrades) {
            validateLevelUpgrade(u);
        }
    }

    private static void validateLevelUpgrade(LevelUpgrade u) {
        if (u == null
            || u.name == null
            || u.name.isBlank()
            || u.name.length() > 128
            || u.startLevel < 0
            || u.maxLevel < 0
            || u.maxLevel > 0 && u.maxLevel < u.startLevel
            || u.maxLevel > 10000) {
            throw new IllegalArgumentException("Invalid level upgrade rule");
        }

        if (u.levelMultiplier == null) {
            u.levelMultiplier = BigDecimal.ZERO;
        }

        if (u.pricePerLevel == null) {
            u.pricePerLevel = BigDecimal.ZERO;
        }

        if (u.bands == null) {
            u.bands = new ArrayList<>();
        }

        amount(u.levelMultiplier);
        amount(u.pricePerLevel);
        if (u.bands.size() > 32) {
            throw new IllegalArgumentException("Too many level price bands");
        }

        for (LevelBand band : u.bands) {
            validateBand(band);
        }

        if (u.bands.isEmpty() && u.levelMultiplier.signum() == 0 && u.pricePerLevel.signum() == 0) {
            throw new IllegalArgumentException("Empty level upgrade price");
        }
    }

    private static void validateBand(LevelBand band) {
        if (band == null || band.fromLevel < 1 || band.toLevel < band.fromLevel || band.toLevel > 10000) {
            throw new IllegalArgumentException("Invalid level price band");
        }

        if (band.levelMultiplier == null) {
            band.levelMultiplier = BigDecimal.ZERO;
        }

        if (band.pricePerLevel == null) {
            band.pricePerLevel = BigDecimal.ZERO;
        }

        if (band.material == null) {
            band.material = "";
        }

        amount(band.levelMultiplier);
        amount(band.pricePerLevel);
        boolean usesMaterial = band.materialLevelMultiplier > 0L || band.materialPerLevel > 0L;
        if (band.materialLevelMultiplier < 0L
            || band.materialPerLevel < 0L
            || band.materialLevelMultiplier > MAX_COUNT
            || band.materialPerLevel > MAX_COUNT
            || usesMaterial && (band.material.isBlank() || band.material.length() > 128)) {
            throw new IllegalArgumentException("Invalid level material cost");
        }

        if (band.levelMultiplier.signum() == 0 && band.pricePerLevel.signum() == 0 && !usesMaterial) {
            throw new IllegalArgumentException("Empty level price band");
        }
    }

    private static void validateUpgrade(Upgrade u) {
        if (u != null && u.loreContains != null && !u.loreContains.isBlank() && u.loreContains.length() <= 512) {
            if (u.addPrice == null) {
                u.addPrice = BigDecimal.ZERO;
            }

            amount(u.addPrice);
            cost(u.cost);
        } else {
            throw new IllegalArgumentException("Invalid upgrade rule");
        }
    }

    private static void cost(Cost c) {
        if (c != null
            && (
                c.bloodstones < 0L
                    || c.zircon < 0L
                    || c.forgedTungsten < 0L
                    || c.blackGems < 0L
                    || c.bloodstones > MAX_COUNT
                    || c.zircon > MAX_COUNT
                    || c.forgedTungsten > MAX_COUNT
                    || c.blackGems > MAX_COUNT
            )) {
            throw new IllegalArgumentException("Invalid mixed-currency cost");
        }
    }

    private static BigDecimal value(Entry e) {
        return e.cost != null && !e.cost.empty() ? e.cost.inBlackGems() : e.unitPrice;
    }

    private static BigDecimal value(Upgrade u) {
        return u.cost != null && !u.cost.empty() ? u.cost.inBlackGems() : u.addPrice;
    }

    private static void amount(BigDecimal n) {
        if (n != null && (n.signum() < 0 || n.precision() > 24 || n.scale() > 6 || n.abs().compareTo(new BigDecimal("1000000000000000")) > 0)) {
            throw new IllegalArgumentException("Price must be 0 to 1 quadrillion, up to 6 decimals");
        }
    }

    /** Finds the most specific entry for an item and prices its upgrades. Ties between equally specific entries are ambiguous. */
    public Inspection inspect(String id, String serverItemType, List<String> lore) {
        Entry best = null;
        int score = -1;
        boolean ambiguous = false;

        for (Entry e : this.entries) {
            boolean stableType = e.serverItemType != null && !e.serverItemType.isBlank();
            if ((stableType ? e.serverItemType.equals(serverItemType) : idMatches(e.itemId, id))
                && (stableType || e.loreContains.stream().allMatch(s -> lore.stream().anyMatch(l -> l.contains(s))))) {
                // An exact item ID beats a wildcard such as minecraft:*_spawn_egg with the same lore rules.
                int specificity = stableType ? 10000 : e.loreContains.size() * 2 + (e.itemId.indexOf('*') < 0 ? 1 : 0);
                if (specificity > score) {
                    best = e;
                    score = specificity;
                    ambiguous = false;
                } else if (specificity == score) {
                    ambiguous = true;
                }
            }
        }

        if (best != null && !ambiguous) {
            BigDecimal base = value(best);
            BigDecimal price = base;
            ArrayList<AppliedUpgrade> applied = new ArrayList<>();
            LinkedHashMap<String, Long> materials = new LinkedHashMap<>();

            for (Requirement requirement : best.requirements) {
                materials.merge(requirement.name, (long)requirement.count, Long::sum);
            }

            for (List<Upgrade> rules : List.of(best.upgrades, this.globalUpgrades)) {
                for (Upgrade u : rules) {
                    if (lore.stream().anyMatch(s -> s.contains(u.loreContains))) {
                        BigDecimal addition = value(u);
                        if (price != null) {
                            price = price.add(addition);
                        }

                        applied.add(new AppliedUpgrade(u.loreContains, addition, u.cost));
                    }
                }
            }

            for (LevelUpgrade ux : best.levelUpgrades) {
                String marker = ux.name.equalsIgnoreCase("Carnage") ? carnageType(lore) : ux.name;
                if (!ux.name.equalsIgnoreCase("Carnage") || !marker.isEmpty()) {
                    int seen = loreLevel(lore, ux.name);
                    if (seen > ux.startLevel) {
                        int level = ux.maxLevel > 0 ? Math.min(seen, ux.maxLevel) : seen;
                        BigDecimal addition = levelPrice(ux, level);
                        levelMaterials(ux, level, materials);
                        if (price != null) {
                            price = price.add(addition);
                        }

                        applied.add(new AppliedUpgrade(marker + " " + level, addition, null));
                    }
                }
            }

            Map<String, Long> finalMaterials = Collections.unmodifiableMap(new LinkedHashMap<>(materials));
            if (best.unique) {
                return new Inspection(best, null, null, List.copyOf(applied), finalMaterials, false);
            } else {
                return price == null
                    ? new Inspection(best, base, null, List.copyOf(applied), finalMaterials, false)
                    : new Inspection(best, base, price, List.copyOf(applied), finalMaterials, false);
            }
        } else {
            return new Inspection(best, best == null ? null : best.unitPrice, null, List.of(), Map.of(), ambiguous);
        }
    }

    /** Matches an item ID against a rule that may contain one {@code *} wildcard. */
    public static boolean idMatches(String rule, String id) {
        int star = rule.indexOf('*');
        if (star < 0) {
            return rule.equals(id);
        } else {
            String prefix = rule.substring(0, star);
            String suffix = rule.substring(star + 1);
            return id != null && id.length() >= prefix.length() + suffix.length() && id.startsWith(prefix) && id.endsWith(suffix);
        }
    }

    private static final DecimalFormat NUMBER = new DecimalFormat("#,##0.######");

    /** Formats a Black Gem value, e.g. "1 Black Gem" or "0.5 Black Gems". */
    public static String gems(BigDecimal value) {
        return NUMBER.format(value) + (value.compareTo(BigDecimal.ONE) == 0 ? " Black Gem" : " Black Gems");
    }

    public Match match(String id, String serverItemType, List<String> lore) {
        Inspection result = this.inspect(id, serverItemType, lore);
        return new Match(result.unitPrice, result.ambiguous);
    }

    public static int loreLevel(List<String> lore, String name) {
        for (String raw : lore) {
            String line = raw == null ? "" : raw.trim();
            if (line.startsWith("-")) {
                line = line.substring(1).trim();
            }

            if (line.length() > name.length() && line.regionMatches(true, 0, name, 0, name.length())) {
                String suffix = line.substring(name.length()).trim();
                if (!suffix.isEmpty()) {
                    String token = suffix.split("\\s+", 2)[0].replaceAll("[^0-9IVXLCDMivxlcdm]", "");
                    if (!token.isEmpty()) {
                        try {
                            return token.chars().allMatch(Character::isDigit) ? Integer.parseInt(token) : roman(token.toUpperCase(Locale.ROOT));
                        } catch (Exception ignored) {
                            // Not a level number; keep looking.
                        }
                    }
                }
            }
        }

        return -1;
    }

    private static String carnageType(List<String> lore) {
        for (String raw : lore) {
            String line = raw == null ? "" : raw.trim();
            if (line.startsWith("-")) {
                line = line.substring(1).trim();
            }

            if (line.equalsIgnoreCase("Melee Carnage")) {
                return "Melee Carnage";
            }

            if (line.equalsIgnoreCase("Ranged Carnage")) {
                return "Ranged Carnage";
            }
        }

        return "";
    }

    private static int roman(String value) {
        int total = 0;
        int last = 0;

        for (int i = value.length() - 1; i >= 0; i--) {
            int n = switch (value.charAt(i)) {
                case 'I' -> 1;
                case 'V' -> 5;
                case 'X' -> 10;
                case 'L' -> 50;
                case 'C' -> 100;
                case 'D' -> 500;
                case 'M' -> 1000;
                default -> throw new IllegalArgumentException();
            };
            if (n < last) {
                total -= n;
            } else {
                total += n;
                last = n;
            }
        }

        if (total >= 1 && total <= 10000) {
            return total;
        } else {
            throw new IllegalArgumentException();
        }
    }

    private static BigDecimal levelPrice(LevelUpgrade upgrade, int level) {
        BigDecimal total = BigDecimal.ZERO;

        for (int current = upgrade.startLevel + 1; current <= level; current++) {
            LevelBand band = null;

            for (LevelBand candidate : upgrade.bands) {
                if (current >= candidate.fromLevel && current <= candidate.toLevel) {
                    band = candidate;
                    break;
                }
            }

            BigDecimal multiplier = band == null ? upgrade.levelMultiplier : band.levelMultiplier;
            BigDecimal flat = band == null ? upgrade.pricePerLevel : band.pricePerLevel;
            total = total.add(multiplier.multiply(BigDecimal.valueOf(current))).add(flat);
        }

        return total;
    }

    private static void levelMaterials(LevelUpgrade upgrade, int level, Map<String, Long> result) {
        for (int current = upgrade.startLevel + 1; current <= level; current++) {
            for (LevelBand band : upgrade.bands) {
                if (current >= band.fromLevel && current <= band.toLevel && !band.material.isBlank()) {
                    long count = Math.addExact(Math.multiplyExact(band.materialLevelMultiplier, current), band.materialPerLevel);
                    result.merge(band.material, count, Math::addExact);
                    break;
                }
            }
        }
    }

    public record AppliedUpgrade(String marker, BigDecimal addPrice, Cost cost) {
    }

    public static final class Cost {
        public long bloodstones;
        public long zircon;
        public long forgedTungsten;
        public long blackGems;

        public boolean empty() {
            return this.bloodstones == 0L && this.zircon == 0L && this.forgedTungsten == 0L && this.blackGems == 0L;
        }

        public BigDecimal inBlackGems() {
            return ArdaCurrency.BLACK_GEM.inBlackGems().multiply(BigDecimal.valueOf(this.blackGems))
                .add(ArdaCurrency.FORGED_TUNGSTEN.inBlackGems().multiply(BigDecimal.valueOf(this.forgedTungsten)))
                .add(ArdaCurrency.ZIRCON.inBlackGems().multiply(BigDecimal.valueOf(this.zircon)))
                .add(ArdaCurrency.BLOODSTONE.inBlackGems().multiply(BigDecimal.valueOf(this.bloodstones)));
        }

        public String display() {
            List<String> parts = new ArrayList<>();
            if (this.blackGems > 0L) {
                parts.add(ArdaCurrency.BLACK_GEM.amount(this.blackGems));
            }

            if (this.forgedTungsten > 0L) {
                parts.add(ArdaCurrency.FORGED_TUNGSTEN.amount(this.forgedTungsten));
            }

            if (this.zircon > 0L) {
                parts.add(ArdaCurrency.ZIRCON.amount(this.zircon));
            }

            if (this.bloodstones > 0L) {
                parts.add(ArdaCurrency.BLOODSTONE.amount(this.bloodstones));
            }

            return parts.isEmpty() ? ArdaCurrency.BLOODSTONE.amount(0L) : String.join(" + ", parts);
        }
    }

    public static final class Entry {
        public String itemId = "";
        public String serverItemType = "";
        public String name = "";
        public String itemStack = "";
        public List<String> loreContains = new ArrayList<>();
        public List<Requirement> requirements = new ArrayList<>();
        public int tradeAmount = 1;
        public BigDecimal unitPrice;
        public Cost cost;
        public boolean unique;
        public List<Upgrade> upgrades = new ArrayList<>();
        public List<LevelUpgrade> levelUpgrades = new ArrayList<>();
    }

    public record Inspection(
        Entry entry,
        BigDecimal basePrice,
        BigDecimal unitPrice,
        List<AppliedUpgrade> upgrades,
        Map<String, Long> materials,
        boolean ambiguous
    ) {
    }

    public static final class LevelBand {
        public int fromLevel;
        public int toLevel;
        public BigDecimal levelMultiplier = BigDecimal.ZERO;
        public BigDecimal pricePerLevel = BigDecimal.ZERO;
        public String material = "";
        public long materialLevelMultiplier;
        public long materialPerLevel;
    }

    public static final class LevelUpgrade {
        public String name = "";
        public int startLevel;
        public int maxLevel;
        public BigDecimal levelMultiplier = BigDecimal.ZERO;
        public BigDecimal pricePerLevel = BigDecimal.ZERO;
        public List<LevelBand> bands = new ArrayList<>();
    }

    public record Match(BigDecimal unitPrice, boolean ambiguous) {
    }

    public static final class Requirement {
        public String itemId = "";
        public String serverItemType = "";
        public String name = "";
        public String itemStack = "";
        public int count;

        public String display() {
            return this.count
                + " "
                + (
                    this.name != null && !this.name.isBlank()
                        ? this.name
                        : (this.serverItemType != null && !this.serverItemType.isBlank() ? this.serverItemType : this.itemId)
                );
        }
    }

    public static final class Upgrade {
        public String loreContains = "";
        public BigDecimal addPrice = BigDecimal.ZERO;
        public Cost cost;
    }
}
