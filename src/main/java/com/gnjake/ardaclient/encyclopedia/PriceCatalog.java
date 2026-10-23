package com.gnjake.ardaclient.encyclopedia;

import com.gnjake.ardaclient.ArdaClient;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** The bundled price list plus the player's local drafts, which {@link CatalogDrafts} merges in. */
public final class PriceCatalog {
    private static final Gson GSON = new GsonBuilder().create();
    private static final String RESOURCE = "/assets/arda-client/data/prices.json";
    private static final Map<String, ItemStack> DISPLAY_STACKS = new HashMap<>();

    public static PriceBook book = new PriceBook();

    private PriceCatalog() {
    }

    public static void load() {
        DISPLAY_STACKS.clear();

        try (InputStream stream = PriceCatalog.class.getResourceAsStream(RESOURCE)) {
            if (stream == null) {
                throw new FileNotFoundException(RESOURCE);
            }

            try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                PriceBook parsed = GSON.fromJson(reader, PriceBook.class);
                if (parsed == null) {
                    throw new IllegalArgumentException("Empty bundled catalog");
                }

                parsed.validate();
                parsed.currency = "Black Gems";
                book = parsed;
                ArdaClient.LOG.info("Loaded {} encyclopedia entries.", book.entries.size());
            }
        } catch (Exception e) {
            book = new PriceBook();
            ArdaClient.LOG.warn("The bundled encyclopedia could not be loaded.", e);
        }
    }

    /** Serializes an item, components included, so the encyclopedia can draw it exactly as captured. */
    public static String encodeStack(ItemStack stack) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) {
                return "";
            }

            RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, mc.level.registryAccess());
            return GSON.toJson(ItemStack.CODEC.encodeStart(ops, stack).getOrThrow());
        } catch (Exception e) {
            ArdaClient.LOG.warn("Could not capture an Encyclopedia item model.", e);
            return "";
        }
    }

    public static ItemStack displayStack(PriceBook.Entry entry) {
        String key = entry.itemStack != null && !entry.itemStack.isBlank() ? "stack:" + entry.itemStack : "id:" + entry.itemId;
        return DISPLAY_STACKS.computeIfAbsent(key, k -> decodeStack(entry));
    }

    private static ItemStack decodeStack(PriceBook.Entry entry) {
        try {
            if (entry.itemStack != null && !entry.itemStack.isBlank()) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.level != null) {
                    RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, mc.level.registryAccess());
                    return ItemStack.CODEC.parse(ops, JsonParser.parseString(entry.itemStack)).getOrThrow();
                }
            }

            Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(entry.itemId));
            return item == null ? ItemStack.EMPTY : new ItemStack(item);
        } catch (Exception ignored) {
            // Wildcard IDs like minecraft:*_spawn_egg have no single item to show.
            return ItemStack.EMPTY;
        }
    }
}
