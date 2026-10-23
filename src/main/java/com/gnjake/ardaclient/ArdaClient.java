package com.gnjake.ardaclient;

import com.gnjake.ardaclient.config.ArdaStore;
import com.gnjake.ardaclient.currency.CurrencyLedger;
import com.gnjake.ardaclient.currency.CurrencyTracker;
import com.gnjake.ardaclient.dungeon.DungeonTracker;
import com.gnjake.ardaclient.encyclopedia.CatalogDrafts;
import com.gnjake.ardaclient.encyclopedia.EncyclopediaScreen;
import com.gnjake.ardaclient.encyclopedia.PriceCatalog;
import com.gnjake.ardaclient.encyclopedia.PriceTooltips;
import com.gnjake.ardaclient.hud.CurrencyHud;
import com.gnjake.ardaclient.hud.DungeonHud;
import com.gnjake.ardaclient.hud.HudPanel;
import com.gnjake.ardaclient.hud.HudPlacementScreen;
import com.gnjake.ardaclient.hud.TallyHud;
import com.gnjake.ardaclient.wheel.QuickWheel;
import com.mojang.blaze3d.platform.InputConstants.Type;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.KeyMapping.Category;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
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
        CurrencyLedger.load();
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
            CurrencyTracker.tick(client);

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
        attachHud("dungeons", DungeonHud.INSTANCE);
        attachHud("currency", CurrencyHud.INSTANCE);
        attachHud("tally", TallyHud.INSTANCE);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> CurrencyLedger.saveNow());
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> CurrencyLedger.saveNow());
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> dispatcher.register(
            ClientCommands.literal(MOD_ID).then(ClientCommands.literal("item").executes(command -> logHeldItem()))
        ));
        LOG.info("Arda Client ready. Hold Middle Mouse for the wheel.");
    }

    private static void attachHud(String name, HudPanel panel) {
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Identifier.fromNamespaceAndPath(MOD_ID, name), (graphics, tick) -> {
            if (showHud() && panel.shown(System.currentTimeMillis())) {
                panel.render(graphics);
            }
        });
    }

    /** {@code /arda-client item}: writes the held item's full data to the log, for checking what the server sends. */
    private static int logHeldItem() {
        Minecraft minecraft = Minecraft.getInstance();
        ItemStack stack = minecraft.player == null ? ItemStack.EMPTY : minecraft.player.getMainHandItem();
        if (stack.isEmpty()) {
            if (minecraft.player != null) {
                minecraft.player.sendSystemMessage(Component.literal("Hold an item first."));
            }

            return 0;
        }

        LOG.info("Held item: {}", PriceCatalog.encodeStack(stack));
        minecraft.player.sendSystemMessage(Component.literal("Wrote " + stack.getHoverName().getString() + " to the log."));
        return 1;
    }

    private static boolean showHud() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player != null && !minecraft.gui.hud.isHidden() && !(minecraft.gui.screen() instanceof HudPlacementScreen);
    }
}
