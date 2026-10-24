package com.gnjake.ardaclient.currency;

import com.gnjake.ardaclient.ArdaClient;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;

/**
 * Watches the player's currency, or their Mithril, to keep a counter and an earnings tally. Arda's currency items carry no unique ID,
 * so the tally can't recognize a coin it has seen before. Instead it counts currency as earned when it reaches the
 * player, unless it is coming back from somewhere the player put it:
 * <ul>
 *   <li>Currency inside bundles and shulker boxes counts as held, so moving it in and out of them changes nothing.</li>
 *   <li>Each container remembers how much currency the player put into it: a chest by its position (both halves of a
 *   double chest are one), the ender chest, shulker boxes, and server menus by their title. Taking currency out of a
 *   container only counts as earned beyond what the player put in, so loot from a dungeon or camp chest counts and a
 *   round trip through a storage chest doesn't.</li>
 *   <li>Gains with a container open and nothing taken from it, such as a trader paying out, are earnings.</li>
 *   <li>The ender chest is the player's own, so nothing taken out of it is earned, including currency that was there
 *   before the tally started.</li>
 *   <li>Currency left in a server menu's slots, such as an upgrade or crafting screen, is handed back when the menu
 *   closes or changes; for a short while that doesn't count either.</li>
 *   <li>Currency that leaves the inventory with no container open (dropped, or lost on death) is remembered for five
 *   minutes, so picking it back up isn't counted twice.</li>
 * </ul>
 * Spending never lowers the tally; it records what was earned.
 */
public final class CurrencyTracker {
    /** Joining, respawning or changing worlds loads the whole inventory at once; that isn't income. */
    private static final int JOIN_GRACE_TICKS = 60;
    /** How long one side of a container transfer waits for the other side to show up. */
    private static final int MATCH_TICKS = 5;
    /** Dropped items despawn after five minutes, so anything picked up later is new. */
    private static final long RECOVERY_WINDOW = 5 * CurrencyLedger.MINUTE;
    /** How long currency left in a closed menu may take to come back to the inventory. */
    private static final long HANDED_BACK_WINDOW = 15_000L;
    /** Bundles can hold bundles; this bounds the search. */
    private static final int MAX_NESTING = 4;
    /** Shulker boxes share one key: a filled box can be broken, carried and placed anywhere. */
    private static final String SHULKER_BOXES = "shulker_boxes";
    private static final String ENDER_CHEST = "ender_chest";

    /** Black Gems, Forged Tungsten, Zircon and Bloodstones, counted in Bloodstones. */
    public static final CurrencyTracker CURRENCY = new CurrencyTracker(Tally.CURRENCY);
    /** Crude Mithril Ore. */
    public static final CurrencyTracker MITHRIL = new CurrencyTracker(Tally.MITHRIL);

    private final Tally tally;
    private Player lastPlayer;
    private int lastMenuId = Integer.MIN_VALUE;
    private String containerKey;
    /** The open container's title, for the currency log. */
    private String containerTitle = "";
    private int grace;
    private long inventory;
    private long nested;
    private long container;
    /** Inventory gains with a container open, waiting to see whether the container lost the same amount. */
    private final Pending gain = new Pending();
    /** Container losses waiting for the inventory to gain the same amount. */
    private final Pending withdrawal = new Pending();
    /** Inventory losses with a container open, waiting for the container to gain the same amount. */
    private final Pending deposit = new Pending();
    /** Container gains waiting for the inventory to lose the same amount. */
    private final Pending received = new Pending();
    /** Inventory gains with only the inventory screen open, waiting in case a matching loss follows, as in crafting. */
    private final Pending unsettled = new Pending();
    /** Currency in the open menu's crafting grid last tick. */
    private long lastGrid;
    /** {expires, amount} for what left the inventory with no container open. */
    private final ArrayDeque<long[]> dropped = new ArrayDeque<>();
    /** {expires, amount} for currency a closed menu is about to hand back, from its slots or its crafting grid. */
    private final ArrayDeque<long[]> handedBack = new ArrayDeque<>();

    private CurrencyTracker(Tally tally) {
        this.tally = tally;
    }

    public static void tick(Minecraft minecraft) {
        CurrencyLedger.tick(System.currentTimeMillis());
        CURRENCY.update(minecraft);
        MITHRIL.update(minecraft);
    }

    /** The amount held: the inventory, bundles and shulker boxes in it, and a stack on the cursor. */
    public long inventory() {
        return lastPlayer == null ? 0L : inventory;
    }

    /** Inventory plus the remembered ender chest when the player has turned that on. */
    public long total(boolean withEnderChest) {
        long total = inventory();
        if (withEnderChest && lastPlayer != null) {
            total += Math.max(0L, CurrencyLedger.enderChest(lastPlayer.getUUID(), this.tally));
        }

        return total;
    }

    public boolean enderChestSeen() {
        return lastPlayer != null && CurrencyLedger.enderChest(lastPlayer.getUUID(), this.tally) >= 0L;
    }

    public long earned(long window, long now) {
        return lastPlayer == null ? 0L : CurrencyLedger.earned(lastPlayer.getUUID(), this.tally, window, now);
    }

