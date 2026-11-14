package com.gnjake.ardaclient.dungeon;

import com.gnjake.ardaclient.ArdaClient;
import com.gnjake.ardaclient.ArdaItems;
import com.gnjake.ardaclient.config.ArdaStore;
import com.gnjake.ardaclient.mixin.BossHealthOverlayAccessor;
import com.gnjake.ardaclient.profit.ProfitEstimator;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/**
 * Tracks Magical Ward per dungeon level: what hits took off, what regenerated, and what each food or potion gave. The
 * charge is read from the boss bar ("Magic Ward Charge 783/800") or a sidebar line that mentions it, every tick.
 * Regeneration can add 40 or more at once, so a rise can't be told apart by its size. Instead, when the player finishes
 * eating or drinking something, the ward it gives is read from the item: a golden apple's lore ("Magic Ward Charge »
 * 50"), or how much a Miruvor's stored ward went down (or its ward mode). The next rises go to that item, up to that
 * amount, and the rest is regeneration. An item with no ward on it gets any rise just after it. Each new format is written to the log once, so the parsing can be checked. The dungeon comes
 * from the profit estimator, which knows when the player's own run starts and ends.
 */
public final class WardTracker {
    public static final int MAX_RUNS = 30;
    /** "Ward", "Magic Ward" or "Magical Ward", but not "Ward Regen", "reward" or "toward". */
    private static final Pattern WARD = Pattern.compile("(?i)\\bward\\b(?!\\s*regen)");
    private static final Pattern NUMBER = Pattern.compile("(\\d+(?:\\.\\d+)?)(?:\\s*/\\s*(\\d+(?:\\.\\d+)?))?");
    /** Rises this soon after eating or drinking something are put down to it. */
    private static final long CONSUMED_WINDOW = 1_500L;
    /** How long an item's ward can take to show up on the bar. */
    private static final long ITEM_WARD_WINDOW = 5_000L;
    /** The ward a food's lore gives: "Magic Ward Charge » 50", but not Miruvor's "Stored Ward » 0". */
    private static final Pattern LORE_WARD = Pattern.compile("(?i)(?<!stored\\s)\\bward(?:\\s+charge)?\\s*(?:»|>>|:)\\s*\\+?(\\d+(?:\\.\\d+)?)");
    private static final Pattern WARD_MODE = Pattern.compile("(?i)ward\\s+mode\\s*(?:»|>>|:)\\s*(\\d+(?:\\.\\d+)?)");
    /** Ward that's gone this long is read fresh when it's back, so its return isn't counted as regenerated. */
    private static final long FORGET_AFTER = 30_000L;
    /** The panel stays up this long after the ward was last on screen. */
    private static final long SHOWN_FOR = 60_000L;

    private static final Set<String> loggedFormats = new HashSet<>();
    private static double current = Double.NaN;
    private static double max;
    private static long seenAt;
    private static ArdaStore.WardRun run;
    private static int ticks;
    /** What the player is eating or drinking, and from which hand, while they are. */
    private static ItemStack using = ItemStack.EMPTY;
    private static InteractionHand usingHand;
    private static int usingSlot;
    /** The last thing the player finished eating or drinking, and when. */
    private static String consumed = "";
    private static long consumedAt;
    /** Ward that items the player just used still have to give: {name, remaining, expires}. */
    private static final List<Object[]> pending = new ArrayList<>();

    private WardTracker() {
    }

    private record Reading(double value, double max) {
    }

    public static void tick(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            return;
        }

        ticks++;
        long now = System.currentTimeMillis();
        int dungeon = ProfitEstimator.activeDungeon() > 0 ? ProfitEstimator.activeDungeon() : ProfitEstimator.arrivedAtDungeon();
        if (run != null && run.tier != dungeon) {
            finish(now);
        }

        if (run == null && dungeon > 0) {
            run = new ArdaStore.WardRun();
            run.tier = dungeon;
            run.startedAt = now;
        }

        if (run != null && ProfitEstimator.activeDungeon() == run.tier) {
            run.started = true;
        }

        watchConsuming(player, now);
        Reading reading = read(minecraft);
        if (reading == null) {
            if (now - seenAt > FORGET_AFTER) {
                current = Double.NaN;
            }

            return;
        }

        if (!Double.isNaN(current) && run != null) {
            double change = reading.value - current;
            if (change < 0) {
                run.lost -= change;
            } else if (change > 0) {
                change = fromItems(change, now);
                if (change > 0 && now - consumedAt < CONSUMED_WINDOW) {
                    run.item(consumed).ward += change;
                } else {
                    run.regained += change;
                }
            }
        }

        if (run != null) {
            run.seen = true;
        }

