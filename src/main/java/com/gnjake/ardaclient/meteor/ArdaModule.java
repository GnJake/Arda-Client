package com.gnjake.ardaclient.meteor;

import com.gnjake.ardaclient.config.ArdaStore;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * A Meteor module that switches one Arda Client feature on and off. Arda Client's own setting is the source of truth;
 * {@link #sync()} keeps the two in step whichever side the player changes.
 */
public final class ArdaModule extends Module {
    public static final Category CATEGORY = new Category("Arda Client", () -> new ItemStack(Items.CLOCK));

    private final BooleanSupplier get;
    private final Consumer<Boolean> set;
    private boolean lastActive;
    private boolean synced;

    private ArdaModule(String name, String description, BooleanSupplier get, Consumer<Boolean> set) {
        super(CATEGORY, name, description);
        this.get = get;
        this.set = set;
    }

    public static List<ArdaModule> create() {
        return List.of(
            new ArdaModule("dungeon-panel", "Arda's dungeon timers. Move and scale it in HUD Settings or Meteor's HUD editor.",
                () -> ArdaStore.data.dungeonHud, value -> ArdaStore.data.dungeonHud = value),
            new ArdaModule("currency-counter", "Your currency in Black Gems and Bloodstones.",
                () -> ArdaStore.data.currencyHud, value -> ArdaStore.data.currencyHud = value),
            new ArdaModule("currency-tally", "Currency earned over the last hour, 3 hours, 12 hours, day and week.",
                () -> ArdaStore.data.tallyHud, value -> ArdaStore.data.tallyHud = value),
            new ArdaModule("mithril-counter", "Your Mithril (Crude Mithril Ore).",
                () -> ArdaStore.data.mithrilHud, value -> ArdaStore.data.mithrilHud = value),
            new ArdaModule("mithril-tally", "Mithril earned over the last hour, 3 hours, 12 hours, day and week.",
                () -> ArdaStore.data.mithrilTallyHud, value -> ArdaStore.data.mithrilTallyHud = value),
            new ArdaModule("profit-estimate", "Estimated dungeon and skirmish profits in chat when you start or finish one.",
                () -> ArdaStore.data.profitChat, value -> ArdaStore.data.profitChat = value),
            new ArdaModule("towers-panel", "Who holds each tower and each clan's estimated points, during the Towers Event.",
                () -> ArdaStore.data.towersHud, value -> ArdaStore.data.towersHud = value),
            new ArdaModule("tunnel-pathfinder", "Finds and marks a walking path to the next tunnel spawner. Turn it off if it lags.",
                () -> ArdaStore.data.tunnelPath, value -> ArdaStore.data.tunnelPath = value),
            new ArdaModule("tunnel-xray", "Outlines logged tunnel spawners through walls and draws the tunnel path as highlighted blocks.",
                () -> ArdaStore.data.tunnelXray, value -> ArdaStore.data.tunnelXray = value),
            new ArdaModule("price-tooltips", "Encyclopedia prices in item tooltips.",
                () -> ArdaStore.data.pricingTooltips, value -> ArdaStore.data.pricingTooltips = value)
        );
    }

    /** Called every tick. A change made in Meteor is saved to Arda Client; a change made in Arda Client toggles the module. */
    public void sync() {
        boolean active = this.isActive();
        if (this.synced && active != this.lastActive) {
            this.set.accept(active);
            ArdaStore.save();
        } else if (active != this.get.getAsBoolean()) {
            this.toggle();
            active = this.isActive();
        }

        this.lastActive = active;
        this.synced = true;
    }
}
