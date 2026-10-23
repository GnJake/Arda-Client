package com.gnjake.ardaclient.wheel;

import com.gnjake.ardaclient.ServerCommands;
import com.gnjake.ardaclient.config.ArdaStore;
import com.gnjake.ardaclient.encyclopedia.EncyclopediaScreen;
import com.gnjake.ardaclient.gui.ArdaPanelScreen;
import com.gnjake.ardaclient.gui.ArdaTheme;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class QuickCommandsScreen extends ArdaPanelScreen {
    private static final int TILE_HEIGHT = 42;
    private static final int DELETE_WIDTH = 18;
    private static final int EDIT_WIDTH = 28;
    private int page;
    private int columns;
    private int rows;
    private int tileWidth;

    public QuickCommandsScreen(Screen parent) {
        super("Arda Commands", parent);
    }

    @Override
    protected void init() {
        this.layout(426, 220);
        this.columns = this.pw >= 320 ? 2 : 1;
        this.tileWidth = (this.pw - 32 - (this.columns - 1) * 8) / this.columns;
        this.rows = Math.max(1, (this.ph - 102) / 49);
        this.page = Math.min(this.page, this.maxPage());
        int actionWidth = (this.pw - 27) / 2;
        this.button("New Command", this.px + 10, this.py + 31, actionWidth, () -> this.minecraft.gui.setScreen(new QuickCommandEditScreen(this, null)));
        this.button("Encyclopedia", this.px + 17 + actionWidth, this.py + 31, actionWidth, () -> this.minecraft.gui.setScreen(new EncyclopediaScreen(this)));
        this.button("<", this.px + this.pw - 67, this.py + this.ph - 27, 26, () -> this.page = Math.max(0, this.page - 1));
        this.button(">", this.px + this.pw - 37, this.py + this.ph - 27, 26, () -> this.page = Math.min(this.maxPage(), this.page + 1));
    }

    private int perPage() {
        return this.columns * this.rows;
    }

    private int maxPage() {
        return Math.max(0, (ArdaStore.data.commands.size() - 1) / this.perPage());
    }

    private int tileX(int column) {
        return this.px + 12 + column * (this.tileWidth + 8);
    }

    private int tileY(int row) {
        return this.py + 61 + row * 49;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        List<ArdaStore.QuickCommand> commands = ArdaStore.data.commands;
        this.page = Math.min(this.page, this.maxPage());

        for (int slot = 0; slot < this.perPage(); slot++) {
            int index = this.page * this.perPage() + slot;
            if (index >= commands.size()) {
                break;
            }

            int x = this.tileX(slot % this.columns);
            int y = this.tileY(slot / this.columns);
            ArdaStore.QuickCommand command = commands.get(index);
            boolean hover = mouseX >= x && mouseX < x + this.tileWidth && mouseY >= y && mouseY < y + TILE_HEIGHT;
            ArdaTheme.row(graphics, x, y, this.tileWidth, TILE_HEIGHT, hover, false);
            graphics.outline(x, y, this.tileWidth, TILE_HEIGHT, hover ? ArdaTheme.accent() : ArdaTheme.BORDER);
            int textX = x + 7;
            Item icon = this.icon(command.icon);
            if (icon != null) {
                graphics.item(new ItemStack(icon), x + 5, y + 11);
                textX += 19;
            }

            int textWidth = Math.max(25, x + this.tileWidth - 46 - textX);
            graphics.text(this.font, this.font.plainSubstrByWidth(command.name, textWidth), textX, y + 8, ArdaTheme.TEXT);
            graphics.text(this.font, this.font.plainSubstrByWidth(command.command, textWidth), textX, y + 23, ArdaTheme.MUTED);
            TileAction hovered = hover ? this.actionAt(index, mouseX) : TileAction.RUN;
            int deleteX = x + this.tileWidth - DELETE_WIDTH;
            int editX = deleteX - EDIT_WIDTH;
            this.zone(graphics, editX, y, EDIT_WIDTH, "Edit", hovered == TileAction.EDIT);
            this.zone(graphics, deleteX, y, DELETE_WIDTH, "X", hovered == TileAction.DELETE);
        }

        if (commands.isEmpty()) {
            graphics.centeredText(this.font, "No commands yet. Add a shortcut above.", this.px + this.pw / 2, this.py + 94, ArdaTheme.MUTED);
        }

        graphics.text(this.font, "Click a box to run  •  Edit to change  •  X to delete", this.px + 13, this.py + this.ph - 23, ArdaTheme.MUTED);
        String pages = this.page + 1 + "/" + (this.maxPage() + 1);
        graphics.text(this.font, pages, this.px + this.pw - 96, this.py + this.ph - 22, ArdaTheme.MUTED);
    }

    /** One of the Edit and X areas at the right end of a tile. */
    private void zone(GuiGraphicsExtractor graphics, int x, int y, int width, String label, boolean hover) {
        if (hover) {
            graphics.fill(x, y + 1, x + width - 1, y + TILE_HEIGHT - 1, ArdaTheme.active());
        }

        graphics.fill(x, y + 8, x + 1, y + TILE_HEIGHT - 8, ArdaTheme.DIVIDER);
        graphics.centeredText(this.font, label, x + width / 2, y + (TILE_HEIGHT - 8) / 2, hover ? ArdaTheme.accentBright() : ArdaTheme.MUTED);
    }

    private Item icon(String id) {
        if (id != null && !id.isBlank()) {
            try {
                return BuiltInRegistries.ITEM.getValue(Identifier.parse(id));
            } catch (Exception ignored) {
                return null;
            }
        } else {
            return null;
        }
    }

    /** The index into the command list of the tile under the mouse, or -1. */
    private int tileAt(double mouseX, double mouseY) {
        for (int slot = 0; slot < this.perPage(); slot++) {
            int index = this.page * this.perPage() + slot;
            if (index >= ArdaStore.data.commands.size()) {
                break;
            }

            int x = this.tileX(slot % this.columns);
            int y = this.tileY(slot / this.columns);
            if (mouseX >= x && mouseX < x + this.tileWidth && mouseY >= y && mouseY < y + TILE_HEIGHT) {
                return index;
            }
        }

        return -1;
    }

    private TileAction actionAt(int index, double mouseX) {
        int slot = index - this.page * this.perPage();
        int right = this.tileX(slot % this.columns) + this.tileWidth;
        if (mouseX >= right - DELETE_WIDTH) {
            return TileAction.DELETE;
        }

        return mouseX >= right - DELETE_WIDTH - EDIT_WIDTH ? TileAction.EDIT : TileAction.RUN;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean twice) {
        if (super.mouseClicked(event, twice)) {
            return true;
        }

        int index = event.button() == 0 ? this.tileAt(event.x(), event.y()) : -1;
        if (index < 0) {
            return false;
        }

        ArdaStore.QuickCommand command = ArdaStore.data.commands.get(index);
        switch (this.actionAt(index, event.x())) {
            case RUN -> {
                this.minecraft.gui.setScreen(null);
                ServerCommands.run(command.command);
            }
            case EDIT -> this.minecraft.gui.setScreen(new QuickCommandEditScreen(this, command));
            case DELETE -> {
                // Saving renumbers the remaining commands.
                ArdaStore.data.commands.remove(index);
                ArdaStore.save();
                this.page = Math.min(this.page, this.maxPage());
            }
        }

        return true;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double dx, double dy) {
        this.page = Math.max(0, Math.min(this.maxPage(), this.page - (int)Math.signum(dy)));
        return true;
    }

    private enum TileAction {
        RUN,
        EDIT,
        DELETE
    }
}
