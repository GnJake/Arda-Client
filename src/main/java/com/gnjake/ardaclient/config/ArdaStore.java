package com.gnjake.ardaclient.config;

import com.gnjake.ardaclient.ArdaClient;
import com.gnjake.ardaclient.dungeon.DungeonTracker;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.fabricmc.loader.api.FabricLoader;

/** Arda Client's settings, saved to {@code config/arda-client.json}. */
public final class ArdaStore {
    public static final double MIN_SCALE = 0.25;
    public static final double MAX_SCALE = 2.0;
    public static final double DEFAULT_SCALE = 0.75;
    public static final int DEFAULT_LEFT = 2;
    public static final int DEFAULT_TOP = 12;
    public static final int DEFAULT_ACCENT = 0xC62828;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG = FabricLoader.getInstance().getConfigDir();
    private static final Path FILE = CONFIG.resolve("arda-client.json");
    /** Settings from the standalone Arda Companion mod, copied over on first launch. */
    private static final Path LEGACY_FILE = CONFIG.resolve("arda-companion.json");
    /** Quick wheel commands from Anor Client, which Arda Companion also imported. */
    private static final Path ANOR_WHEEL = CONFIG.resolve("anor").resolve("quickwheel.json");
    private static final List<String> DUNGEON_STATES = List.of("UNKNOWN", "OPEN", "ACTIVE", "COOLDOWN");

    public static Data data = new Data();

    private ArdaStore() {
    }

    public static void load() {
        try {
            Path source = Files.exists(FILE) ? FILE : (Files.exists(LEGACY_FILE) ? LEGACY_FILE : null);
            if (source != null) {
                Data loaded = GSON.fromJson(Files.readString(source), Data.class);
                if (loaded != null) {
                    data = loaded;
                }

                if (source == LEGACY_FILE) {
                    save();
                    ArdaClient.LOG.info("Copied Arda Companion settings into Arda Client.");
                }
            } else {
                importAnorData();
                save();
            }

            validate();
        } catch (Exception e) {
            ArdaClient.LOG.warn("Could not read Arda Client settings. The existing file was not changed.", e);
            data = new Data();
        }
    }

    private static void importAnorData() {
        if (!Files.exists(ANOR_WHEEL)) {
            return;
        }

        try {
            JsonObject root = JsonParser.parseString(Files.readString(ANOR_WHEEL)).getAsJsonObject();
            JsonArray commands = root.getAsJsonArray("commands");
            if (commands != null) {
                for (JsonElement item : commands) {
                    QuickCommand command = GSON.fromJson(item, QuickCommand.class);
                    if (command != null) {
                        data.commands.add(command);
                    }
                }
            }

            JsonObject settings = root.getAsJsonObject("settings");
            if (settings != null) {
                data.settings.chroniclesCommand = text(settings, "chroniclesCommand", "/chronicles");
                data.settings.skirmishCommand = text(settings, "skirmishCommand", "/skirmish info");
                data.settings.scale = number(settings, "scale", 0.9);
                data.settings.opacity = number(settings, "opacity", 0.88);
                data.settings.labels = flag(settings, "labels", true);
                data.settings.icons = flag(settings, "icons", true);
            }

            ArdaClient.LOG.info("Imported existing Anor commands into Arda Client.");
        } catch (Exception e) {
            ArdaClient.LOG.warn("Could not import some old Anor settings; the original files remain untouched.", e);
        }
    }

