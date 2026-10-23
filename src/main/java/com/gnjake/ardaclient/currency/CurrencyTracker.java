package com.gnjake.ardaclient.currency;

import com.gnjake.ardaclient.ArdaClient;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;

/**
 * Watches the player's currency to keep the counter and the earnings tally. Arda's currency items carry no unique ID,
 * so the tally can't recognize a coin it has seen before. Instead it counts currency as earned when it reaches the
 * player, unless it is coming back from somewhere the player put it:
 * <ul>
 *   <li>Currency inside bundles and shulker boxes counts as held, so moving it in and out of them changes nothing.</li>
 *   <li>With a container open, a gain in the inventory matched by a loss in the container (either order, within a
 *   few ticks) is a withdrawal. A loss matched by a container gain is a deposit and is remembered, so a stocked shulker
 *   box that is broken and picked up later isn't counted either. Gains with no matching container loss, such as a
 *   trader paying out, are earnings.</li>
 *   <li>Currency that leaves the inventory with no container open (dropped, or lost on death) is remembered for five
 *   minutes, so picking it back up isn't counted twice.</li>
 *   <li>Opening a container, joining, respawning or changing worlds starts a short grace period while the server sends
 *   the contents, during which nothing is counted.</li>
 * </ul>
 * Spending never lowers the tally; it records what was earned.
 */
public final class CurrencyTracker {
    private static final int OPEN_GRACE_TICKS = 10;
    private static final int JOIN_GRACE_TICKS = 60;
    /** How long one side of a container transfer waits for the other side to show up. */
    private static final int MATCH_TICKS = 5;
    /** Dropped items despawn after five minutes, so anything picked up later is new. */
    private static final long RECOVERY_WINDOW = 5 * CurrencyLedger.MINUTE;
    /** Bundles can hold bundles; this bounds the search. */
    private static final int MAX_NESTING = 4;

    private static Player lastPlayer;
    private static int lastMenuId = Integer.MIN_VALUE;
    private static int grace;
    private static long inventory;
    private static long nested;
    private static long container;
    /** Inventory gains with a container open, waiting to see whether the container lost the same amount. */
    private static final Pending gain = new Pending();
    /** Container losses waiting for the inventory to gain the same amount. */
    private static final Pending withdrawal = new Pending();
    /** Inventory losses with a container open, waiting for the container to gain the same amount. */
    private static final Pending deposit = new Pending();
    /** Container gains waiting for the inventory to lose the same amount. */
    private static final Pending received = new Pending();
    /** {time, bloodstones} for currency that left the inventory with no container open. */
    private static final ArrayDeque<long[]> dropped = new ArrayDeque<>();

    private CurrencyTracker() {
    }

    /** Bloodstones held: the inventory, bundles and shulker boxes in it, and a stack on the cursor. */
    public static long inventory() {
        return lastPlayer == null ? 0L : inventory;
    }

    /** Inventory plus the remembered ender chest when the player has turned that on. */
    public static long total(boolean withEnderChest) {
        long total = inventory();
        if (withEnderChest && lastPlayer != null) {
            total += Math.max(0L, CurrencyLedger.enderChest(lastPlayer.getUUID()));
        }

        return total;
    }

    public static boolean enderChestSeen() {
        return lastPlayer != null && CurrencyLedger.enderChest(lastPlayer.getUUID()) >= 0L;
    }

    public static long earned(long window, long now) {
        return lastPlayer == null ? 0L : CurrencyLedger.earned(lastPlayer.getUUID(), window, now);
    }

    public static void resetTally() {
        if (lastPlayer != null) {
            CurrencyLedger.resetEarned(lastPlayer.getUUID(), System.currentTimeMillis());
            dropped.clear();
        }
    }

    public static void tick(Minecraft minecraft) {
        long now = System.currentTimeMillis();
        CurrencyLedger.tick(now);
        Player player = minecraft.player;
        if (player == null) {
            lastPlayer = null;
            return;
        }

        UUID id = player.getUUID();
        AbstractContainerMenu menu = player.containerMenu;
        long held = 0L;
        long inside = 0L;
        Inventory items = player.getInventory();
        for (int slot = 0; slot <= items.getContainerSize(); slot++) {
            ItemStack stack = slot < items.getContainerSize() ? items.getItem(slot) : menu.getCarried();
            held += value(stack);
            inside += nestedValue(stack);
        }

        long cont = containerValue(menu);
        if (player != lastPlayer || menu.containerId != lastMenuId) {
            if (player == lastPlayer && grace == 0) {
                // A sale made just before closing the screen still counts.
                credit(id, gain.take(), 0L, now);
            }

            grace = player != lastPlayer ? JOIN_GRACE_TICKS : OPEN_GRACE_TICKS;
            lastPlayer = player;
            lastMenuId = menu.containerId;
            gain.clear();
            withdrawal.clear();
            deposit.clear();
            received.clear();
        }

        if (isEnderChest(minecraft.gui.screen(), menu) && grace == 0) {
            CurrencyLedger.setEnderChest(id, cont, now);
        }

        long invDelta = held - inventory;
        long nestedDelta = inside - nested;
        long contDelta = cont - container;
        inventory = held;
        nested = inside;
        container = cont;
        if (grace > 0) {
            grace--;
            return;
        }

        boolean open = menu != player.inventoryMenu;
        if (contDelta < 0L) {
            long out = -contDelta - gain.match(-contDelta);
            CurrencyLedger.takeStored(id, -contDelta - out, now);
            withdrawal.add(out);
        } else if (contDelta > 0L) {
            long in = deposit.match(contDelta);
            CurrencyLedger.addStored(id, in, now);
            received.add(contDelta - in);
        }

        if (invDelta > 0L) {
            long withdrawn = withdrawal.match(invDelta);
            CurrencyLedger.takeStored(id, withdrawn, now);
            long gained = invDelta - withdrawn;
            if (open) {
                gain.add(gained);
            } else {
                credit(id, gained, Math.min(gained, Math.max(0L, nestedDelta)), now);
            }
        } else if (invDelta < 0L) {
            if (open) {
                long stored = received.match(-invDelta);
                CurrencyLedger.addStored(id, stored, now);
                deposit.add(-invDelta - stored);
            } else {
                dropped.addLast(new long[]{now, -invDelta});
            }
        }

        // Unmatched gains are earnings; unmatched losses were spent or never came back.
        credit(id, gain.expire(), 0L, now);
        withdrawal.expire();
        deposit.expire();
        received.expire();
    }

