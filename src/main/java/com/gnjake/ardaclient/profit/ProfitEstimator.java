package com.gnjake.ardaclient.profit;

import com.gnjake.ardaclient.ArdaClient;
import com.gnjake.ardaclient.config.ArdaStore;
import com.gnjake.ardaclient.currency.ArdaCurrency;
import com.gnjake.ardaclient.dungeon.DungeonTracker;
import com.gnjake.ardaclient.gui.ArdaTheme;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

/**
 * Estimates what dungeons and skirmishes pay, from players' averages, and posts the estimate in chat when the player
 * starts or finishes one. Party size comes from the party list in the sidebar scoreboard.
 * <ul>
 *   <li>Skirmish: about 2.25 Black Gems per level, however many players took part, plus up to a Bloodstone per bonus
 *   kill in each reward chest.</li>
 *   <li>Dungeon: about 2.25 Black Gems per level plus 2.25, and 2.25 Mithril per level, for each player.</li>
 * </ul>
 */
public final class ProfitEstimator {
    public static final int MAX_PARTY = 5;
    public static final int SKIRMISH_LEVELS = 8;
    /** Bonus kills available at each skirmish level; each one adds a Bloodstone to every reward chest. */
    private static final int[] SKIRMISH_BONUS_KILLS = {11, 24, 39, 37, 55, 21, 41, 35};
    private static final double RATE = 2.25;
    /** A dungeon start counts as the player's when they changed worlds this recently, which entering one does. */
    private static final long ENTER_WINDOW = 45_000L;
    /** One finish can show up as both a vanquish message and a Chronicle message. */
    private static final long REPEAT_WINDOW = 60_000L;
    private static final long DUNGEON_TIMEOUT = 2 * 3_600_000L;