    private static String text(JsonObject object, String key, String fallback) {
        try {
            return object.has(key) ? object.get(key).getAsString() : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static double number(JsonObject object, String key, double fallback) {
        try {
            return object.has(key) ? object.get(key).getAsDouble() : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static boolean flag(JsonObject object, String key, boolean fallback) {
        try {
            return object.has(key) ? object.get(key).getAsBoolean() : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    public static synchronized void save() {
        validate();

        try {
            Path temp = FILE.resolveSibling("arda-client.json.tmp");
            Files.createDirectories(FILE.getParent());
            Files.writeString(temp, GSON.toJson(data));

            try {
                Files.move(temp, FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, FILE, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            ArdaClient.LOG.warn("Could not save Arda Client settings.", e);
        }
    }

    private static void validate() {
        if (data == null) {
            data = new Data();
        }

        if (data.settings == null) {
            data.settings = new Settings();
        }

        if (data.commands == null) {
            data.commands = new ArrayList<>();
        }

        if (data.dungeons == null) {
            data.dungeons = new ArrayList<>();
        }

        if (data.shownDungeons == null || data.shownDungeons.length != DungeonTracker.TIERS) {
            boolean[] shown = allShown();
            if (data.shownDungeons != null) {
                System.arraycopy(data.shownDungeons, 0, shown, 0, Math.min(DungeonTracker.TIERS, data.shownDungeons.length));
            }

            data.shownDungeons = shown;
        }

        data.dungeons.removeIf(Objects::isNull);

        for (int tier = 1; tier <= DungeonTracker.TIERS; tier++) {
            int number = tier;
            DungeonTier entry = data.dungeons.stream().filter(value -> value.tier == number).findFirst().orElse(null);
            if (entry == null) {
                entry = new DungeonTier(tier);
                data.dungeons.add(entry);
            }

            if (entry.state == null || !DUNGEON_STATES.contains(entry.state)) {
                entry.state = "UNKNOWN";
            }

            if (entry.readyAt < 0L) {
                entry.readyAt = 0L;
            }
        }

        data.dungeons.removeIf(entry -> entry.tier < 1 || entry.tier > DungeonTracker.TIERS);
        data.dungeons.sort(Comparator.comparingInt(entry -> entry.tier));
        data.settings.scale = Math.max(0.65, Math.min(1.35, data.settings.scale));
        data.settings.opacity = Math.max(0.35, Math.min(1.0, data.settings.opacity));
        if (data.settings.chroniclesCommand == null) {
            data.settings.chroniclesCommand = "/chronicles";
        }

        if (data.settings.skirmishCommand == null) {
            data.settings.skirmishCommand = "/skirmish info";
        }

        data.commands.removeIf(command -> command == null || command.command == null || command.command.isBlank());
        data.commands.sort(Comparator.comparingInt(command -> command.order));

        for (int index = 0; index < data.commands.size(); index++) {
            QuickCommand command = data.commands.get(index);
            if (command.name == null || command.name.isBlank()) {
                command.name = "Shortcut";
            }

            if (command.icon == null) {
                command.icon = "";
            }

            if (command.id == null) {
                command.id = UUID.randomUUID().toString();
            }

            command.order = index;
        }

        data.dungeonScale = scale(data.dungeonScale);
        data.currencyScale = scale(data.currencyScale);
        data.dungeonLeft = Math.max(0, data.dungeonLeft);
        data.dungeonTop = Math.max(0, data.dungeonTop);
        data.currencyLeft = Math.max(0, data.currencyLeft);
        data.currencyTop = Math.max(0, data.currencyTop);
        data.tallyLeft = Math.max(0, data.tallyLeft);
        data.tallyTop = Math.max(0, data.tallyTop);
        data.accentColor &= 0xFFFFFF;
        data.hudOpacity = Double.isFinite(data.hudOpacity) ? Math.max(0.0, Math.min(1.0, data.hudOpacity)) : 1.0;
    }

    /** Clamps a HUD scale to the allowed range in 5% steps. */
    public static double scale(double value) {
        return Double.isFinite(value) ? Math.max(MIN_SCALE, Math.min(MAX_SCALE, Math.round(value * 20.0) / 20.0)) : DEFAULT_SCALE;
    }

    private static boolean[] allShown() {
        boolean[] shown = new boolean[DungeonTracker.TIERS];
        Arrays.fill(shown, true);
        return shown;
    }

    public static final class Data {
        public int schema = 1;
        public Settings settings = new Settings();
        public List<QuickCommand> commands = new ArrayList<>();
        public boolean pricingTooltips = true;
        public boolean dungeonHud = true;
        public double dungeonScale = DEFAULT_SCALE;
        public int dungeonLeft = DEFAULT_LEFT;
        public int dungeonTop = DEFAULT_TOP;
        public boolean currencyHud = true;
        public double currencyScale = DEFAULT_SCALE;
        public boolean currencyDocked = true;
        public int currencyLeft = DEFAULT_LEFT;
        public int currencyTop = DEFAULT_TOP;
        /** Adds the last-seen ender chest contents to the currency counter. */
        public boolean currencyEnderChest = false;
        public boolean tallyHud = true;
        public boolean tallyDocked = true;
        public int tallyLeft = DEFAULT_LEFT;
        public int tallyTop = DEFAULT_TOP;
        public int accentColor = DEFAULT_ACCENT;
        /** Background opacity of the HUD panels, from 0 (text only) to 1. */
        public double hudOpacity = 1.0;
        public long dungeonInfoAt;
        public List<DungeonTier> dungeons = new ArrayList<>();
        public boolean[] shownDungeons = allShown();

        public Data() {
            for (int tier = 1; tier <= DungeonTracker.TIERS; tier++) {
                dungeons.add(new DungeonTier(tier));
            }
        }
    }

    public static final class DungeonTier {
        public int tier;
        public String state = "UNKNOWN";
        public long readyAt;
        public long updatedAt;
        public long announcedAt;

        public DungeonTier() {
        }

        DungeonTier(int tier) {
            this.tier = tier;
        }
    }

    public static final class QuickCommand {
        public String id = UUID.randomUUID().toString();
        public String name = "Shortcut";
        public String command = "/home";
        public String icon = "";
        public int order;
    }

    public static final class Settings {
        public String chroniclesCommand = "/chronicles";
        public String skirmishCommand = "/skirmish info";
        public double scale = 0.9;
        public double opacity = 0.88;
        public boolean labels = true;
        public boolean icons = true;
    }
}
