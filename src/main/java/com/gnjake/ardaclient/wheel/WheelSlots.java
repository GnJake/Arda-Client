package com.gnjake.ardaclient.wheel;

import com.gnjake.ardaclient.ServerCommands;
import com.gnjake.ardaclient.config.ArdaStore;
import com.gnjake.ardaclient.currency.CurrencyLogScreen;
import com.gnjake.ardaclient.dungeon.DungeonTracker;
import com.gnjake.ardaclient.encyclopedia.EncyclopediaScreen;
import com.gnjake.ardaclient.hud.HudSettingsScreen;
import com.gnjake.ardaclient.profit.ProfitScreen;
import com.gnjake.ardaclient.structure.StructureScreen;
import com.gnjake.ardaclient.structure.TowerEvent;
import com.gnjake.ardaclient.waypoint.WaypointSettingsScreen;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * What the quick wheel's slots can hold. Each slot is saved as a string: a built-in action's id, {@code command:<id>}
 * for one of the player's command shortcuts, or {@link #NONE} for an empty slot.
 */
public final class WheelSlots {
    public static final int MIN_SLOTS = 4;
    public static final int MAX_SLOTS = 16;
    public static final String NONE = "none";
    private static final String COMMAND = "command:";

    private static final List<Builtin> BUILTINS = List.of(
        new Builtin("chronicles", "CHRONICLES", "Open Arda Chronicles", Items.WRITABLE_BOOK, () -> send(ArdaStore.data.settings.chroniclesCommand)),
        new Builtin("hud", "HUD", "Panels, color and transparency", Items.SPYGLASS, () -> open(new HudSettingsScreen(null))),
        new Builtin("skirmish", "SKIRMISH INFO", "Run /skirmish info", Items.CLOCK, () -> send(ArdaStore.data.settings.skirmishCommand)),
        new Builtin("dungeon", "DUNGEON INFO", "Run /dungeon_info", Items.IRON_SWORD, () -> {
            open(null);
            DungeonTracker.requestInfo();
        }),
        new Builtin("profit", "PROFIT", "Dungeon and skirmish estimates", Items.GOLD_INGOT, () -> open(new ProfitScreen(null))),
        new Builtin("events", "EVENTS", "Run /events schedule", Items.PAPER, () -> send("/events schedule")),
        new Builtin("home", "HOME", "Run /home", Items.BED.red(), () -> send("/home")),
        new Builtin("back", "BACK", "Run /back", Items.ENDER_PEARL, () -> send("/back")),
        new Builtin("harad", "HARAD", "Run /harad", Items.SANDSTONE, () -> send("/harad")),
        new Builtin("dungeon_warp", "DUNGEON", "Run /dungeon", Items.MOSSY_COBBLESTONE, () -> send("/dungeon")),
        new Builtin("towers", "TOWERS", "Run /towersinfo holders", Items.IRON_BLOCK, () -> {
            open(null);
            TowerEvent.requestHolders();
        }),
        new Builtin("texturepack", "TEXTURE PACK", "Run /texturepack", Items.PAINTING, () -> send("/texturepack")),
        new Builtin("commands", "COMMANDS", "Your command shortcuts", Items.COMMAND_BLOCK, () -> open(new QuickCommandsScreen(null))),
        new Builtin("currency_log", "CURRENCY LOG", "Earnings by hour and source", Items.WRITTEN_BOOK, () -> open(new CurrencyLogScreen(null))),
        new Builtin("encyclopedia", "ENCYCLOPEDIA", "Item prices", Items.BOOK, () -> open(new EncyclopediaScreen(null))),
        new Builtin("structures", "STRUCTURES", "Camps, outposts, tunnels and towers", Items.FILLED_MAP, () -> open(new StructureScreen(null))),
        new Builtin("waypoints", "WAYPOINTS", "Skirmish waypoint settings", Items.LODESTONE, () -> open(new WaypointSettingsScreen(null))),
        new Builtin("wheel", "EDIT WHEEL", "Choose what the wheel holds", Items.COMPASS, () -> open(new WheelEditScreen(null)))
    );

