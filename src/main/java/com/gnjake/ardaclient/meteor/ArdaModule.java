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
