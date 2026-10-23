package com.gnjake.ardaclient.encyclopedia;

import com.gnjake.ardaclient.ArdaItems;
import com.gnjake.ardaclient.gui.ArdaButton;
import com.gnjake.ardaclient.gui.ArdaPanelScreen;
import com.gnjake.ardaclient.gui.ArdaTheme;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public final class ItemDraftScreen extends ArdaPanelScreen {
    private final ItemStack stack;
    private EditBox name;
    private EditBox lore;
    private CostInput cost;
    private ArdaButton uniqueButton;
    private ArdaButton saveButton;
    private boolean unique;

    public ItemDraftScreen(Screen parent, ItemStack stack) {
        super("Add Encyclopedia Item", parent);
        this.stack = stack.copy();
    }

    @Override
    protected void init() {
        this.layout(440, 278);
        this.name = this.textField(this.px + 12, this.py + 44, this.pw - 24, "Catalog name", this.stack.getHoverName().getString(), 256);
        int half = (this.pw - 30) / 2;
        this.cost = new CostInput(
            this.textField(this.px + 12, this.py + 83, half, "Bloodstones", "", 10),
            this.textField(this.px + 18 + half, this.py + 83, half, "Zircon", "", 10),
            this.textField(this.px + 12, this.py + 119, half, "Forged Tungsten", "", 10),
            this.textField(this.px + 18 + half, this.py + 119, half, "Black Gems", "", 10)
        );
        this.lore = this.textField(this.px + 12, this.py + 158, this.pw - 24, "Identity lore", String.join(" || ", ArdaItems.lore(this.stack)), 2048);
        this.uniqueButton = this.button("Type: Priced", this.px + 12, this.py + 194, half, () -> {
            this.unique = !this.unique;
            this.refresh();
        });
        this.saveButton = this.button("Save Draft", this.px + 18 + half, this.py + 194, half, this::save);
        this.button("Cancel", this.px + 12, this.py + 239, this.pw - 24, this::onClose);
        this.name.setResponder(s -> this.refresh());
        this.cost.onChange(this::refresh);
        this.refresh();
    }

    private void refresh() {
        if (this.uniqueButton != null) {
            this.uniqueButton.setMessage(Component.literal(this.unique ? "Type: Unique" : "Type: Priced"));
            this.cost.setActive(!this.unique);
            this.saveButton.active = !this.name.getValue().trim().isEmpty() && (this.unique || this.cost.read() != null);
        }
    }

    private List<String> identity() {
        String raw = this.lore.getValue().trim();
        return raw.isEmpty() ? List.of() : Arrays.stream(raw.split("\\s*\\|\\|\\s*")).map(String::trim).filter(s -> !s.isEmpty()).distinct().toList();
    }

    private void save() {
        PriceBook.Cost amount = this.cost.read();
        if (!this.name.getValue().trim().isEmpty() && (this.unique || amount != null)) {
            CatalogDrafts.add(this.stack, this.name.getValue().trim(), this.identity(), this.unique, amount);
            this.minecraft.gui.setScreen(this.parent);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float dt) {
        super.extractRenderState(g, mx, my, dt);
        g.item(this.stack, this.px + this.pw - 36, this.py + 27);
        g.text(this.font, "Capture the base item and its exact trader cost.", this.px + 12, this.py + 29, ArdaTheme.MUTED);
        this.label(g, "Name", this.py + 38);
        this.label(g, "Mixed cost — enter any combination", this.py + 74);
        this.label(g, "Identity lore — separate lines with ||", this.py + 149);
        g.pose().pushMatrix();
        g.pose().translate(this.px + 12, this.py + 221);
        g.pose().scale(0.68F);
        g.text(this.font, ArdaItems.id(this.stack) + "  •  saved to config/arda-client/encyclopedia-drafts.json", 0, 0, ArdaTheme.MUTED);
        g.pose().popMatrix();
    }

    private void label(GuiGraphicsExtractor g, String text, int y) {
        g.pose().pushMatrix();
        g.pose().translate(this.px + 13, y);
        g.pose().scale(0.65F);
        g.text(this.font, text, 0, 0, ArdaTheme.ACCENT);
        g.pose().popMatrix();
    }
}
