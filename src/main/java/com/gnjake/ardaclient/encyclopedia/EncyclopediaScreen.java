package com.gnjake.ardaclient.encyclopedia;

import com.gnjake.ardaclient.gui.ArdaButton;
import com.gnjake.ardaclient.gui.ArdaPanelScreen;
import com.gnjake.ardaclient.gui.ArdaTheme;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Searchable list of item prices. Clicking a local draft opens it for adding upgrade prices. */
public final class EncyclopediaScreen extends ArdaPanelScreen {
    private static final int ROW_HEIGHT = 34;
    private static final int LIST_TOP = 72;
    private static final int LIST_BOTTOM_MARGIN = 34;

    private final List<PriceBook.Entry> shown = new ArrayList<>();
    private EditBox search;
    private int scroll;

    public EncyclopediaScreen(Screen parent) {
        super("Arda Encyclopedia", parent);
    }

    @Override
    protected void init() {
        this.layout(410, 360);
        this.search = this.addRenderableWidget(new EditBox(this.font, this.px + 9, this.py + 29, this.pw - 18, 20, Component.literal("Search encyclopedia")));
        this.search.setHint(Component.literal("Search item name or ID..."));
        this.search.setResponder(s -> this.filter());
        this.filter();
        this.setInitialFocus(this.search);
        InventoryWorth.calculate(this.minecraft);
        this.addRenderableWidget(new ArdaButton("Add Held Item", this.px + 9, this.py + this.ph - 27, this.pw - 18, 19, () -> {
            ItemStack stack = this.minecraft.player == null ? ItemStack.EMPTY : this.minecraft.player.getMainHandItem();
            if (!stack.isEmpty()) {
                this.minecraft.gui.setScreen(new ItemDraftScreen(this, stack));
            }
        }));
    }

    private void filter() {
        String query = this.search == null ? "" : this.search.getValue().trim().toLowerCase(Locale.ROOT);
        this.shown.clear();

        for (PriceBook.Entry entry : PriceCatalog.book.entries) {
            String text = String.join(" ",
                entry.name == null ? "" : entry.name,
                entry.itemId == null ? "" : entry.itemId,
                entry.serverItemType == null ? "" : entry.serverItemType,
                String.join(" ", entry.loreContains)
            ).toLowerCase(Locale.ROOT);
            if (query.isEmpty() || text.contains(query)) {
                this.shown.add(entry);
            }
        }

        this.shown.sort(Comparator.comparing(e -> e.name == null ? e.itemId : e.name, String.CASE_INSENSITIVE_ORDER));
        this.scroll = 0;
    }

    private int rows() {
        return Math.max(1, (this.ph - 117) / ROW_HEIGHT);
    }

    private int maxScroll() {
        return Math.max(0, this.shown.size() - this.rows());
    }

    private static String priceText(PriceBook.Entry entry) {
        String value;
        if (entry.unique) {
            value = "UNIQUE";
        } else if (entry.cost != null && !entry.cost.empty()) {
            value = entry.cost.display() + (entry.tradeAmount > 1 ? " for " + entry.tradeAmount : "");
        } else {
            value = entry.unitPrice == null ? "No currency price" : PriceBook.gems(entry.unitPrice);
        }

        if (entry.requirements != null && !entry.requirements.isEmpty()) {
            value = value
                + (entry.cost != null && !entry.cost.empty() ? " + " : "")
                + entry.requirements.stream().map(PriceBook.Requirement::display).collect(Collectors.joining(" + "));
        }

        int upgradeRules = entry.upgrades.size() + entry.levelUpgrades.size();
        if (upgradeRules > 0) {
            value = value + "  •  " + upgradeRules + " upgrade rules";
        }

        return value;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float dt) {
        super.extractRenderState(g, mx, my, dt);
        this.scroll = Math.max(0, Math.min(this.maxScroll(), this.scroll));
        String count = this.shown.size() == PriceCatalog.book.entries.size()
            ? this.shown.size() + (this.shown.size() == 1 ? " entry" : " entries")
            : this.shown.size() + " of " + PriceCatalog.book.entries.size();
        ArdaTheme.accentText(g, this.font, this.font.plainSubstrByWidth(InventoryWorth.text(), Math.max(60, this.pw - 110)), this.px + 10, this.py + 57);
        g.text(this.font, count, this.px + this.pw - 10 - this.font.width(count), this.py + 57, ArdaTheme.MUTED);
        int top = this.py + LIST_TOP;
        int bottom = this.py + this.ph - LIST_BOTTOM_MARGIN;
        g.enableScissor(this.px + 4, top, this.px + this.pw - 4, bottom);

        for (int row = 0; row < this.rows() && row + this.scroll < this.shown.size(); row++) {
            PriceBook.Entry entry = this.shown.get(row + this.scroll);
            int ry = top + row * ROW_HEIGHT;
            boolean hover = mx >= this.px + 7 && mx < this.px + this.pw - 7 && my >= ry && my < ry + 31;
            ArdaTheme.row(g, this.px + 7, ry, this.pw - 14, 31, hover, false);
            ItemStack model = PriceCatalog.displayStack(entry);
            if (!model.isEmpty()) {
                g.item(model, this.px + 12, ry + 7);
            }

            String name = entry.name != null && !entry.name.isBlank() ? entry.name : entry.itemId;
            if (CatalogDrafts.isDraft(entry)) {
                name = name + "  [LOCAL]";
            }

            g.text(this.font, this.font.plainSubstrByWidth(name, this.pw - 50), this.px + 34, ry + 5, entry.unique ? ArdaTheme.UNIQUE : ArdaTheme.TEXT);
            int valueColor = entry.unique ? ArdaTheme.UNIQUE : (entry.unitPrice == null ? ArdaTheme.MUTED : ArdaTheme.accentBright());
            g.pose().pushMatrix();
            g.pose().translate(this.px + 34, ry + 18);
            g.pose().scale(0.68F);
            g.text(this.font, this.font.plainSubstrByWidth(priceText(entry), (int)((this.pw - 50) / 0.68)), 0, 0, valueColor);
            g.pose().popMatrix();
        }

        if (this.shown.isEmpty()) {
            String empty = PriceCatalog.book.entries.isEmpty() ? "No entries are included in this build yet." : "No matching entries.";
            g.centeredText(this.font, empty, this.px + this.pw / 2, top + 18, ArdaTheme.MUTED);
        }

        g.disableScissor();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean twice) {
        if (super.mouseClicked(e, twice)) {
            return true;
        }

        boolean inList = e.x() >= this.px + 7 && e.x() < this.px + this.pw - 7 && e.y() >= this.py + LIST_TOP && e.y() < this.py + this.ph - LIST_BOTTOM_MARGIN;
        if (e.button() != 0 || !inList) {
            return false;
        }

        int index = this.scroll + (int)(e.y() - (this.py + LIST_TOP)) / ROW_HEIGHT;
        if (index < 0 || index >= this.shown.size() || !CatalogDrafts.isDraft(this.shown.get(index))) {
            return false;
        }

        ItemStack held = this.minecraft.player == null ? ItemStack.EMPTY : this.minecraft.player.getMainHandItem();
        this.minecraft.gui.setScreen(new DraftEntryScreen(this, this.shown.get(index), held));
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        this.scroll = Math.max(0, Math.min(this.maxScroll(), this.scroll - (int)Math.signum(dy) * 3));
        return true;
    }
}