    private static final Pattern DUNGEON_START = Pattern.compile("(?i)\\[Dungeon\\]\\s*The\\s+(.+?)\\s+are challenged to combat");
    private static final Pattern DUNGEON_VANQUISHED = Pattern.compile("(?i)\\[Dungeon\\]\\s*(\\S+)\\s+has vanquished\\s+The\\s+(.+?)\\s*$");
    private static final Pattern SKIRMISH_VANQUISHED = Pattern.compile("(?i)\\[Skirmish\\]:?\\s*Level\\s+(\\d+)\\s+has been vanquished by\\s+(\\S+)");
    private static final Pattern SKIRMISH_INFO = Pattern.compile("(?i)^\\s*\\[Skirmish\\]:?\\s*Level\\s+(\\d+)\\s+(?:Spawn location|spawns at)\\b");
    /** Chronicle progress is only sent to players who took part. */
    private static final Pattern CHRONICLE = Pattern.compile("(?i)^\\s*Chronicle(?:\\s+Advanced!)?:?\\s*(Dungeon|Skirmish)\\s+Level\\s+(\\d+)\\b");
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]{3,16}");

    private static final Map<String, Long> reported = new HashMap<>();
    private static ClientLevel lastLevel;
    private static long levelChangedAt;
    private static int activeTier;
    private static int activePlayers;
    private static long activeSince;

    private ProfitEstimator() {
    }

    public static double skirmishGems(int level) {
        return RATE * level;
    }

    /**
     * The most extra Bloodstones a skirmish pays when every bonus kill is made, or 0 for an unknown level. A skirmish
     * has one reward chest per level, and each bonus kill adds a Bloodstone to every chest.
     */
    public static long skirmishMaxBonus(int level) {
        return (long)skirmishBonusKills(level) * level;
    }

    /** Bonus kills available at a skirmish level, or 0 for an unknown level. */
    public static int skirmishBonusKills(int level) {
        return level < 1 || level > SKIRMISH_LEVELS ? 0 : SKIRMISH_BONUS_KILLS[level - 1];
    }

    /** "~13.5 Black Gems +42 Bloodstones max bonus". */
    private static String skirmishEstimate(int level) {
        String base = "~" + format(skirmishGems(level)) + " Black Gems";
        long bonus = skirmishMaxBonus(level);
        return bonus > 0L ? base + " +" + ArdaCurrency.describe(bonus) + " max bonus" : base;
    }

    public static double dungeonGems(int level, int players) {
        return (RATE * level + RATE) * players;
    }

    public static double dungeonMithril(int level, int players) {
        return RATE * level * players;
    }

    /** A number with up to two decimals and no trailing zeros, such as 9, 4.5 or 6.75. */
    public static String format(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    /**
     * Adds the estimate to each skirmish in {@code /skirmish info}, whose lines read
     * "[Skirmish]: Level 6 Spawn location: ..." or "[Skirmish]: Level 5 spawns at: ... in 11 minutes".
     */
    public static Component annotate(Component message, boolean overlay) {
        if (overlay || !ArdaStore.data.profitChat || message == null) {
            return message;
        }

        Matcher info = SKIRMISH_INFO.matcher(message.getString().replaceAll("(?i)§[0-9a-fk-or]", ""));
        if (!info.find()) {
            return message;
        }

        String estimate = "  " + skirmishEstimate(Integer.parseInt(info.group(1)));
        return message.copy().append(ArdaTheme.accentComponent(estimate));
    }

    public static void tick(Minecraft minecraft) {
        if (minecraft.level != lastLevel) {
            lastLevel = minecraft.level;
            levelChangedAt = System.currentTimeMillis();
        }
    }

    public static void onMessage(Component message) {
        if (message == null) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        long now = System.currentTimeMillis();
        String clean = message.getString().replaceAll("(?i)§[0-9a-fk-or]", "");
        for (String line : clean.split("\\R")) {
            observe(minecraft, line, now);
        }
    }

    private static void observe(Minecraft minecraft, String line, long now) {
        if (minecraft.player == null) {
            return;
        }

        if (activeTier > 0 && now - activeSince > DUNGEON_TIMEOUT) {
            activeTier = 0;
        }

        Matcher start = DUNGEON_START.matcher(line);
        if (start.find()) {
            int tier = DungeonTracker.tierForName(start.group(1));
            if (tier > 0 && now - levelChangedAt <= ENTER_WINDOW) {
                activeTier = tier;
                activeSince = now;
                activePlayers = partySize(minecraft);
                ArdaClient.LOG.info("Profit estimate: started dungeon {} with {} players; sidebar shows {}", tier, activePlayers, sidebar(minecraft));
                post(minecraft, dungeonName(tier) + " started with " + players(activePlayers) + ".", dungeonEstimate(tier, activePlayers));
            }

            return;
        }

        Matcher vanquished = DUNGEON_VANQUISHED.matcher(line);
        if (vanquished.find()) {
            int tier = DungeonTracker.tierForName(vanquished.group(2));
            if (tier > 0 && partyNames(minecraft).contains(vanquished.group(1))) {
                dungeonFinished(minecraft, tier, now);
            }

            return;
        }

        Matcher skirmish = SKIRMISH_VANQUISHED.matcher(line);
        if (skirmish.find()) {
            if (partyNames(minecraft).contains(skirmish.group(2))) {
                skirmishFinished(minecraft, Integer.parseInt(skirmish.group(1)), now);
            }

            return;
        }

        Matcher chronicle = CHRONICLE.matcher(line);
        if (chronicle.find()) {
            int level = Integer.parseInt(chronicle.group(2));
            if (chronicle.group(1).equalsIgnoreCase("dungeon")) {
                dungeonFinished(minecraft, level, now);
            } else {
                skirmishFinished(minecraft, level, now);
            }
        }
    }

    private static void dungeonFinished(Minecraft minecraft, int tier, long now) {
        if (tier < 1 || tier > DungeonTracker.TIERS || !firstReport("dungeon:" + tier, now)) {
            return;
        }

        // The party can shrink during a run; the larger count is the better guess when the scoreboard is gone.
        int players = partySize(minecraft);
        if (tier == activeTier) {
            players = Math.max(players, activePlayers);
            activeTier = 0;
        }

        post(minecraft, dungeonName(tier) + " complete with " + players(players) + ".", dungeonEstimate(tier, players));
    }

    private static void skirmishFinished(Minecraft minecraft, int level, long now) {
        if (level >= 1 && firstReport("skirmish:" + level, now)) {
            post(minecraft, "Skirmish Level " + level + " complete.", "Estimated profit: " + skirmishEstimate(level) + ".");
        }
    }

    private static boolean firstReport(String key, long now) {
        Long last = reported.get(key);
        if (last != null && now - last < REPEAT_WINDOW) {
            return false;
        }

        reported.put(key, now);
        return true;
    }

    private static String dungeonName(int tier) {
        return DungeonTracker.NAMES[tier - 1] + " (Dungeon " + tier + ")";
    }

    private static String players(int count) {
        return count + (count == 1 ? " player" : " players");
    }

    private static String dungeonEstimate(int tier, int players) {
        String party = "~" + format(dungeonGems(tier, players)) + " Black Gems and " + format(dungeonMithril(tier, players)) + " Mithril";
        if (players == 1) {
            return "Estimated profit: " + party + ".";
        }

        return "Estimated profit: " + party + " for the party, " + format(dungeonGems(tier, 1)) + " Black Gems and "
            + format(dungeonMithril(tier, 1)) + " Mithril each.";
    }

    private static void post(Minecraft minecraft, String what, String estimate) {
        ArdaClient.LOG.info("Profit estimate: {} {}", what, estimate);
        if (ArdaStore.data.profitChat && minecraft.player != null) {
            minecraft.player.sendSystemMessage(ArdaTheme.accentComponent("[Profit]").append(" ")
                .append(Component.literal(what + " ").withColor(ArdaTheme.TEXT & 0xFFFFFF))
                .append(Component.literal(estimate).withColor(ArdaTheme.MUTED & 0xFFFFFF)));
        }
    }

    /** The player and everyone in their party, as far as the sidebar scoreboard shows. */
    public static Set<String> partyNames(Minecraft minecraft) {
        Set<String> names = new LinkedHashSet<>();
        if (minecraft.player == null) {
            return names;
        }

        names.add(minecraft.player.getGameProfile().name());
        List<String> lines = sidebar(minecraft);
        if (lines.stream().noneMatch(line -> line.toLowerCase(Locale.ROOT).contains("party")) || minecraft.getConnection() == null) {
            return names;
        }

        Set<String> online = new LinkedHashSet<>();
        for (PlayerInfo info : minecraft.getConnection().getOnlinePlayers()) {
            online.add(info.getProfile().name());
        }

        for (String line : lines) {
            Matcher name = NAME.matcher(line);
            while (name.find()) {
                if (online.contains(name.group())) {
                    names.add(name.group());
                }
            }
        }

        return names;
    }

    /** How many players are in the party, from 1 to 5. Without a party list in the sidebar, the player is alone. */
    public static int partySize(Minecraft minecraft) {
        return Math.max(1, Math.min(MAX_PARTY, partyNames(minecraft).size()));
    }

    /** The sidebar's title and lines as plain text, top line first. */
    public static List<String> sidebar(Minecraft minecraft) {
        List<String> lines = new ArrayList<>();
        if (minecraft.level == null) {
            return lines;
        }

        Scoreboard scoreboard = minecraft.level.getScoreboard();
        Objective objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
        if (objective == null) {
            return lines;
        }

        lines.add(plain(objective.getDisplayName()));
        List<PlayerScoreEntry> entries = new ArrayList<>(scoreboard.listPlayerScores(objective));
        entries.removeIf(PlayerScoreEntry::isHidden);
        entries.sort((a, b) -> Integer.compare(b.value(), a.value()));
        for (PlayerScoreEntry entry : entries) {
            PlayerTeam team = scoreboard.getPlayersTeam(entry.owner());
            lines.add(plain(PlayerTeam.formatNameForTeam(team, entry.ownerName())));
        }

        return lines;
    }

    private static String plain(Component component) {
        return component.getString().replaceAll("(?i)§[0-9a-fk-or]", "").strip();
    }
}
