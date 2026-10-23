package com.gnjake.ardaclient;

import com.gnjake.ardaclient.config.ArdaStore;
import com.gnjake.ardaclient.dungeon.DungeonTracker;
import com.gnjake.ardaclient.encyclopedia.CatalogDrafts;
import com.gnjake.ardaclient.encyclopedia.EncyclopediaScreen;
import com.gnjake.ardaclient.encyclopedia.PriceCatalog;
import com.gnjake.ardaclient.encyclopedia.PriceTooltips;
import com.gnjake.ardaclient.hud.CurrencyHud;
import com.gnjake.ardaclient.hud.DungeonHud;
import com.gnjake.ardaclient.hud.HudPlacementScreen;
import com.gnjake.ardaclient.wheel.QuickWheel;
import com.mojang.blaze3d.platform.InputConstants.Type;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.KeyMapping.Category;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ArdaClient implements ClientModInitializer {
    public static final String MOD_ID = "arda-client";
    public static final Logger LOG = LoggerFactory.getLogger("Arda Client");
    public static KeyMapping wheelKey;
    public static KeyMapping pricingKey;
    public static KeyMapping dungeonHudKey;

    @Override
    public void onInitializeClient() {
        ArdaStore.load();
        PriceCatalog.load();
        CatalogDrafts.load();
        PriceTooltips.register();
        Category category = Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "controls"));
        wheelKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.arda-client.quick_wheel", Type.MOUSE, 2, category));
        pricingKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.arda-client.pricing", Type.KEYSYM, -1, category));
        dungeonHudKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.arda-client.dungeon_hud", Type.KEYSYM, -1, category));
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> DungeonTracker.onMessage(message));
        ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, receivedTime) -> DungeonTracker.onMessage(message));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            QuickWheel.tick(client);
            DungeonTracker.tick(System.currentTimeMillis());
            CurrencyHud.update(client);

            while (pricingKey.consumeClick()) {
                if (client.player != null && client.gui.screen() == null) {
                    client.gui.setScreen(new EncyclopediaScreen(null));
                }
            }

            while (dungeonHudKey.consumeClick()) {
                ArdaStore.data.dungeonHud = !ArdaStore.data.dungeonHud;
                ArdaStore.save();
            }
        });
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Identifier.fromNamespaceAndPath(MOD_ID, "dungeons"), (graphics, tick) -> {
            if (showHud() && DungeonHud.INSTANCE.enabled()) {
                DungeonHud.INSTANCE.render(graphics);
            }
        });
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Identifier.fromNamespaceAndPath(MOD_ID, "currency"), (graphics, tick) -> {
            if (showHud() && CurrencyHud.INSTANCE.enabled()) {
                CurrencyHud.INSTANCE.render(graphics);
            }
        });
        LOG.info("Arda Client ready. Hold Middle Mouse for the wheel.");
    }

    private static boolean showHud() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player != null && !minecraft.gui.hud.isHidden() && !(minecraft.gui.screen() instanceof HudPlacementScreen);
    }
}
