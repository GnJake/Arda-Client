package com.gnjake.ardaclient.structure;

import com.gnjake.ardaclient.ArdaBuild;
import com.gnjake.ardaclient.ArdaClient;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;

/**
 * Camps, outposts, tunnel spawners and towers in Harad. The mod ships a list ({@code arda-client/default_structures.json});
 * the player's copy, with which tunnels are completed, is saved to {@code config/arda-client/structures.json}. Tunnel
 * spawners also remember whether they have been completed since Harad last refreshed.
 */
public final class StructureLog {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String DEFAULTS = "/arda-client/default_structures.json";
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("arda-client").resolve("structures.json");

    private static Data data = new Data();

    private StructureLog() {
    }

    /**
     * Release builds use the bundled list and keep only which tunnels are completed from the saved file. Developer
     * builds record their own list, starting from the bundled one.
     */
    public static void load() {
        Data saved = null;
        try {
            if (Files.exists(FILE)) {
                saved = clean(GSON.fromJson(Files.readString(FILE), Data.class));
            }
        } catch (Exception e) {
            ArdaClient.LOG.warn("Could not read the structure log; starting a new one. The old file was not changed.", e);
        }

        Data bundled = bundled();
        if (ArdaBuild.DEV) {
            data = saved != null ? saved : bundled;
            return;
        }

        data = bundled;
        if (saved != null) {
            for (Structure entry : data.structures) {
                for (Structure old : saved.structures) {
                    if (old.kind.equals(entry.kind) && old.x == entry.x && old.y == entry.y && old.z == entry.z) {
                        entry.completedAt = old.completedAt;
                    }
                }
            }
        }
    }

    /** The structures shipped with the mod. */
    private static Data bundled() {
        try (InputStream in = StructureLog.class.getResourceAsStream(DEFAULTS)) {
            if (in != null) {
                Data loaded = clean(GSON.fromJson(new String(in.readAllBytes(), StandardCharsets.UTF_8), Data.class));
                if (loaded != null) {
                    return loaded;
                }
            }
        } catch (Exception e) {
            ArdaClient.LOG.warn("Could not read the bundled structure list.", e);
        }

        return new Data();
    }

    private static Data clean(Data loaded) {
        if (loaded == null || loaded.structures == null) {
            return null;
        }

        loaded.structures.removeIf(entry -> entry == null || Kind.of(entry.kind) == null);
        // Tunnel tiers change with every refresh; older logs stored them.
        loaded.structures.stream().filter(entry -> Kind.of(entry.kind) == Kind.TUNNEL).forEach(entry -> entry.number = 0);
        return loaded;
    }

    public static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Path temp = FILE.resolveSibling("structures.json.tmp");
            Files.writeString(temp, GSON.toJson(data));
            try {
                Files.move(temp, FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, FILE, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            ArdaClient.LOG.warn("Could not save the structure log.", e);
        }
    }

    /** One kind's structures, sorted by number and then position. */
    public static List<Structure> of(Kind kind) {
        List<Structure> list = new ArrayList<>();
        for (Structure entry : data.structures) {
            if (Kind.of(entry.kind) == kind) {
                list.add(entry);
            }
        }

        list.sort(Comparator.comparingInt((Structure entry) -> entry.number).thenComparingInt(entry -> entry.x).thenComparingInt(entry -> entry.z));
        return list;
    }

    public static Structure find(Kind kind, int number) {
        for (Structure entry : data.structures) {
            if (Kind.of(entry.kind) == kind && entry.number == number) {
                return entry;
            }
        }

        return null;
    }

    /** The structure of {@code kind} closest to {@code pos} within {@code range} blocks, or null. */
    public static Structure nearest(Kind kind, BlockPos pos, double range) {
        Structure best = null;
        double bestDistance = range * range;
        for (Structure entry : data.structures) {
            double distance = entry.pos().distSqr(pos);
            if (Kind.of(entry.kind) == kind && distance <= bestDistance) {
                best = entry;
                bestDistance = distance;
            }
        }

        return best;
    }

    public static Structure add(Kind kind, int number, BlockPos pos) {
        Structure entry = new Structure();
        entry.kind = kind.id;
        entry.number = number;
        entry.x = pos.getX();
        entry.y = pos.getY();
        entry.z = pos.getZ();
        entry.foundAt = System.currentTimeMillis();
        data.structures.add(entry);
        save();
        return entry;
    }

    /** Logs a tower at a lodestone, replacing any tower already logged there or under the same number. */
    public static Structure setTower(int number, BlockPos pos) {
        data.structures.removeIf(entry -> Kind.of(entry.kind) == Kind.TOWER && (entry.number == number || entry.pos().closerThan(pos, 2.0)));
        return add(Kind.TOWER, number, pos);
    }

    public static void remove(Structure entry) {
        data.structures.remove(entry);
        save();
    }

    /** Harad refreshed: every tunnel can be run again. */
    public static void clearCompleted() {
        for (Structure entry : data.structures) {
            entry.completedAt = 0L;
        }

        save();
    }

    public enum Kind {
        CAMP("camp", "Camp", "Camps", "C"),
        OUTPOST("outpost", "Outpost", "Outposts", "O"),
        TUNNEL("tunnel", "Tunnel", "Tunnels", "T"),
        TOWER("tower", "Tower", "Towers", "Tw");

        public final String id;
        public final String singular;
        public final String plural;
        public final String initial;

        Kind(String id, String singular, String plural, String initial) {
            this.id = id;
            this.singular = singular;
            this.plural = plural;
            this.initial = initial;
        }

        public static Kind of(String id) {
            for (Kind kind : values()) {
                if (kind.id.equals(Objects.requireNonNullElse(id, "").toLowerCase(Locale.ROOT))) {
                    return kind;
                }
            }

            return null;
        }
    }

    public static final class Structure {
        public String kind;
        /** The camp, outpost or tower number; 0 for a tunnel. */
        public int number;
        public int x;
        public int y;
        public int z;
        public long foundAt;
        public long completedAt;

        public BlockPos pos() {
            return new BlockPos(this.x, this.y, this.z);
        }

        public boolean completed() {
            return this.completedAt > 0L;
        }

        /** "Camp 19", "Outpost 3", "Tower 2" or "Tunnel". */
        public String name() {
            Kind kind = Kind.of(this.kind);
            return kind == Kind.TUNNEL ? kind.singular : kind.singular + " " + this.number;
        }
    }

    private static final class Data {
        public int schema = 1;
        public List<Structure> structures = new ArrayList<>();
    }
}
