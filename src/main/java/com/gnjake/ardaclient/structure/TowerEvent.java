package com.gnjake.ardaclient.structure;

import com.gnjake.ardaclient.ArdaClient;
import com.gnjake.ardaclient.ServerCommands;
import com.gnjake.ardaclient.profit.ProfitEstimator;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Follows Arda's Towers Event from chat: which clan holds each tower, and the final standings. Arda announces each
 * capture, and now and then lists every tower's holder, which corrects anything missed. {@code /towersinfo holders}
 * prints the same list at any time, so the holders are known outside the event too. Points are left to Arda's own
 * scoreboard.
 */
public final class TowerEvent {
    public static final int TOWERS = 8;
    /** The event runs 30 minutes from its start. */
    public static final long LENGTH = 30 * 60_000L;
    /** How long the final standings stay up once the event ends. */
    private static final long RESULTS_SHOWN = 5 * 60_000L;
    /** How long after a "Tower Holders" header its "Tower N: clan" lines are read. */
    private static final long LISTING = 3000L;
    /** Once the event's scoreboard has been gone this long, the event is over. */
    private static final long BOARD_GONE = 60_000L;
    /** The scoreboard can linger after the event ends; it mustn't start it again this soon. */
    private static final long RESTART_AFTER = 15 * 60_000L;
    /** Outside the event, holders older than this are shown as outdated. */
    private static final long REFRESH_AFTER = 20 * 60_000L;
    /** How long to show "Waiting" after requesting the holders. */
    private static final long REQUEST_TIMEOUT = 10_000L;
    public static final String HOLDERS_COMMAND = "/towersinfo holders";

    private static final Pattern BEGUN = Pattern.compile("(?i)\\[Towers Event\\]:?\\s*Has Begun");
    private static final Pattern TAKEN = Pattern.compile("(?i)\\[Towers Event\\]:?\\s*(\\S+) has taken Tower (\\d+)");
    private static final Pattern HOLDERS = Pattern.compile("(?i)\\[Towers Event\\]:?\\s*Tower Holders");
    /** "Tower 3: AIEL (Resistance I)", or "Tower 3:" when nobody holds it. */
    private static final Pattern HOLDER = Pattern.compile("(?i)^\\s*Tower (\\d+):\\s*([^\\s(]*)");
    /** "AIEL has 4003 points. Their golems are now at no Resistance" only comes during the event. */
    private static final Pattern HAS_POINTS = Pattern.compile("(?i)\\[Towers Event\\]:?\\s*\\S+ has [\\d,]+ points");
    private static final Pattern WON = Pattern.compile("(?i)\\[Towers Event\\]:?\\s*(\\S+) has taken the towers!");
    private static final Pattern STANDING = Pattern.compile("(?i)\\[Towers Event\\]:?\\s*\\[(\\d+)\\]\\s*:\\s*\\[([^\\]]+)\\]\\s*»\\s*([\\d,]+)");
    private static final Pattern DROP_INVENTORY = Pattern.compile("(?i)\\[Towers Event\\]:?\\s*Haradwaith is now Drop Inventory");

    private static final Map<Integer, String> holders = new TreeMap<>();
    private static final List<Standing> standings = new ArrayList<>();
    private static final Set<String> loggedBoards = new HashSet<>();
    private static boolean active;
    private static long startedAt;
    private static long endedAt;
    private static long finishedAt;
    private static long listingUntil;
    /** When a full holder list last arrived, and when any holder last changed. */
    private static long listedAt;
    private static long updatedAt;
    private static long requestedAt;
    /** When the "Towers Event" sidebar was last showing. */
    private static long boardSeenAt;
    private static String winner = "";
    private static int ticks;

    private TowerEvent() {
    }

    /** Runs {@code /towersinfo holders}, showing "Waiting" until the list arrives. */
    public static void requestHolders() {
        requestedAt = System.currentTimeMillis();
        ServerCommands.run(HOLDERS_COMMAND);
    }

    /** What the panel should ask for, like the dungeon panel: nothing when the holders are known and current. */
    public static String prompt(long now) {
        if (requestedAt > 0L && now - requestedAt < REQUEST_TIMEOUT) {
            return "Waiting for " + HOLDERS_COMMAND;
        }

        if (listedAt <= 0L) {
            return "Run " + HOLDERS_COMMAND;
        }

        // During the event every capture is announced, so a list seen once stays right.
        return !active && now - updatedAt > REFRESH_AFTER ? "Outdated, run " + HOLDERS_COMMAND : "";
    }

