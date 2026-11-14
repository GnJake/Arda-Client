package com.gnjake.ardaclient.currency;

import com.gnjake.ardaclient.ArdaClient;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;

/**
 * Crafts currency up a step at a crafting table: 8 Bloodstones make a Zircon, 4 Zircon a Forged Tungsten and 2 Forged
 * Tungsten a Black Gem. Arda's recipes are shapeless, so the ingredients go in the first grid slots. It works by
 * clicking slots the way a player would, a few clicks a tick so the server isn't flooded, and waits for the server to
 * show the result before taking it, since only the server knows the recipes.
 */
public final class CurrencyCrafter {
    public static final int RESULT_SLOT = 0;
    private static final int GRID_START = 1;
    private static final int INVENTORY_START = 10;
    private static final int CLICKS_PER_TICK = 6;
    /** Ticks to wait for the server to show the result, or to finish crafting, before giving up. */
    private static final int TIMEOUT = 40;
    /** Ticks to let the server's crafting reach the client before counting what it made. */
    private static final int SETTLE = 5;
    private static final long STATUS_FOR = 6_000L;

    private enum Phase { CLEAR, FILL, STOW, RESULT, SETTLE }

    private static final class Job {
        final int containerId;
        final boolean all;
        final Deque<ArdaCurrency> next = new ArrayDeque<>();
        final Map<ArdaCurrency, Integer> made = new EnumMap<>(ArdaCurrency.class);
        final Set<Integer> cleared = new HashSet<>();
        ArdaCurrency target;
        Phase phase = Phase.CLEAR;
        int perSlot;
        int source = -1;
        int waited;
        int before;

        Job(int containerId, boolean all, List<ArdaCurrency> targets) {
            this.containerId = containerId;
            this.all = all;
            this.next.addAll(targets);
            this.target = this.next.poll();
        }
    }

    private static Job job;
    private static String status = "";
    private static boolean failed;
    private static long statusAt;

    private CurrencyCrafter() {
    }

    /** The currency that makes this one, or null for Bloodstones. */
    public static ArdaCurrency ingredient(ArdaCurrency target) {
        return target.ordinal() == 0 ? null : ArdaCurrency.values()[target.ordinal() - 1];
    }

    /** How many of the ingredient one craft takes: 8, 4 or 2. */
    public static int needed(ArdaCurrency target) {
        return (int)(target.bloodstones / ingredient(target).bloodstones);
    }

    /** Fills the grid with one craft's worth of the recipe, or with {@code all}, crafts as many as possible. */
    public static void craft(ArdaCurrency target, boolean all) {
        start(List.of(target), all);
    }

    /** Turns all the currency into the highest coins it makes, keeping the change. */
    public static void allUp() {
        start(List.of(ArdaCurrency.ZIRCON, ArdaCurrency.FORGED_TUNGSTEN, ArdaCurrency.BLACK_GEM), true);
    }

    private static void start(List<ArdaCurrency> targets, boolean all) {
        Minecraft minecraft = Minecraft.getInstance();
        if (job != null || minecraft.player == null || !(minecraft.player.containerMenu instanceof CraftingMenu menu)) {
            return;
        }

        if (!menu.getCarried().isEmpty()) {
            fail("Put down the item you're holding first.");
            return;
        }

        job = new Job(menu.containerId, all, targets);
        status = "";
    }

    public static boolean busy() {
        return job != null;
    }

    /** What the last craft did or why it stopped, shown for a few seconds; empty when there's nothing to say. */
    public static String status(long now) {
        return job != null ? "Crafting..." : now - statusAt < STATUS_FOR ? status : "";
    }

    public static boolean failed() {
        return failed && job == null;
    }

    /** How many of a currency the player has in the open crafting menu: the inventory and the grid. */
    public static int count(CraftingMenu menu, ArdaCurrency currency) {
        return count(menu, currency, GRID_START);
    }

    private static int count(CraftingMenu menu, ArdaCurrency currency, int from) {
        int total = 0;
        for (int slot = from; slot < menu.slots.size(); slot++) {
            ItemStack stack = menu.getSlot(slot).getItem();
            if (ArdaCurrency.of(stack) == currency) {
                total += stack.getCount();
            }
        }

        return total;
    }

    public static void tick(Minecraft minecraft) {
        if (job == null) {
            return;
        }

        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.gameMode == null || !(player.containerMenu instanceof CraftingMenu menu) || menu.containerId != job.containerId) {
            job = null;
            return;
        }