    public void resetTally() {
        if (lastPlayer != null) {
            CurrencyLedger.resetEarned(lastPlayer.getUUID(), this.tally, System.currentTimeMillis());
            dropped.clear();
            handedBack.clear();
        }
    }

    private void update(Minecraft minecraft) {
        long now = System.currentTimeMillis();
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

        // Currency in a crafting grid is still the player's, so crafting it up or down only changes its form.
        long grid = 0L;
        for (Slot slot : menu.slots) {
            if (slot.container instanceof CraftingContainer) {
                grid += value(slot.getItem());
                inside += nestedValue(slot.getItem());
            }
        }

        held += grid;

        long cont = containerValue(menu);
        long previousGrid = lastGrid;
        lastGrid = grid;
        boolean changed = player != lastPlayer || menu.containerId != lastMenuId;
        if (changed) {
            if (player == lastPlayer && grace == 0) {
                // A sale made just before closing the screen still counts, and so does a pickup still settling.
                if (!ENDER_CHEST.equals(containerKey)) {
                    earn(id, gain.take(), now, "paid while a menu was open");
                }

                this.pickedUp(id, unsettled.take(), now);
            }

            if (player == lastPlayer) {
                // Closing a crafting or upgrade screen hands back what was left in it a moment later; that isn't new.
                long left = previousGrid;
                if (containerKey != null && isMenuKey(containerKey)) {
                    left += CurrencyLedger.takeStored(id, this.tally, containerKey, container, now);
                }

                if (left > 0L) {
                    handedBack.addLast(new long[]{now + HANDED_BACK_WINDOW, left});
                }
            }

            // The first tick compares against the previous menu, so it is always skipped.
            grace = player != lastPlayer ? JOIN_GRACE_TICKS : 1;
            lastPlayer = player;
            lastMenuId = menu.containerId;
            containerKey = menu == player.inventoryMenu ? null : containerKey(minecraft, menu);
            containerTitle = minecraft.gui.screen() == null ? "" : minecraft.gui.screen().getTitle().getString().strip();
            gain.clear();
            withdrawal.clear();
            deposit.clear();
            received.clear();
            unsettled.clear();
        }

        if (ENDER_CHEST.equals(containerKey) && grace == 0) {
            CurrencyLedger.setEnderChest(id, this.tally, cont, now);
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

        if (contDelta < 0L) {
            long matched = gain.match(-contDelta);
            withdrawn(id, matched, now);
            withdrawal.add(-contDelta - matched);
        } else if (contDelta > 0L) {
            long matched = deposit.match(contDelta);
            CurrencyLedger.addStored(id, this.tally, containerKey, matched, now);
            received.add(contDelta - matched);
        }

        if (invDelta > 0L) {
            long matched = withdrawal.match(invDelta);
            withdrawn(id, matched, now);
            long gained = invDelta - matched;
            if (containerKey != null) {
                gain.add(gained);
            } else {
                long arrivedInBox = Math.min(gained, Math.max(0L, nestedDelta));
                long restored = CurrencyLedger.takeStored(id, this.tally, SHULKER_BOXES, arrivedInBox, now);
                if (restored > 0L) {
                    ArdaClient.LOG.info("{} tally: not counted {} from a shulker box", this.tally.logName, this.tally.describe(restored));
                }

                if (minecraft.gui.screen() != null) {
                    // With the inventory open, crafting can show the result a tick before it uses up the grid.
                    unsettled.add(gained - restored);
                } else {
                    this.pickedUp(id, gained - restored, now);
                }
            }
        } else if (invDelta < 0L) {
            long loss = -invDelta - gain.match(-invDelta) - unsettled.match(-invDelta);
            if (containerKey != null) {
                long matched = received.match(loss);
                CurrencyLedger.addStored(id, this.tally, containerKey, matched, now);
                deposit.add(loss - matched);
            } else if (loss > 0L) {
                dropped.addLast(new long[]{now + RECOVERY_WINDOW, loss});
            }
        }

        this.pickedUp(id, unsettled.expire(), now);

        // Unmatched gains are earnings, except at the ender chest; unmatched losses were spent or never came back.
        long paid = gain.expire();
        if (ENDER_CHEST.equals(containerKey)) {
            this.withdrawn(id, paid, now);
        } else {
            earn(id, paid, now, "paid while a menu was open");
        }
        withdrawal.expire();
        deposit.expire();
        received.expire();
    }

    /** Currency that reached the inventory with no container open: new, unless it is the player's own drop. */
    private void pickedUp(UUID id, long amount, long now) {
        long returned = recover(handedBack, amount, now);
        if (returned > 0L) {
            ArdaClient.LOG.info("{} tally: not counted {} handed back by a closed menu", this.tally.logName, this.tally.describe(returned));
        }

        long recovered = recover(dropped, amount - returned, now);
        if (recovered > 0L) {
            ArdaClient.LOG.info("{} tally: not counted {} picked back up", this.tally.logName, this.tally.describe(recovered));
        }

        earn(id, amount - returned - recovered, now, "picked up");
    }

    /** Currency taken out of the open container: what the player put in comes back, anything more is earned. */
    private void withdrawn(UUID id, long amount, long now) {
        if (amount <= 0L) {
            return;
        }

        long own = CurrencyLedger.takeStored(id, this.tally, containerKey, amount, now);
        if (ENDER_CHEST.equals(containerKey)) {
            // Only the player fills their ender chest, even with currency stored before the tally began.
            own = amount;
        } else if (own < amount && isMenuKey(containerKey)) {
            // A menu that replaced another, such as the next page of an upgrade screen, still shows the player's currency.
            own += recover(handedBack, amount - own, now);
        }

        if (own > 0L) {
            ArdaClient.LOG.info("{} tally: not counted {} taken back out of {}", this.tally.logName, this.tally.describe(own), containerKey);
        }

        earn(id, amount - own, now, "taken from " + containerKey);
    }

    private void earn(UUID id, long amount, long now, String how) {
        if (amount > 0L) {
            CurrencyLedger.recordEarned(id, this.tally, amount, now, source(how));
            ArdaClient.LOG.info("{} tally: earned {} ({})", this.tally.logName, this.tally.describe(amount), how);
        }
    }

    /** A short description of where currency came from, for the currency log. */
    private String source(String how) {
        if (how.equals("picked up")) {
            return "Picked up";
        }

        if (!how.startsWith("taken from ")) {
            return containerTitle.isEmpty() ? "Paid in a menu" : "Paid in " + containerTitle;
        }

        String key = how.substring("taken from ".length());
        if (key.equals(SHULKER_BOXES)) {
            return "Shulker box";
        }

        if (key.equals(ENDER_CHEST)) {
            return "Ender chest";
        }

        String name = containerTitle.isEmpty() ? "Container" : containerTitle;
        int at = key.indexOf('@');
        return at < 0 ? name : name + " at " + key.substring(at + 1).replace(",", ", ");
    }

    public long earnedBetween(long from, long to) {
        return lastPlayer == null ? 0L : CurrencyLedger.earnedBetween(lastPlayer.getUUID(), this.tally, from, to);
    }

    public List<CurrencyLedger.Entry> history() {
        return lastPlayer == null ? List.of() : CurrencyLedger.history(lastPlayer.getUUID(), this.tally);
    }

    /** Takes up to {@code amount} from a pool of currency expected back, returning how much was recovered. */
    private static long recover(ArrayDeque<long[]> pool, long amount, long now) {
        long recovered = 0L;
        Iterator<long[]> it = pool.iterator();
        while (it.hasNext() && recovered < amount) {
            long[] drop = it.next();
            if (now > drop[0]) {
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

    /** A server menu or entity rather than a chest, the ender chest or a shulker box. */
    private static boolean isMenuKey(String key) {
        return key.startsWith("menu:") || key.startsWith("entity:");
    }

    /**
     * Names the container a menu belongs to, so deposits can be matched to later withdrawals. Falls back to the
     * menu's title for server menus that aren't attached to a block.
     */
    private static String containerKey(Minecraft minecraft, AbstractContainerMenu menu) {
        Screen screen = minecraft.gui.screen();
        if (menu instanceof ShulkerBoxMenu) {
            return SHULKER_BOXES;
        }

        if (isEnderChest(screen, menu)) {
            return ENDER_CHEST;
        }

        ClientLevel level = minecraft.level;
        if (level != null && minecraft.hitResult instanceof BlockHitResult hit && level.getBlockEntity(hit.getBlockPos()) instanceof Container) {
            BlockPos pos = hit.getBlockPos();
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
                BlockPos other = pos.relative(ChestBlock.getConnectedDirection(state));
                pos = other.asLong() < pos.asLong() ? other : pos;
            }

            return level.dimension() + "@" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
        }

        if (minecraft.hitResult instanceof EntityHitResult hit) {
            return "entity:" + hit.getEntity().getUUID();
        }

        return "menu:" + (screen == null ? "unknown" : screen.getTitle().getString());
    }

    /**
     * Currency in the open menu's own slots, such as a chest or trader. The player's inventory and crafting grid count
     * as held instead, and a crafting result is only a preview of what the grid will turn into, so neither is here.
     */
    private long containerValue(AbstractContainerMenu menu) {
        long total = 0L;
        for (Slot slot : menu.slots) {
            if (!(slot.container instanceof Inventory) && !(slot.container instanceof CraftingContainer)
                && !(slot instanceof ResultSlot) && !(slot.container instanceof ResultContainer)) {
                total += value(slot.getItem());
            }
        }

        return total;
    }

    /** The amount in a stack, including anything stored inside it such as a bundle or shulker box. */
    private long value(ItemStack stack) {
        long unit = this.tally.unitValue(stack);
        return unit > 0L ? unit * stack.getCount() : nestedValue(stack);
    }

    private long nestedValue(ItemStack stack) {
        return nestedValue(stack, 0);
    }

    private long nestedValue(ItemStack stack, int depth) {
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

    private long valueAt(ItemStack stack, int depth) {
        long unit = this.tally.unitValue(stack);
        return unit > 0L ? unit * stack.getCount() : nestedValue(stack, depth);
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