    /** Checks once a second for the event's sidebar, which means the event is on even when the player joined late. */
    public static void tick(Minecraft minecraft) {
        if (++ticks % 20 != 0 || minecraft.level == null) {
            return;
        }

        long now = System.currentTimeMillis();
        List<String> sidebar = ProfitEstimator.sidebar(minecraft);
        if (sidebar.isEmpty() || !sidebar.getFirst().toLowerCase(Locale.ROOT).contains("towers")) {
            if (active && boardSeenAt > 0L && now - boardSeenAt > BOARD_GONE && startedAt == 0L) {
                // Joined mid-event and missed the end message: the board going away ends it.
                finish(now);
            }

            return;
        }

        if (loggedBoards.add(String.join(" | ", sidebar).replaceAll("\\d", "#"))) {
            ArdaClient.LOG.info("Towers sidebar: {}", sidebar);
        }

        boardSeenAt = now;
        begin();
    }

    public static void onMessage(Component message) {
        String text = message.getString().replaceAll("(?i)§[0-9a-fk-or]", "");
        long now = System.currentTimeMillis();
        if (BEGUN.matcher(text).find()) {
            // Every tower starts the event empty, so the list is complete from here.
            holders.clear();
            listedAt = now;
            updatedAt = now;
            standings.clear();
            winner = "";
            active = true;
            startedAt = now;
            endedAt = 0L;
            return;
        }

        Matcher taken = TAKEN.matcher(text);
        if (taken.find()) {
            begin();
            hold(Integer.parseInt(taken.group(2)), taken.group(1));
            return;
        }

        if (HOLDERS.matcher(text).find()) {
            listingUntil = now + LISTING;
            listedAt = now;
            updatedAt = now;
            requestedAt = 0L;
            return;
        }

        Matcher holder = HOLDER.matcher(text);
        if ((active || now < listingUntil) && holder.find()) {
            listingUntil = active ? listingUntil : now + LISTING;
            hold(Integer.parseInt(holder.group(1)), holder.group(2));
            return;
        }

        if (HAS_POINTS.matcher(text).find()) {
            begin();
            return;
        }

        Matcher won = WON.matcher(text);
        if (won.find()) {
            winner = won.group(1);
            finish(now);
            return;
        }

        Matcher standing = STANDING.matcher(text);
        if (standing.find()) {
            standings.add(new Standing(Integer.parseInt(standing.group(1)), standing.group(2), Long.parseLong(standing.group(3).replace(",", ""))));
            endedAt = endedAt == 0L ? now : endedAt;
            return;
        }

        if (DROP_INVENTORY.matcher(text).find()) {
            // The towers stay with whoever held them last.
            standings.clear();
            endedAt = 0L;
            winner = "";
        }
    }

    /** Joining partway through: the event starts without its start time, keeping the holders already known. */
    private static void begin() {
        long now = System.currentTimeMillis();
        if (!active && (finishedAt == 0L || now - finishedAt > RESTART_AFTER)) {
            standings.clear();
            winner = "";
            active = true;
            startedAt = 0L;
            endedAt = 0L;
        }
    }

    private static void finish(long now) {
        active = false;
        endedAt = now;
        finishedAt = now;
    }

    private static void hold(int tower, String clan) {
        if (tower < 1 || tower > TOWERS) {
            return;
        }

        updatedAt = System.currentTimeMillis();

        if (clan.isBlank()) {
            holders.remove(tower);
        } else {
            holders.put(tower, clan);
        }
    }

    /** Whether there is anything to show: the event is running, or it ended a few minutes ago. */
    public static boolean shown(long now) {
        return active || endedAt > 0L && now - endedAt < RESULTS_SHOWN;
    }

    public static boolean active() {
        return active;
    }

    /** The clan holding a tower, or an empty string. */
    public static String holder(int tower) {
        return holders.getOrDefault(tower, "");
    }

    /** Milliseconds left in the event, or -1 when the start wasn't seen. */
    public static long remaining(long now) {
        return active && startedAt > 0L ? Math.max(0L, startedAt + LENGTH - now) : -1L;
    }

    /** Each clan holding towers, with how many, most first. */
    public static List<Clan> clans() {
        Map<String, Integer> towers = new LinkedHashMap<>();
        holders.values().forEach(clan -> towers.merge(clan, 1, Integer::sum));
        List<Clan> list = new ArrayList<>();
        towers.forEach((name, count) -> list.add(new Clan(name, count)));
        list.sort((a, b) -> Integer.compare(b.towers, a.towers));
        return list;
    }

    public static String winner() {
        return winner;
    }

    public static List<Standing> standings() {
        return List.copyOf(standings);
    }

    public record Clan(String name, int towers) {
    }

    public record Standing(int place, String clan, long points) {
    }
}
