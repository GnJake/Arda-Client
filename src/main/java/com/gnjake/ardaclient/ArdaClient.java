package com.gnjake.ardaclient;

import com.gnjake.ardaclient.config.ArdaStore;
import com.gnjake.ardaclient.currency.CraftingPanel;
import com.gnjake.ardaclient.currency.CurrencyCrafter;
import com.gnjake.ardaclient.currency.CurrencyLedger;
import com.gnjake.ardaclient.currency.CurrencyTracker;
import com.gnjake.ardaclient.debug.PerfMonitor;
import com.gnjake.ardaclient.dungeon.DungeonTracker;
import com.gnjake.ardaclient.dungeon.WardTracker;
import com.gnjake.ardaclient.encyclopedia.CatalogDrafts;
import com.gnjake.ardaclient.encyclopedia.EncyclopediaScreen;
import com.gnjake.ardaclient.encyclopedia.PriceCatalog;
import com.gnjake.ardaclient.encyclopedia.PriceTooltips;
import com.gnjake.ardaclient.hud.CurrencyHud;
import com.gnjake.ardaclient.hud.DungeonHud;
import com.gnjake.ardaclient.hud.HudPanel;
import com.gnjake.ardaclient.hud.HudPlacementScreen;
import com.gnjake.ardaclient.hud.MithrilHud;
import com.gnjake.ardaclient.hud.TowersHud;
import com.gnjake.ardaclient.hud.WardHud;
import com.gnjake.ardaclient.structure.TowerEvent;
import com.gnjake.ardaclient.hud.MithrilTallyHud;
import com.gnjake.ardaclient.hud.TallyHud;
import com.gnjake.ardaclient.profit.ProfitEstimator;
import com.gnjake.ardaclient.structure.StructureLog;
import com.gnjake.ardaclient.structure.StructureLogger;
import com.gnjake.ardaclient.structure.TunnelPathfinder;
import com.gnjake.ardaclient.waypoint.SkirmishWaypoints;
import com.gnjake.ardaclient.xaero.XaeroWaypoints;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
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
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
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
        StructureLog.load();
        if (ArdaBuild.DEV) {
            ArdaBuild.registerDevFeatures();
        }
        PriceTooltips.register();
        Category category = Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "controls"));
        wheelKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.arda-client.quick_wheel", Type.MOUSE, 2, category));
        pricingKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.arda-client.pricing", Type.KEYSYM, -1, category));
        dungeonHudKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.arda-client.dungeon_hud", Type.KEYSYM, -1, category));
        ClientReceiveMessageEvents.MODIFY_GAME.register((message, overlay) -> PerfMonitor.time("profit annotate", () -> ProfitEstimator.annotate(message, overlay)));
        ClientReceiveMessageEvents.MODIFY_GAME.register((message, overlay) -> PerfMonitor.time("waypoint annotate", () -> SkirmishWaypoints.annotate(message, overlay)));
        if (SkirmishWaypoints.xaeroInstalled()) {
            // Only touched when Xaero's Minimap is installed, so its classes are never loaded without it.
            XaeroWaypoints.register();
        }

        ClientReceiveMessageEvents.GAME.register((message, overlay) -> PerfMonitor.time("chat messages", () -> {
            DungeonTracker.onMessage(message);
            if (!overlay) {
                ProfitEstimator.onMessage(message);
                StructureLogger.onMessage(message);
                TowerEvent.onMessage(message);
            }
        }));
        ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, receivedTime) -> DungeonTracker.onMessage(message));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            HudPanel.tick();
            PerfMonitor.time("towers tick", () -> TowerEvent.tick(client));
            PerfMonitor.time("wheel tick", () -> QuickWheel.tick(client));
            PerfMonitor.time("dungeon tick", () -> DungeonTracker.tick(System.currentTimeMillis()));
            PerfMonitor.time("currency tick", () -> CurrencyTracker.tick(client));
            PerfMonitor.time("crafting tick", () -> CurrencyCrafter.tick(client));
            PerfMonitor.time("profit tick", () -> ProfitEstimator.tick(client));
            PerfMonitor.time("ward tick", () -> WardTracker.tick(client));
            PerfMonitor.time("tunnel path tick", () -> TunnelPathfinder.tick(client));

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
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (screen instanceof CraftingScreen crafting) {
                CraftingPanel.attach(crafting, Screens.getWidgets(crafting));
            }
        });
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Identifier.fromNamespaceAndPath(MOD_ID, "perf"), (graphics, tick) -> PerfMonitor.frame());
        attachHud("dungeons", DungeonHud.INSTANCE);
        attachHud("currency", CurrencyHud.INSTANCE);
        attachHud("tally", TallyHud.INSTANCE);
        attachHud("mithril", MithrilHud.INSTANCE);
        attachHud("mithril_tally", MithrilTallyHud.INSTANCE);
        attachHud("towers", TowersHud.INSTANCE);
        attachHud("ward", WardHud.INSTANCE);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> CurrencyLedger.saveNow());
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> CurrencyLedger.saveNow());
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> dispatcher.register(
            ClientCommands.literal(MOD_ID)
                .then(ClientCommands.literal("item").executes(command -> logHeldItem()))
                .then(ClientCommands.literal("scoreboard").executes(command -> logScoreboard()))
                .then(ClientCommands.literal("perf")
                    .executes(command -> PerfMonitor.start(30))
                    .then(ClientCommands.argument("seconds", IntegerArgumentType.integer(5, 600))
                        .executes(command -> PerfMonitor.start(IntegerArgumentType.getInteger(command, "seconds")))))
                .then(waypointCommand(SkirmishWaypoints.Target.SKIRMISH))
                .then(waypointCommand(SkirmishWaypoints.Target.MIRAGE))
        ));
        LOG.info("Arda Client ready. Hold Middle Mouse for the wheel.");
    }

    private static void attachHud(String name, HudPanel panel) {
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Identifier.fromNamespaceAndPath(MOD_ID, name), (graphics, tick) -> {
            PerfMonitor.time("hud " + name, () -> {
                if (showHud() && panel.shown(System.currentTimeMillis())) {
                    panel.render(graphics);
                }
            });
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

    /** {@code /arda-client waypoint <skirmish|mirage> <number> <x> <z>}, which the chat waypoint links run. */
    private static LiteralArgumentBuilder<FabricClientCommandSource> waypointCommand(SkirmishWaypoints.Target target) {
        return ClientCommands.literal("waypoint").then(ClientCommands.literal(target.id)
            .then(ClientCommands.argument("number", IntegerArgumentType.integer(0))
                .then(ClientCommands.argument("x", IntegerArgumentType.integer())
                    .then(ClientCommands.argument("z", IntegerArgumentType.integer())
                        .executes(command -> SkirmishWaypoints.add(target, IntegerArgumentType.getInteger(command, "number"),
                            IntegerArgumentType.getInteger(command, "x"), IntegerArgumentType.getInteger(command, "z")))))));
    }

    /** {@code /arda-client scoreboard}: writes the sidebar and the party it shows to the log. */
    private static int logScoreboard() {
        Minecraft minecraft = Minecraft.getInstance();
        LOG.info("Sidebar: {}", ProfitEstimator.sidebar(minecraft));
        LOG.info("Party: {}", ProfitEstimator.partyNames(minecraft));
        if (minecraft.player != null) {
            minecraft.player.sendSystemMessage(Component.literal("Party of " + ProfitEstimator.partySize(minecraft) + ". Wrote the scoreboard to the log."));
        }

        return 1;
    }

    private static boolean showHud() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player != null && !minecraft.gui.hud.isHidden() && !(minecraft.gui.screen() instanceof HudPlacementScreen);
    }
}