    /**
     * Records a gain as earned, after taking out currency the player recently dropped and, for currency that arrived
     * inside a shulker box or bundle, currency the player had put into storage.
     */
    private static void credit(UUID id, long amount, long arrivedInside, long now) {
        if (amount <= 0L) {
            return;
        }

        long recovered = recover(amount, now);
        long restored = CurrencyLedger.takeStored(id, Math.min(arrivedInside, amount - recovered), now);
        long earned = amount - recovered - restored;
        if (earned > 0L) {
            CurrencyLedger.recordEarned(id, earned, now);
            ArdaClient.LOG.info("Currency tally: earned {}", ArdaCurrency.describe(earned));
        }

        if (recovered > 0L || restored > 0L) {
            ArdaClient.LOG.info("Currency tally: not counted {} picked back up, {} from storage",
                ArdaCurrency.describe(recovered), ArdaCurrency.describe(restored));
        }
    }

    /** Takes up to {@code amount} from currency the player recently dropped, returning how much was recovered. */
    private static long recover(long amount, long now) {
        long recovered = 0L;
        Iterator<long[]> it = dropped.iterator();
        while (it.hasNext() && recovered < amount) {
            long[] drop = it.next();
            if (now - drop[0] > RECOVERY_WINDOW) {
                it.remove();
                continue;
            }

            long take = Math.min(drop[1], amount - recovered);
            drop[1] -= take;
            recovered += take;
            if (drop[1] == 0L) {
                it.remove();
            }
        }

        return recovered;
    }

    /** Currency in the open menu's own slots: the chest, trader or crafting grid, not the player's inventory. */
    private static long containerValue(AbstractContainerMenu menu) {
        long total = 0L;
        for (Slot slot : menu.slots) {
            if (!(slot.container instanceof Inventory)) {
                total += value(slot.getItem());
            }
        }

        return total;
    }

    /** Currency in a stack, including anything stored inside it such as a bundle or shulker box. */
    private static long value(ItemStack stack) {
        ArdaCurrency currency = ArdaCurrency.of(stack);
        return currency != null ? currency.bloodstones * stack.getCount() : nestedValue(stack);
    }

    private static long nestedValue(ItemStack stack) {
        return nestedValue(stack, 0);
    }

    private static long nestedValue(ItemStack stack, int depth) {
        if (stack.isEmpty() || depth >= MAX_NESTING) {
            return 0L;
        }

        long total = 0L;
        BundleContents bundle = stack.get(DataComponents.BUNDLE_CONTENTS);
        if (bundle != null && !bundle.isEmpty()) {
            total += bundle.itemCopyStream().mapToLong(inner -> valueAt(inner, depth + 1)).sum();
        }

        ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
        if (contents != null) {
            total += contents.nonEmptyItemCopyStream().mapToLong(inner -> valueAt(inner, depth + 1)).sum();
        }

        return total;
    }

    private static long valueAt(ItemStack stack, int depth) {
        ArdaCurrency currency = ArdaCurrency.of(stack);
        return currency != null ? currency.bloodstones * stack.getCount() : nestedValue(stack, depth);
    }

    private static boolean isEnderChest(Screen screen, AbstractContainerMenu menu) {
        if (!(menu instanceof ChestMenu) || screen == null) {
            return false;
        }

        if (screen.getTitle().getContents() instanceof TranslatableContents translatable) {
            return translatable.getKey().equals("container.enderchest");
        }

        return screen.getTitle().getString().strip().toLowerCase(Locale.ROOT).equals("ender chest");
    }

    /** An amount waiting a few ticks for the other half of a transfer. */
    private static final class Pending {
        private long amount;
        private int ticks;

        void add(long value) {
            if (value > 0L) {
                this.amount += value;
                this.ticks = MATCH_TICKS + 1;
            }
        }

        /** Takes up to {@code value} to pair with the other side of a transfer, returning how much matched. */
        long match(long value) {
            long matched = Math.min(this.amount, value);
            this.amount -= matched;
            return matched;
        }

        /** Counts down, returning the leftover amount once the wait is over. */
        long expire() {
            if (this.ticks > 0 && --this.ticks == 0) {
                return this.take();
            }

            return 0L;
        }

        long take() {
            long value = this.amount;
            this.clear();
            return value;
        }

        void clear() {
            this.amount = 0L;
            this.ticks = 0;
        }
    }
}
