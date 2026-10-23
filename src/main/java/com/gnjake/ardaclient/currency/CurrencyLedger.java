package com.gnjake.ardaclient.currency;

import com.gnjake.ardaclient.ArdaClient;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Per-player currency records, saved to {@code config/arda-client/currency.json}: currency earned, bucketed by minute
 * and kept for a week; the ender chest contents from the last time it was opened; and how much the player has put into
 * storage. All amounts are in Bloodstones.
 */
public final class CurrencyLedger {
    public static final long MINUTE = 60_000L;
    public static final long HOUR = 60 * MINUTE;
    public static final long[] WINDOWS = {HOUR, 3 * HOUR, 12 * HOUR, 24 * HOUR, 7 * 24 * HOUR};
    public static final String[] WINDOW_NAMES = {"Last Hour", "3 Hours", "12 Hours", "24 Hours", "Week"};
    private static final long KEEP = 7 * 24 * HOUR;
    private static final long SAVE_DELAY = 10_000L;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("arda-client").resolve("currency.json");

    private static Data data = new Data();
    private static long dirtySince;

    private CurrencyLedger() {
    }

    public static void load() {
        try {
            if (Files.exists(FILE)) {
                Data loaded = GSON.fromJson(Files.readString(FILE), Data.class);
                if (loaded != null && loaded.players != null) {
                    data = loaded;
                }
            }
        } catch (Exception e) {
            ArdaClient.LOG.warn("Could not read the currency tally; starting a new one. The old file was not changed.", e);
            data = new Data();
        }
    }

    private static Player player(UUID id) {
        return data.players.computeIfAbsent(id.toString(), key -> new Player());
    }

    public static void recordEarned(UUID id, long bloodstones, long now) {
        Player player = player(id);
        if (player.earned == null) {
            player.earned = new TreeMap<>();
        }

        player.earned.merge(now / MINUTE, bloodstones, Long::sum);
        player.earned.headMap((now - KEEP) / MINUTE).clear();
        markDirty(now);
    }

    /** Bloodstones earned within {@code window} milliseconds before {@code now}. */
    public static long earned(UUID id, long window, long now) {
        Player player = data.players.get(id.toString());
        if (player == null || player.earned == null) {
            return 0L;
        }

        long total = 0L;
        for (long amount : player.earned.tailMap((now - window) / MINUTE, false).values()) {
            total += amount;
        }

        return total;
    }

    public static void resetEarned(UUID id, long now) {
        player(id).earned = new TreeMap<>();
        markDirty(now);
        saveNow();
    }

    /** The ender chest's currency when it was last opened, or -1 if it hasn't been opened yet. */
    public static long enderChest(UUID id) {
        Player player = data.players.get(id.toString());
        return player == null ? -1L : player.enderChest;
    }

    public static void setEnderChest(UUID id, long bloodstones, long now) {
        Player player = player(id);
        if (player.enderChest != bloodstones) {
            player.enderChest = bloodstones;
            markDirty(now);
        }
    }

    /** Currency the player has put into chests and other storage, which shouldn't count as earned when it comes back. */
    public static long stored(UUID id) {
        Player player = data.players.get(id.toString());
        return player == null ? 0L : player.stored;
    }

    public static void addStored(UUID id, long bloodstones, long now) {
        if (bloodstones > 0L) {
            player(id).stored += bloodstones;
            markDirty(now);
        }
    }

    /** Takes up to {@code bloodstones} from storage, returning how much was there. */
    public static long takeStored(UUID id, long bloodstones, long now) {
        Player player = player(id);
        long taken = Math.min(player.stored, Math.max(0L, bloodstones));
        if (taken > 0L) {
            player.stored -= taken;
            markDirty(now);
        }

        return taken;
    }

    private static void markDirty(long now) {
        if (dirtySince == 0L) {
            dirtySince = now;
        }
    }

    /** Saves a few seconds after the first change, so a stream of pickups is one write. */
    public static void tick(long now) {
        if (dirtySince != 0L && now - dirtySince >= SAVE_DELAY) {
            saveNow();
        }
    }

    public static synchronized void saveNow() {
        dirtySince = 0L;

        try {
            Files.createDirectories(FILE.getParent());
            Path temp = FILE.resolveSibling("currency.json.tmp");
            Files.writeString(temp, GSON.toJson(data));

            try {
                Files.move(temp, FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, FILE, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            ArdaClient.LOG.warn("Could not save the currency tally.", e);
        }
    }

    private static final class Data {
        public int schema = 1;
        public Map<String, Player> players = new HashMap<>();
    }

    private static final class Player {
        public long enderChest = -1L;
        public long stored;
        /** Minute (milliseconds since the epoch / 60000) to Bloodstones earned in it. */
        public TreeMap<Long, Long> earned = new TreeMap<>();
    }
}
