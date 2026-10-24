package com.gnjake.ardaclient.waypoint;

import com.gnjake.ardaclient.ArdaClient;
import com.gnjake.ardaclient.config.ArdaStore;
import com.gnjake.ardaclient.gui.ArdaTheme;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;

/**
 * Adds a waypoint link to skirmish announcements and {@code /skirmish info}. Arda only gives the X and Z of a
 * skirmish, so the waypoint goes at the Y level chosen in Waypoint Settings. Clicking the link runs
 * {@code /arda-client waypoint}, which adds it with the player's chosen waypoint mod.
 */
public final class SkirmishWaypoints {
    public static final String OFF = "off";
    public static final int MIN_Y = -64;
    public static final int MAX_Y = 320;
    /** What a waypoint link adds: a skirmish, or a mirage encampment. Each goes in its own Xaero waypoint set. */
    public enum Target {
        SKIRMISH("skirmish", "Skirmish", "Skirmish Level ", "S"),
        MIRAGE("mirage", "Mirage", "Mirage Encampment", "M");

        public final String id;
        /** The Xaero waypoint set, which every waypoint's name also starts with. */
        public final String set;
        private final String prefix;
        private final String initial;

        Target(String id, String set, String prefix, String initial) {
            this.id = id;
            this.set = set;
            this.prefix = prefix;
            this.initial = initial;
        }

        /** "Skirmish Level 6", "Mirage Encampment 3", or "Mirage Encampment" when Arda gives no number. */
        public String name(int number) {
            if (this == SKIRMISH) {
                return this.prefix + number;
            }

            return number > 0 ? this.prefix + " " + number : this.prefix;
        }
    }
    public static final String COMMAND = "/arda-client waypoint ";
    /** Skirmishes, camps and tunnels are all in Harad, whichever dimension the player is in when they click. */
    public static final ResourceKey<Level> HARAD = ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath("arda", "haradwaith"));

    private static final Pattern ANNOUNCED = Pattern.compile("(?i)\\[Skirmish\\]:?\\s*Level\\s+(\\d+)\\s+(?:will attack|has attacked)\\s+near\\s+(-?\\d+)\\s*,\\s*(-?\\d+)");
    private static final Pattern MIRAGE = Pattern.compile("(?i)\\[Harad\\]\\s*A Mirage Encampment\\s*(?:\\(Mirage\\.(\\d+)\\))?\\s*has appeared at\\s*(-?\\d+)\\s*,\\s*(-?\\d+)");
    private static final Pattern INFO = Pattern.compile("(?i)\\[Skirmish\\]:?\\s*Level\\s+(\\d+)\\s+(?:Spawn location|spawns at):?\\s*x:\\s*(-?\\d+)\\s*,\\s*z:\\s*(-?\\d+)");
    /** Xaero's Minimap and Better PVP, in their normal and Fair-Play editions. They share one waypoint system. */
    private static final String[] XAERO_MODS = {"xaerominimap", "xaerominimapfair", "xaerobetterpvp", "xaerobetterpvpfair"};
    private static final List<WaypointProvider> providers = new ArrayList<>();

    private SkirmishWaypoints() {
    }

    public static boolean xaeroInstalled() {
        for (String id : XAERO_MODS) {
            if (FabricLoader.getInstance().isModLoaded(id)) {
                return true;
            }
        }

        return false;
    }

    public static void register(WaypointProvider provider) {
        providers.add(provider);
        ArdaClient.LOG.info("{} waypoints available", provider.name());
    }

    public static List<WaypointProvider> providers() {
        return List.copyOf(providers);
    }

    /** The provider waypoints go to: the chosen one, or the first installed one if that mod is missing. Null when off. */
    public static WaypointProvider provider() {
        String chosen = ArdaStore.data.waypointType;
        if (OFF.equals(chosen) || providers.isEmpty()) {
            return null;
        }

        return providers.stream().filter(provider -> provider.id().equals(chosen)).findFirst().orElse(providers.getFirst());
    }

    /** Appends a waypoint link to a skirmish or mirage encampment message that gives a location. */
    public static Component annotate(Component message, boolean overlay) {
        WaypointProvider provider = overlay || message == null ? null : provider();
        if (provider == null) {
            return message;
        }

        String text = message.getString().replaceAll("(?i)§[0-9a-fk-or]", "");
        Target target = Target.SKIRMISH;
        Matcher match = ANNOUNCED.matcher(text);
        if (!match.find()) {
            match = INFO.matcher(text);
            if (!match.find()) {
                target = Target.MIRAGE;
                match = MIRAGE.matcher(text);
                if (!match.find()) {
                    return message;
                }
            }
        }

        int number = match.group(1) == null ? 0 : Integer.parseInt(match.group(1));
        int x = Integer.parseInt(match.group(2));
        int z = Integer.parseInt(match.group(3));
        int y = ArdaStore.data.waypointY;
        Target kind = target;
        Component hover = Component.literal("Add a " + provider.name() + " waypoint for " + kind.name(number) + " at " + x + ", " + y + ", " + z
            + ".\nThe height is a guess; set it in Waypoint Settings.");
        Component link = ArdaTheme.accentComponent("[" + provider.name() + " Waypoint]").withStyle(style -> style
            .withUnderlined(true)
            .withClickEvent(new ClickEvent.RunCommand(COMMAND + kind.id + " " + number + " " + x + " " + z))
            .withHoverEvent(new HoverEvent.ShowText(hover)));
        return message.copy().append(Component.literal(" ")).append(link);
    }

    /** Runs when the player clicks a waypoint link. */
    public static int add(Target target, int number, int x, int z) {
        WaypointProvider provider = provider();
        String result;
        if (provider == null) {
            result = providers.isEmpty() ? "Install Xaero's Minimap or Meteor Client to add waypoints." : "Waypoints are off in Waypoint Settings.";
        } else {
            try {
                result = provider.add(target.set, target.name(number), target.initial + (number > 0 ? number : ""), x, ArdaStore.data.waypointY, z);
            } catch (RuntimeException | LinkageError e) {
                ArdaClient.LOG.warn("Could not add a {} waypoint", provider.name(), e);
                result = "Could not add the waypoint; " + provider.name() + " may have changed. See the log.";
            }
        }

        tell(result);
        return 1;
    }

    public static int removeAll() {
        int removed = 0;
        for (WaypointProvider provider : providers) {
            try {
                removed += provider.removeSkirmish();
            } catch (RuntimeException | LinkageError e) {
                ArdaClient.LOG.warn("Could not remove {} waypoints", provider.name(), e);
            }
        }

        return removed;
    }

    private static void tell(String text) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.sendSystemMessage(ArdaTheme.accentComponent("[Waypoint]").append(" ")
                .append(Component.literal(text).withColor(ArdaTheme.TEXT & 0xFFFFFF)));
        }
    }
}
