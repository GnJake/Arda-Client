package com.gnjake.ardaclient.encyclopedia;

import com.gnjake.ardaclient.ArdaClient;
import com.gnjake.ardaclient.ArdaItems;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.math.RoundingMode;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.item.ItemStack;

/** Encyclopedia entries the player captured in game, saved to {@code config/arda-client/encyclopedia-drafts.json}. */
public final class CatalogDrafts {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("arda-client").resolve("encyclopedia-drafts.json");
    public static Data data = new Data();

    private CatalogDrafts() {
    }

    public static void load() {
        try {
            if (!Files.exists(FILE)) {
                for (String folder : List.of("arda-companion", "anor")) {
                    Path old = FabricLoader.getInstance().getConfigDir().resolve(folder).resolve("encyclopedia-drafts.json");
                    if (Files.exists(old)) {
                        Files.createDirectories(FILE.getParent());
                        Files.copy(old, FILE);
                        ArdaClient.LOG.info("Copied Encyclopedia drafts from config/{} into Arda Client.", folder);
                        break;
                    }
                }
            }

            if (Files.exists(FILE)) {
                Data loaded = GSON.fromJson(Files.readString(FILE), Data.class);
                if (loaded != null) {
                    data = loaded;
                }
            }

            validate();
            merge();
        } catch (Exception e) {
            ArdaClient.LOG.warn("Could not load Encyclopedia drafts; keeping the original file.", e);
            data = new Data();
        }
    }

    private static void validate() {
        if (data.entries == null) {
            data.entries = new ArrayList<>();
        }

        if (data.entries.size() > 2048) {
            throw new IllegalArgumentException("Too many Encyclopedia drafts");
        } else {
            data.entries.removeIf(dx -> dx == null || dx.entry == null || dx.entry.itemId == null || !dx.entry.itemId.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"));

            for (Draft d : data.entries) {
                if (d.id == null || d.id.isBlank()) {
                    d.id = UUID.randomUUID().toString();
                }

                if (d.capturedLore == null) {
                    d.capturedLore = new ArrayList<>();
                }

                if (d.entry.loreContains == null) {
                    d.entry.loreContains = new ArrayList<>();
                }

                if (d.entry.requirements == null) {
                    d.entry.requirements = new ArrayList<>();
                }

                if (d.entry.upgrades == null) {
                    d.entry.upgrades = new ArrayList<>();
                }

                if (d.entry.levelUpgrades == null) {
                    d.entry.levelUpgrades = new ArrayList<>();
                }

                KnownUpgradeRules.repair(d.entry);
                PriceBook check = new PriceBook();
                check.entries.add(d.entry);
                check.validate();
            }
        }
    }

    private static void merge() {
        for (Draft draft : data.entries) {
            removeCatalogMatch(draft.entry);
            PriceCatalog.book.entries.add(draft.entry);
        }
    }

    /** Saves a held item as a local entry, replacing an earlier draft of the same item. */
    public static void add(ItemStack stack, String name, List<String> identityLore, boolean unique, PriceBook.Cost cost) {
        String itemId = ArdaItems.id(stack);
        String serverType = ArdaItems.serverItemType(stack);
        Draft existing = data.entries.stream()
            .filter(d -> {
                String known = d.entry.serverItemType == null ? "" : d.entry.serverItemType;
                return !serverType.isBlank()
                    ? serverType.equals(known)
                    : known.isBlank() && d.entry.itemId.equals(itemId) && d.entry.loreContains.equals(identityLore);
            })
            .findFirst()
            .orElse(null);
        Draft draft = existing == null ? new Draft() : existing;
        draft.capturedAt = System.currentTimeMillis();
        draft.capturedName = stack.getHoverName().getString();
        draft.itemId = itemId;
        draft.serverItemType = serverType;
        draft.source = "manual";
        draft.capturedLore = new ArrayList<>(ArdaItems.lore(stack));
        draft.componentSummary = stack.getComponents().toString();
        draft.entry.itemId = itemId;
        draft.entry.serverItemType = serverType;
        draft.entry.itemStack = PriceCatalog.encodeStack(stack.copyWithCount(1));
        draft.entry.name = name;
        draft.entry.loreContains = new ArrayList<>(identityLore);
        draft.entry.requirements = new ArrayList<>();
        draft.entry.tradeAmount = 1;
        draft.entry.unique = unique;
        draft.entry.cost = unique ? null : cost;
        draft.entry.unitPrice = !unique && cost != null ? cost.inBlackGems().setScale(6, RoundingMode.HALF_UP) : null;
        KnownUpgradeRules.apply(draft.entry, stack);
        if (existing == null) {
            data.entries.add(draft);
            removeCatalogMatch(draft.entry);
            PriceCatalog.book.entries.add(draft.entry);
        }

        save();
    }

    private static void removeCatalogMatch(PriceBook.Entry local) {
        String type = local.serverItemType == null ? "" : local.serverItemType;
        PriceCatalog.book
            .entries
            .removeIf(
                entry -> entry != local
                    && (
                        !type.isBlank()
                            ? type.equals(entry.serverItemType)
                            : (entry.serverItemType == null || entry.serverItemType.isBlank())
                                && entry.itemId.equals(local.itemId)
                                && entry.loreContains.equals(local.loreContains)
                    )
            );
    }

    public static boolean isDraft(PriceBook.Entry entry) {
        return data.entries.stream().anyMatch(d -> d.entry == entry);
    }

    public static void addUpgrade(PriceBook.Entry entry, String marker, PriceBook.Cost cost) {
        PriceBook.Upgrade upgrade = new PriceBook.Upgrade();
        upgrade.loreContains = marker;
        upgrade.cost = cost;
        upgrade.addPrice = cost.inBlackGems();
        entry.upgrades.add(upgrade);
        save();
    }

    public static void remove(PriceBook.Entry entry) {
        data.entries.removeIf(d -> d.entry == entry);
        PriceCatalog.book.entries.remove(entry);
        save();
    }

    public static synchronized void save() {
        try {
            validate();
            Files.createDirectories(FILE.getParent());
            Path temp = FILE.resolveSibling("encyclopedia-drafts.json.tmp");
            Files.writeString(temp, GSON.toJson(data));

            try {
                Files.move(temp, FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temp, FILE, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            ArdaClient.LOG.warn("Could not save Encyclopedia drafts.", e);
        }
    }

    public static final class Data {
        public int schema = 1;
        public List<Draft> entries = new ArrayList<>();
    }

    public static final class Draft {
        public String id = UUID.randomUUID().toString();
        public String capturedName = "";
        public String itemId = "";
        public String serverItemType = "";
        public String componentSummary = "";
        public String source = "manual";
        public List<String> capturedLore = new ArrayList<>();
        public long capturedAt;
        public PriceBook.Entry entry = new PriceBook.Entry();
    }
}
