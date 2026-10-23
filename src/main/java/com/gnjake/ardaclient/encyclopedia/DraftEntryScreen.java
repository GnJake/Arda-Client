package com.gnjake.ardaclient.encyclopedia;

import com.gnjake.ardaclient.ArdaItems;
import com.gnjake.ardaclient.gui.ArdaButton;
import com.gnjake.ardaclient.gui.ArdaPanelScreen;
import com.gnjake.ardaclient.gui.ArdaTheme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.ItemStack;

public final class DraftEntryScreen extends ArdaPanelScreen {
    private final PriceBook.Entry entry;
    private final ItemStack held;
    private EditBox marker;
    private CostInput cost;
    private ArdaButton add;

    public DraftEntryScreen(Screen parent, PriceBook.Entry entry, ItemStack held) {
        super("Local Encyclopedia Draft", parent);
        this.entry = entry;
        this.held = held.copy();
    }

    @Override
    protected void init() {
        this.layout(430, 270);
        int half = (this.pw - 30) / 2;
        String suggestion = ArdaItems.lore(this.held).stream().filter(line -> this.entry.loreContains.stream().noneMatch(line::equals)).findFirst().orElse("");
        this.marker = this.textField(this.px + 12, this.py + 83, this.pw - 24, "Exact upgrade lore", suggestion, 512);
        this.cost = new CostInput(
            this.textField(this.px + 12, this.py + 122, half, "Bloodstones", "", 10),
            this.textField(this.px + 18 + half, this.py + 122, half, "Zircon", "", 10),
            this.textField(this.px + 12, this.py + 158, half, "Forged Tungsten", "", 10),
            this.textField(this.px + 18 + half, this.py + 158, half, "Black Gems", "", 10)
        );
        this.add = this.button("Add Upgrade", this.px + 12, this.py + 194, this.pw - 24, this::addUpgrade);
        this.button("Delete Draft", this.px + 12, this.py + 230, half, () -> {
            CatalogDrafts.remove(this.entry);
            this.minecraft.gui.setScreen(this.parent);
        });
        this.button("Done", this.px + 18 + half, this.py + 230, half, this::onClose);
        this.marker.setResponder(s -> this.refresh());
        this.cost.onChange(this::refresh);
        this.refresh();
    }

    private void refresh() {
        if (this.add != null) {
            this.add.active = !this.marker.getValue().trim().isEmpty() && this.cost.read() != null && ArdaItems.id(this.held).equals(this.entry.itemId);
        }
    }

    private void addUpgrade() {
        PriceBook.Cost amount = this.cost.read();
        String text = this.marker.getValue().trim();
        if (!text.isEmpty() && amount != null && ArdaItems.id(this.held).equals(this.entry.itemId)) {
            CatalogDrafts.addUpgrade(this.entry, text, amount);
            this.minecraft.gui.setScreen(this.parent);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float dt) {
        super.extractRenderState(g, mx, my, dt);
        ItemStack model = PriceCatalog.displayStack(this.entry);
        if (!model.isEmpty()) {
            g.item(model, this.px + this.pw - 35, this.py + 30);
        }

        g.text(this.font, this.font.plainSubstrByWidth(this.entry.name, this.pw - 60), this.px + 12, this.py + 34, this.entry.unique ? ArdaTheme.UNIQUE : ArdaTheme.TEXT);
        String base = this.entry.unique
            ? "UNIQUE"
            : (
                this.entry.cost != null && !this.entry.cost.empty()
                    ? this.entry.cost.display()
                    : (this.entry.unitPrice == null ? "No currency price" : PriceBook.gems(this.entry.unitPrice))
            );
        g.text(
            this.font,
            base + "  •  " + (this.entry.upgrades.size() + this.entry.levelUpgrades.size()) + " upgrade rules",
            this.px + 12,
            this.py + 51,
            this.entry.unique ? ArdaTheme.UNIQUE : (this.entry.unitPrice == null ? ArdaTheme.MUTED : ArdaTheme.accentBright())
        );
        this.label(
            g,
            ArdaItems.id(this.held).equals(this.entry.itemId)
                ? "Held item captured — enter its distinguishing upgrade lore"
                : "Hold the upgraded item before opening this draft",
            this.py + 69,
            ArdaTheme.MUTED
        );
        this.label(g, "Upgrade cost — enter any combination", this.py + 113, ArdaTheme.accent());
    }

    private void label(GuiGraphicsExtractor g, String text, int y, int color) {
        g.pose().pushMatrix();
        g.pose().translate(this.px + 12, y);
        g.pose().scale(0.65F);
        g.text(this.font, text, 0, 0, color);
        g.pose().popMatrix();
    }
}