        int clicks = 0;
        while (job != null && clicks < CLICKS_PER_TICK) {
            switch (job.phase) {
                case CLEAR -> {
                    int slot = firstFilled(menu, job.cleared);
                    if (slot >= 0) {
                        click(minecraft, menu, slot, 0, ContainerInput.QUICK_MOVE);
                        job.cleared.add(slot);
                        clicks++;
                        continue;
                    }

                    if (firstFilled(menu, Set.of()) >= 0) {
                        fail("Make room in your inventory first.");
                        return;
                    }

                    job.cleared.clear();
                    if (job.target == null) {
                        finish();
                        return;
                    }

                    int sets = count(menu, ingredient(job.target), INVENTORY_START) / needed(job.target);
                    if (sets == 0) {
                        if (!job.all) {
                            fail("You need " + ingredient(job.target).amount(needed(job.target)) + ".");
                            return;
                        }

                        job.target = job.next.poll();
                        continue;
                    }

                    job.perSlot = job.all ? Math.min(sets, ingredientStack(menu, job.target)) : 1;
                    job.phase = Phase.FILL;
                }
                case FILL -> {
                    if (fill(minecraft, menu)) {
                        clicks++;
                    }
                }
                case STOW -> {
                    if (!menu.getCarried().isEmpty()) {
                        if (!stow(minecraft, menu)) {
                            fail("Make room in your inventory first.");
                            return;
                        }

                        clicks++;
                        continue;
                    }

                    if (!job.all) {
                        job = null;
                        return;
                    }

                    job.before = count(menu, job.target, INVENTORY_START);
                    job.waited = 0;
                    job.phase = Phase.RESULT;
                }
                case RESULT -> {
                    if (ArdaCurrency.of(menu.getSlot(RESULT_SLOT).getItem()) == job.target) {
                        click(minecraft, menu, RESULT_SLOT, 0, ContainerInput.QUICK_MOVE);
                        job.waited = 0;
                        job.phase = Phase.SETTLE;
                    } else if (++job.waited > TIMEOUT) {
                        ArdaClient.LOG.info("Currency crafting: no {} in the result slot, grid {}", job.target.singular, describeGrid(menu));
                        fail("The server didn't make " + job.target.singular + " from this.");
                    }

                    return;
                }
                case SETTLE -> {
                    if (++job.waited < SETTLE) {
                        return;
                    }

                    int made = count(menu, job.target, INVENTORY_START) - job.before;
                    if (made <= 0) {
                        if (job.waited > TIMEOUT) {
                            fail("Make room in your inventory first.");
                        }

                        return;
                    }

                    job.made.merge(job.target, made, Integer::sum);
                    int sets = count(menu, ingredient(job.target)) / needed(job.target);
                    if (sets > 0) {
                        job.perSlot = Math.min(sets, ingredientStack(menu, job.target));
                        job.phase = Phase.FILL;
                    } else {
                        job.target = job.next.poll();
                        job.phase = Phase.CLEAR;
                    }

                    return;
                }
            }
        }
    }

    /**
     * Makes one click towards filling the recipe's slots with {@code perSlot} each, or moves on to putting the held stack
     * away once they're full. Returns whether it clicked.
     */
    private static boolean fill(Minecraft minecraft, CraftingMenu menu) {
        ArdaCurrency ingredient = ingredient(job.target);
        int slot = -1;
        for (int index = GRID_START; index < GRID_START + needed(job.target); index++) {
            if (menu.getSlot(index).getItem().getCount() < job.perSlot) {
                slot = index;
                break;
            }
        }

        ItemStack carried = menu.getCarried();
        if (slot < 0) {
            job.phase = Phase.STOW;
            return false;
        }

        ItemStack there = menu.getSlot(slot).getItem();
        if (!carried.isEmpty() && (ArdaCurrency.of(carried) != ingredient || !there.isEmpty() && !ItemStack.isSameItemSameComponents(carried, there))) {
            // Holding a different kind than the slot has, such as an older version of the coin.
            if (!stow(minecraft, menu)) {
                fail("Make room in your inventory first.");
            }

            return true;
        }

        if (carried.isEmpty()) {
            int from = find(menu, ingredient, there);
            if (from < 0) {
                // Nothing left that stacks with this slot: craft what's there.
                job.phase = Phase.STOW;
                return false;
            }

            click(minecraft, menu, from, 0, ContainerInput.PICKUP);
            job.source = from;
            return true;
        }

        int need = job.perSlot - there.getCount();
        // A left click places the whole stack, which the slot caps at a full stack anyway.
        boolean whole = carried.getCount() <= need || job.perSlot >= menu.getSlot(slot).getMaxStackSize(carried);
        click(minecraft, menu, slot, whole ? 0 : 1, ContainerInput.PICKUP);
        return true;
    }

    /** The most of one ingredient a grid slot holds: a full stack. */
    private static int ingredientStack(CraftingMenu menu, ArdaCurrency target) {
        for (int slot = GRID_START; slot < menu.slots.size(); slot++) {
            ItemStack stack = menu.getSlot(slot).getItem();
            if (ArdaCurrency.of(stack) == ingredient(target)) {
                return stack.getMaxStackSize();
            }
        }

        return 64;
    }

    /** An inventory slot with the ingredient that stacks with {@code like}, or any of it when {@code like} is empty. */
    private static int find(CraftingMenu menu, ArdaCurrency ingredient, ItemStack like) {
        for (int slot = INVENTORY_START; slot < menu.slots.size(); slot++) {
            ItemStack stack = menu.getSlot(slot).getItem();
            if (ArdaCurrency.of(stack) == ingredient && (like.isEmpty() || ItemStack.isSameItemSameComponents(stack, like))) {
                return slot;
            }
        }

        return -1;
    }

    /** Puts the held stack back where it came from, or anywhere in the inventory it fits. */
    private static boolean stow(Minecraft minecraft, CraftingMenu menu) {
        ItemStack carried = menu.getCarried();
        int target = -1;
        if (job.source >= INVENTORY_START && fits(menu, job.source, carried)) {
            target = job.source;
        }

        for (int slot = INVENTORY_START; target < 0 && slot < menu.slots.size(); slot++) {
            ItemStack stack = menu.getSlot(slot).getItem();
            if (!stack.isEmpty() && fits(menu, slot, carried)) {
                target = slot;
            }
        }

        for (int slot = INVENTORY_START; target < 0 && slot < menu.slots.size(); slot++) {
            if (menu.getSlot(slot).getItem().isEmpty()) {
                target = slot;
            }
        }

        if (target < 0) {
            return false;
        }

        click(minecraft, menu, target, 0, ContainerInput.PICKUP);
        return true;
    }

    private static boolean fits(CraftingMenu menu, int slot, ItemStack carried) {
        ItemStack stack = menu.getSlot(slot).getItem();
        return stack.isEmpty() || ItemStack.isSameItemSameComponents(stack, carried) && stack.getCount() < menu.getSlot(slot).getMaxStackSize(stack);
    }

    private static int firstFilled(CraftingMenu menu, Set<Integer> skip) {
        for (int slot = GRID_START; slot < INVENTORY_START; slot++) {
            if (!skip.contains(slot) && !menu.getSlot(slot).getItem().isEmpty()) {
                return slot;
            }
        }

        return -1;
    }

    private static void click(Minecraft minecraft, CraftingMenu menu, int slot, int button, ContainerInput input) {
        minecraft.gameMode.handleContainerInput(menu.containerId, slot, button, input, minecraft.player);
    }

    private static String describeGrid(CraftingMenu menu) {
        StringBuilder grid = new StringBuilder();
        for (int slot = GRID_START; slot < INVENTORY_START; slot++) {
            ItemStack stack = menu.getSlot(slot).getItem();
            grid.append(slot == GRID_START ? "" : ", ").append(stack.isEmpty() ? "-" : stack.getCount() + " " + stack.getHoverName().getString());
        }

        return grid.toString();
    }

    private static void finish() {
        StringBuilder made = new StringBuilder();
        job.made.forEach((currency, count) -> made.append(made.isEmpty() ? "Made " : ", ").append(currency.amount(count)));
        status = made.isEmpty() ? "Nothing to craft." : made.toString();
        failed = false;
        statusAt = System.currentTimeMillis();
        job = null;
    }

    private static void fail(String reason) {
        String made = "";
        if (job != null && !job.made.isEmpty()) {
            finish();
            made = status + ". ";
        }

        status = made + reason;
        failed = true;
        statusAt = System.currentTimeMillis();
        job = null;
    }
}