        current = reading.value;
        max = reading.max;
        seenAt = now;
    }

    /** Puts a rise down to items just used, up to the ward each gives, and returns what's left over as regeneration. */
    private static double fromItems(double change, long now) {
        pending.removeIf(item -> now > (long)item[2]);
        for (Object[] item : pending) {
            double given = Math.min(change, (double)item[1]);
            run.item((String)item[0]).ward += given;
            item[1] = (double)item[1] - given;
            change -= given;
        }

        pending.removeIf(item -> (double)item[1] <= 0.0);
        return change;
    }

    /**
     * The ward an item gives, from what it was before and after use: how far a Miruvor's stored ward went down, else its
     * ward mode, else a number after "Ward" in the lore. NaN when the item doesn't say.
     */
    private static double wardOf(ItemStack before, ItemStack after) {
        if (ArdaItems.serverItemType(before).equals("miruvor")) {
            double stored = ArdaItems.customNumber(before, "arda:miruvor_stored_ward", Double.NaN) - ArdaItems.customNumber(after, "arda:miruvor_stored_ward", Double.NaN);
            if (stored > 0) {
                return stored;
            }

            double mode = ArdaItems.customNumber(before, "arda:miruvor_ward_mode", Double.NaN);
            if (!Double.isNaN(mode)) {
                return mode;
            }
        }

        for (String line : ArdaItems.lore(before)) {
            String text = plain(line);
            for (Pattern pattern : new Pattern[]{WARD_MODE, LORE_WARD}) {
                Matcher ward = pattern.matcher(text);
                if (ward.find()) {
                    return Double.parseDouble(ward.group(1));
                }
            }
        }

        return Double.NaN;
    }

    /**
     * Notices when the player finishes eating or drinking: they stop using a food or potion, and the stack in that hand
     * has shrunk or turned into something else, such as a potion into a bottle.
     */
    private static void watchConsuming(LocalPlayer player, long now) {
        if (player.isUsingItem()) {
            if (using.isEmpty()) {
                usingHand = player.getUsedItemHand();
                usingSlot = player.getInventory().getSelectedSlot();
                using = player.getItemInHand(usingHand).copy();
            }

            return;
        }

        if (using.isEmpty()) {
            return;
        }

        ItemStack after = player.getItemInHand(usingHand);
        // A Miruvor stays the same item and only its stored ward changes, so any change counts. Switching hotbar slots
        // stops eating too, so that's not a finish.
        boolean switched = usingHand == InteractionHand.MAIN_HAND && player.getInventory().getSelectedSlot() != usingSlot;
        boolean finished = !switched && (after.getCount() < using.getCount() || !ItemStack.isSameItemSameComponents(after, using));
        if (finished && (using.has(DataComponents.FOOD) || using.has(DataComponents.CONSUMABLE))) {
            consumed = plain(using.getHoverName().getString());
            double ward = wardOf(using, after);
            ArdaClient.LOG.info("Ward: used {}, which gives {}", consumed, Double.isNaN(ward) ? "no ward it says" : ProfitEstimator.format(ward) + " ward");
            if (Double.isNaN(ward)) {
                consumedAt = now;
            } else {
                pending.add(new Object[]{consumed, ward, now + ITEM_WARD_WINDOW});
                consumedAt = 0L;
            }

            if (run != null) {
                run.item(consumed).count++;
            }
        }

        using = ItemStack.EMPTY;
    }

    private static void finish(long now) {
        if (run.seen && run.started) {
            run.endedAt = now;
            List<ArdaStore.WardRun> runs = ArdaStore.data.wardRuns;
            runs.add(run);
            while (runs.size() > MAX_RUNS) {
                runs.removeFirst();
            }

            StringBuilder items = new StringBuilder();
            run.items.forEach((name, item) -> items.append(", ").append(name).append(" x").append(item.count).append(" gave ")
                .append(ProfitEstimator.format(item.ward)));
            ArdaClient.LOG.info("Ward: Dungeon {} lost {} to hits, regenerated {}{}", run.tier, ProfitEstimator.format(run.lost),
                ProfitEstimator.format(run.regained), items);
            ArdaStore.save();
        }

        run = null;
    }

    private static Reading read(Minecraft minecraft) {
        BossHealthOverlayAccessor overlay = (BossHealthOverlayAccessor)minecraft.gui.hud.getBossOverlay();
        for (LerpingBossEvent bar : overlay.ardaClient$events().values()) {
            String text = plain(bar.getName().getString());
            if (WARD.matcher(text).find()) {
                logFormat("boss bar", text);
                Reading reading = parse(text);
                return reading != null ? reading : new Reading(Math.round(bar.getProgress() * 1000.0) / 10.0, 100.0);
            }
        }

        // The sidebar changes rarely, and reading it every tick isn't worth it.
        if (ticks % 5 != 0) {
            return null;
        }

        List<String> sidebar = ProfitEstimator.sidebar(minecraft);
        for (int index = 1; index < sidebar.size(); index++) {
            String line = sidebar.get(index);
            if (WARD.matcher(line).find()) {
                logFormat("sidebar", line);
                Reading reading = parse(line);
                if (reading != null) {
                    return reading;
                }
            }
        }

        return null;
    }

    private static Reading parse(String text) {
        Matcher number = NUMBER.matcher(text);
        if (!number.find()) {
            return null;
        }

        double value = Double.parseDouble(number.group(1));
        return new Reading(value, number.group(2) == null ? 0.0 : Double.parseDouble(number.group(2)));
    }

    private static void logFormat(String where, String text) {
        if (loggedFormats.add(where + ":" + text.replaceAll("\\d+(\\.\\d+)?", "#"))) {
            ArdaClient.LOG.info("Ward {}: {}", where, text);
        }
    }

    private static String plain(String text) {
        return text.replaceAll("(?i)§[0-9a-fk-or]", "").strip();
    }

    /** The ward on screen, or NaN when none has been seen lately. */
    public static double current() {
        return current;
    }

    /** The most the ward can be, or 0 when Arda doesn't show it. */
    public static double max() {
        return max;
    }

    /** The current dungeon level's run, or null outside a dungeon. */
    public static ArdaStore.WardRun run() {
        return run;
    }

    /** Whether there's anything to show: a dungeon run, or ward on screen lately. */
    public static boolean shown(long now) {
        return run != null || now - seenAt < SHOWN_FOR;
    }
}
