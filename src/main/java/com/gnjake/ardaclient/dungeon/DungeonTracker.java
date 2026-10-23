package com.gnjake.ardaclient.dungeon;

import com.gnjake.ardaclient.ServerCommands;
import com.gnjake.ardaclient.config.ArdaStore;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.network.chat.Component;

public final class DungeonTracker {
    public static final String[] NAMES = new String[]{
        "Upper Levels",
        "band of Easterlings",
        "Caverns of Ungoliant",
        "Pits",
        "Goblin Warrens",
        "Carven Halls",
        "Black Crypts",
        "Deeper Dark",
        "Deepest Dark",
        "Frozen Wastes",
        "Rotting Deeps",
        "Sunless Tomb"
    };
    public static final int TIERS = NAMES.length;
    private static final long HOUR = 3600000L;
    /** Statuses older than this are shown as outdated. */
    private static final long REFRESH_AFTER = 1200000L;
    /** Repeated announcements within this window are the same event. */
    private static final long ANNOUNCE_DEBOUNCE = 5000L;
    /** How long to show "Waiting" after requesting /dungeon_info. */
    private static final long REQUEST_TIMEOUT = 10000L;
    private static final Pattern ANNOUNCEMENT = Pattern.compile("(?i)\\[Dungeon\\]\\s*The\\s+(.+?)\\s+are challenged to combat[!.]?");
    private static final Pattern STATUS = Pattern.compile("(?i)\\bTier\\s+(1[0-2]|[1-9])\\b[^\\r\\n]*?\\b(COOLDOWN|OPEN|ACTIVE)\\b([^\\r\\n]*)");
    private static final Pattern HOURS = Pattern.compile("(?i)(\\d+)\\s*h(?:ours?|rs?)?");
    private static final Pattern MINUTES = Pattern.compile("(?i)(\\d+)\\s*m(?:in(?:utes?)?)?");
    private static final Pattern SECONDS = Pattern.compile("(?i)(\\d+)\\s*s(?:ec(?:onds?)?)?");
    private static final Pattern CLOCK = Pattern.compile("\\b(\\d{1,2}):(\\d{2})(?::(\\d{2}))?\\b");
    private static long requestStartedAt;

    private DungeonTracker() {
    }

    public static void requestInfo() {
        requestStartedAt = System.currentTimeMillis();
        ServerCommands.run("/dungeon_info");
    }

    public static void onMessage(Component message) {
        if (message != null) {
            observe(message.getString(), System.currentTimeMillis());
        }
    }

    private static void observe(String text, long now) {
        if (text != null && !text.isBlank()) {
            String clean = text.replaceAll("(?i)§[0-9a-fk-or]", "");
            boolean changed = false;

            for (String line : clean.split("\\R")) {
                Matcher announcement = ANNOUNCEMENT.matcher(line);
                if (announcement.find()) {
                    int tier = tierForName(announcement.group(1));
                    if (tier > 0) {
                        ArdaStore.DungeonTier entry = entry(tier);
                        if (entry.announcedAt == 0L || now - entry.announcedAt > ANNOUNCE_DEBOUNCE) {
                            entry.state = "ACTIVE";
                            entry.readyAt = now + HOUR;
                            entry.announcedAt = now;
                            entry.updatedAt = now;
                            changed = true;
                        }
                    }
                }

                Matcher status = STATUS.matcher(line);
                if (status.find()) {
                    int tier = Integer.parseInt(status.group(1));
                    String state = status.group(2).toUpperCase(Locale.ROOT);
                    String detail = status.group(3);
                    ArdaStore.DungeonTier entry = entry(tier);
                    String previousState = entry.state;
                    entry.state = state;
                    entry.updatedAt = now;
                    if (state.equals("OPEN")) {
                        entry.readyAt = 0L;
                    } else if (state.equals("COOLDOWN")) {
                        long remaining = parseRemaining(detail);
                        entry.readyAt = remaining > 0L ? now + remaining : 0L;
                    } else if (!previousState.equals("ACTIVE") || entry.readyAt <= now) {
                        entry.readyAt = 0L;
                    }

                    ArdaStore.data.dungeonInfoAt = now;
                    requestStartedAt = 0L;
                    changed = true;
                }
            }

            if (changed) {
                ArdaStore.save();
            }
        }
    }

    private static int tierForName(String name) {
        String sought = name.trim().replaceAll("\\s+", " ");

        for (int index = 0; index < NAMES.length; index++) {
            if (NAMES[index].equalsIgnoreCase(sought)) {
                return index + 1;
            }
        }

        return -1;
    }

    private static long parseRemaining(String text) {
        Matcher clock = CLOCK.matcher(text);
        if (clock.find()) {
            long first = Long.parseLong(clock.group(1));
            long second = Long.parseLong(clock.group(2));
            return clock.group(3) == null ? (first * 60L + second) * 1000L : (first * 3600L + second * 60L + Long.parseLong(clock.group(3))) * 1000L;
        } else {
            long hours = unit(HOURS, text);
            long minutes = unit(MINUTES, text);
            long seconds = unit(SECONDS, text);
            return Math.max(0L, (hours * 3600L + minutes * 60L + seconds) * 1000L);
        }
    }

    private static long unit(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? Long.parseLong(matcher.group(1)) : 0L;
    }

    public static ArdaStore.DungeonTier entry(int tier) {
        return ArdaStore.data.dungeons.get(tier - 1);
    }

    public static void tick(long now) {
        boolean changed = false;

        for (ArdaStore.DungeonTier entry : ArdaStore.data.dungeons) {
            if (entry.readyAt > 0L && now >= entry.readyAt && (entry.state.equals("ACTIVE") || entry.state.equals("COOLDOWN"))) {
                entry.state = "OPEN";
                entry.readyAt = 0L;
                entry.updatedAt = now;
                changed = true;
            }
        }

        if (changed) {
            ArdaStore.save();
        }
    }

    public static String status(ArdaStore.DungeonTier tier, long now) {
        boolean timed = tier.readyAt > now;
        return switch (tier.state) {
            case "OPEN" -> "Open";
            case "ACTIVE" -> timed ? "Active " + remaining(tier.readyAt - now) : "Active";
            case "COOLDOWN" -> timed ? remaining(tier.readyAt - now) : "Cooldown";
            default -> "Unknown";
        };
    }

    private static String remaining(long milliseconds) {
        long seconds = Math.max(0L, (milliseconds + 999L) / 1000L);
        return seconds >= 3600L
            ? seconds / 3600L + "h " + String.format(Locale.ROOT, "%02dm", seconds % 3600L / 60L)
            : String.format(Locale.ROOT, "%02d:%02d", seconds / 60L, seconds % 60L);
    }

    public static String prompt(long now) {
        if (requestStartedAt > 0L && now - requestStartedAt < REQUEST_TIMEOUT) {
            return "Waiting for /dungeon_info";
        }

        boolean unknown = ArdaStore.data.dungeons.stream()
            .filter(tier -> ArdaStore.data.shownDungeons[tier.tier - 1])
            .anyMatch(tier -> tier.state.equals("UNKNOWN") || !tier.state.equals("OPEN") && tier.readyAt <= 0L);
        if (unknown || ArdaStore.data.dungeonInfoAt <= 0L) {
            return "Run /dungeon_info";
        }

        return now - ArdaStore.data.dungeonInfoAt > REFRESH_AFTER ? "Outdated, run /dungeon_info" : "";
    }
}