    /** Every slot of a full wheel, clockwise from the top. */
    public static final List<String> DEFAULTS = List.of("wheel", "commands", "encyclopedia", "structures", "currency_log", "texturepack",
        "profit", "events", "dungeon", "skirmish", "towers", "harad", "home", "back", "waypoints", "hud");
    /** The teleports leave a shrinking wheel first, then the other slots from the end. */
    private static final List<String> TELEPORTS = List.of("dungeon_warp", "back", "home", "harad");

    /** The order slots leave a shrinking wheel: empty slots, teleports, then defaults from the end. */
    private static int removalRank(String id) {
        if (id.equals(NONE)) {
            return -1;
        }

        if (TELEPORTS.contains(id)) {
            return TELEPORTS.indexOf(id);
        }

        int index = DEFAULTS.indexOf(id);
        // Slots that aren't defaults, such as the player's own commands, are kept longest.
        return index < 0 ? Integer.MAX_VALUE : TELEPORTS.size() + DEFAULTS.size() - index;
    }

    /** Removes the slot that should go first. */
    public static void shrink(List<String> wheel) {
        int remove = wheel.size() - 1;
        for (int index = wheel.size() - 1; index >= 0; index--) {
            if (removalRank(wheel.get(index)) < removalRank(wheel.get(remove))) {
                remove = index;
            }
        }

        wheel.remove(remove);
    }

    /**
     * Adds back the missing default that would have left last, in its default place, so shrinking the wheel and
     * growing it again brings the same wheel back.
     */
    public static void grow(List<String> wheel) {
        String next = null;
        for (String id : DEFAULTS) {
            if (!wheel.contains(id) && (next == null || removalRank(id) > removalRank(next))) {
                next = id;
            }
        }

        if (next == null) {
            wheel.add(NONE);
            return;
        }

        // After the last slot that comes before it in the default order.
        int place = DEFAULTS.indexOf(next);
        int at = 0;
        for (int index = 0; index < wheel.size(); index++) {
            int other = DEFAULTS.indexOf(wheel.get(index));
            if (other >= 0 && other < place) {
                at = index + 1;
            }
        }

        wheel.add(at, next);
    }

    private WheelSlots() {
    }

    private static void send(String command) {
        open(null);
        ServerCommands.run(command);
    }

    private static void open(Screen screen) {
        Minecraft.getInstance().gui.setScreen(screen);
    }

    /** Every choice for a slot, in the order the wheel editor cycles through them. */
    public static List<String> options() {
        List<String> options = new ArrayList<>();
        BUILTINS.forEach(builtin -> options.add(builtin.id));
        ArdaStore.data.commands.forEach(command -> options.add(COMMAND + command.id));
        options.add(NONE);
        return options;
    }

    /** The section a saved slot shows on the wheel, or null for an empty slot or a deleted command. */
    public static WheelScreen.Section section(String slot) {
        for (Builtin builtin : BUILTINS) {
            if (builtin.id.equals(slot)) {
                return new WheelScreen.Section(builtin.name, builtin.subtitle, builtin.icon, builtin.action);
            }
        }

        ArdaStore.QuickCommand command = command(slot);
        if (command == null) {
            return null;
        }

        Item icon = icon(command.icon);
        return new WheelScreen.Section(command.name.toUpperCase(Locale.ROOT), command.command, icon == null ? Items.COMMAND_BLOCK : icon,
            () -> send(command.command));
    }

    /** The name the wheel editor shows for a slot. */
    public static String label(String slot) {
        for (Builtin builtin : BUILTINS) {
            if (builtin.id.equals(slot)) {
                return titleCase(builtin.name);
            }
        }

        ArdaStore.QuickCommand command = command(slot);
        return command == null ? "Empty" : "Command: " + command.name;
    }

    private static ArdaStore.QuickCommand command(String slot) {
        if (slot == null || !slot.startsWith(COMMAND)) {
            return null;
        }

        String id = slot.substring(COMMAND.length());
        return ArdaStore.data.commands.stream().filter(command -> id.equals(command.id)).findFirst().orElse(null);
    }

    public static Item icon(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }

        try {
            Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(id));
            return item == Items.AIR ? null : item;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String titleCase(String upper) {
        StringBuilder out = new StringBuilder();
        for (String word : upper.toLowerCase(Locale.ROOT).split(" ")) {
            if (!out.isEmpty()) {
                out.append(' ');
            }

            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }

        return out.toString();
    }

    private record Builtin(String id, String name, String subtitle, Item icon, Runnable action) {
    }
}
