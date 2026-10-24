package com.gnjake.ardaclient.currency;

import com.gnjake.ardaclient.ArdaClient;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Per-player currency and Mithril records, saved to {@code config/arda-client/currency.json}: what was earned,
 * bucketed by minute and kept for a week; the ender chest contents from the last time it was opened; and how much the
 * player has put into each container. Currency amounts are in Bloodstones, Mithril in Crude Mithril Ore.
 */
public final class CurrencyLedger {
    public static final long MINUTE = 60_000L;
    public static final long HOUR = 60 * MINUTE;
    public static final long[] WINDOWS = {HOUR, 3 * HOUR, 12 * HOUR, 24 * HOUR, 7 * 24 * HOUR};
    public static final String[] WINDOW_NAMES = {"Last Hour", "3 Hours", "12 Hours", "24 Hours", "Week"};
    private static final long KEEP = 7 * 24 * HOUR;
    /** How many individual earnings the currency log keeps. */
    private static final int HISTORY = 200;
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

    /** One tally's records for a player, created if missing. */
    private static Book book(UUID id, Tally tally) {
        Player player = player(id);
        if (tally == Tally.CURRENCY) {
            return player;
        }

        if (player.mithril == null) {
            player.mithril = new Book();
        }

        return player.mithril;
    }

    /** One tally's records for a player, or null if there are none yet. */
    private static Book existing(UUID id, Tally tally) {
        Player player = data.players.get(id.toString());
        return player == null || tally == Tally.CURRENCY ? player : player.mithril;
    }

    public static void recordEarned(UUID id, Tally tally, long bloodstones, long now, String source) {
        Book player = book(id, tally);
        if (player.earned == null) {
            player.earned = new TreeMap<>();
        }

        if (player.history == null) {
            player.history = new ArrayList<>();
        }

        player.earned.merge(now / MINUTE, bloodstones, Long::sum);
        player.earned.headMap((now - KEEP) / MINUTE).clear();
        player.history.add(new Entry(now, bloodstones, source));
        player.history.removeIf(entry -> entry == null || now - entry.time > KEEP);
        if (player.history.size() > HISTORY) {
            player.history.subList(0, player.history.size() - HISTORY).clear();
        }

        markDirty(now);
    }

    /** Bloodstones earned from {@code from} (inclusive) to {@code to} (exclusive), to the minute. */
    public static long earnedBetween(UUID id, Tally tally, long from, long to) {
        Book player = existing(id, tally);
        if (player == null || player.earned == null) {
            return 0L;
        }

        long total = 0L;
        for (long amount : player.earned.subMap(from / MINUTE, to / MINUTE).values()) {
            total += amount;
        }

        return total;
    }

    /** The most recent earnings, oldest first. */
    public static List<Entry> history(UUID id, Tally tally) {
        Book player = existing(id, tally);
        return player == null || player.history == null ? List.of() : List.copyOf(player.history);
    }

    /** Bloodstones earned within {@code window} milliseconds before {@code now}. */
    public static long earned(UUID id, Tally tally, long window, long now) {
        Book player = existing(id, tally);
        if (player == null || player.earned == null) {
            return 0L;
        }

        long total = 0L;
        for (long amount : player.earned.tailMap((now - window) / MINUTE, false).values()) {
            total += amount;
        }

        return total;
    }

    public static void resetEarned(UUID id, Tally tally, long now) {
        Book book = book(id, tally);
        book.earned = new TreeMap<>();
        book.history = new ArrayList<>();
        markDirty(now);
        saveNow();
    }

    /** The ender chest's currency when it was last opened, or -1 if it hasn't been opened yet. */
    public static long enderChest(UUID id, Tally tally) {
        Book player = existing(id, tally);
        return player == null ? -1L : player.enderChest;
    }

    public static void setEnderChest(UUID id, Tally tally, long bloodstones, long now) {
        Book player = book(id, tally);
        if (player.enderChest != bloodstones) {
            player.enderChest = bloodstones;
            markDirty(now);
        }
    }

    /** Currency the player has put into one container, which shouldn't count as earned when it comes back out. */
    public static long stored(UUID id, Tally tally, String container) {
        Book player = existing(id, tally);
        return player == null || player.deposits == null ? 0L : player.deposits.getOrDefault(container, 0L);
    }

    public static void addStored(UUID id, Tally tally, String container, long bloodstones, long now) {
        if (container != null && bloodstones > 0L) {
            storedMap(id, tally).merge(container, bloodstones, Long::sum);
            markDirty(now);
        }
    }

    /** Takes up to {@code bloodstones} from what the player put into {@code container}, returning how much was there. */
    public static long takeStored(UUID id, Tally tally, String container, long bloodstones, long now) {
        if (container == null || bloodstones <= 0L) {
            return 0L;
        }

        Map<String, Long> stored = storedMap(id, tally);
        long taken = Math.min(stored.getOrDefault(container, 0L), bloodstones);
        if (taken > 0L) {
            stored.merge(container, -taken, Long::sum);
            stored.remove(container, 0L);
            markDirty(now);
        }

        return taken;
    }

    private static Map<String, Long> storedMap(UUID id, Tally tally) {
        Book player = book(id, tally);
        if (player.deposits == null) {
            player.deposits = new HashMap<>();
        }

        return player.deposits;
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

    /** The player's currency records, with their Mithril records alongside. */
    private static final class Player extends Book {
        public Book mithril = new Book();
    }

    /** One tally's records. Currency amounts are in Bloodstones, Mithril in Crude Mithril Ore. */
    private static class Book {
        public long enderChest = -1L;
        /** Container key to Bloodstones the player put in and hasn't taken back out. */
        public Map<String, Long> deposits = new HashMap<>();
        /** Minute (milliseconds since the epoch / 60000) to Bloodstones earned in it. */
        public TreeMap<Long, Long> earned = new TreeMap<>();
        public List<Entry> history = new ArrayList<>();
    }

    /** One earning: when, how many Bloodstones, and where it came from. */
    public static final class Entry {
        public long time;
        public long bloodstones;
        public String source;

        public Entry() {
        }

        Entry(long time, long bloodstones, String source) {
            this.time = time;
            this.bloodstones = bloodstones;
            this.source = source;
        }
    }
}
