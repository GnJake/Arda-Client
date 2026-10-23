package com.gnjake.ardaclient.hud;

import com.gnjake.ardaclient.config.ArdaStore;
import com.gnjake.ardaclient.dungeon.DungeonTracker;
import com.gnjake.ardaclient.gui.ArdaButton;
import com.gnjake.ardaclient.gui.ArdaPanelScreen;
import com.gnjake.ardaclient.gui.ArdaTheme;
import java.util.Arrays;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;

public final class HudSettingsScreen extends ArdaPanelScreen {
    private static final int CELL_HEIGHT = 19;
    private static final int CELL_GAP = 2;
    private static final int GRID_ROWS = (DungeonTracker.TIERS + 1) / 2;
    private int cellWidth;
    private int gridTop;

    public HudSettingsScreen(Screen parent) {
        super("HUD Settings", parent);
    }

    @Override
    protected void init() {
        this.layout(424, 262);
        this.cellWidth = (this.pw - 28) / 2;
        int left = this.px + 10;
        int dungeonRow = this.py + 42;
        this.gridTop = dungeonRow + 26;
        int currencyRow = this.gridTop + GRID_ROWS * (CELL_HEIGHT + CELL_GAP) + 22;
        int x = this.toggle(left, dungeonRow, "Dungeons", () -> ArdaStore.data.dungeonHud, value -> ArdaStore.data.dungeonHud = value);
        x = this.scaleControls(x + 6, dungeonRow, () -> ArdaStore.data.dungeonScale, value -> ArdaStore.data.dungeonScale = value);
        this.button("Show All", x + 6, dungeonRow, 62, () -> this.setAll(true));
        this.button("Hide All", x + 74, dungeonRow, 62, () -> this.setAll(false));
        x = this.toggle(left, currencyRow, "Currency", () -> ArdaStore.data.currencyHud, value -> ArdaStore.data.currencyHud = value);
        x = this.scaleControls(x + 6, currencyRow, () -> ArdaStore.data.currencyScale, value -> ArdaStore.data.currencyScale = value);
        this.button("Move HUD", x + 6, currencyRow, 130, () -> this.minecraft.gui.setScreen(new HudPlacementScreen(this)));
    }

    private int toggle(int x, int y, String name, BooleanSupplier get, Consumer<Boolean> set) {
        this.button(name + ": " + (get.getAsBoolean() ? "On" : "Off"), x, y, 96, () -> {
            set.accept(!get.getAsBoolean());
            this.refresh();
        });
        return x + 96;
    }

    private int scaleControls(int x, int y, DoubleSupplier get, DoubleConsumer set) {
        this.button("-", x, y, 20, () -> {
            set.accept(ArdaStore.scale(get.getAsDouble() - 0.05));
            this.refresh();
        });
        ArdaButton label = this.button("Scale " + Math.round(get.getAsDouble() * 100.0) + "%", x + 22, y, 76, () -> {
        });
        label.active = false;
        this.button("+", x + 100, y, 20, () -> {
            set.accept(ArdaStore.scale(get.getAsDouble() + 0.05));
            this.refresh();
        });
        return x + 120;
    }

    private void refresh() {
        ArdaStore.save();
        this.minecraft.gui.setScreen(new HudSettingsScreen(this.parent));
    }

    private void setAll(boolean shown) {
        Arrays.fill(ArdaStore.data.shownDungeons, shown);
        ArdaStore.save();
    }

    private int cellX(int tier) {
        return this.px + 10 + (tier - 1) / GRID_ROWS * (this.cellWidth + 8);
    }

    private int cellY(int tier) {
        return this.gridTop + (tier - 1) % GRID_ROWS * (CELL_HEIGHT + CELL_GAP);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.text(this.font, "Dungeon panel", this.px + 10, this.py + 32, ArdaTheme.MUTED);
        graphics.text(this.font, "Currency counter", this.px + 10, this.gridTop + GRID_ROWS * (CELL_HEIGHT + CELL_GAP) + 12, ArdaTheme.MUTED);

        for (int tier = 1; tier <= DungeonTracker.TIERS; tier++) {
            int x = this.cellX(tier);
            int y = this.cellY(tier);
            boolean shown = ArdaStore.data.shownDungeons[tier - 1];
            boolean hover = mouseX >= x && mouseX < x + this.cellWidth && mouseY >= y && mouseY < y + CELL_HEIGHT;
            ArdaTheme.row(graphics, x, y, this.cellWidth, CELL_HEIGHT, hover, shown);
            graphics.outline(x + 6, y + 5, 9, 9, hover ? ArdaTheme.ACCENT_BRIGHT : ArdaTheme.BORDER);
            if (shown) {
                graphics.fill(x + 8, y + 7, x + 13, y + 12, ArdaTheme.ACCENT);
            }

            String label = "Tier " + tier + " · " + DungeonTracker.NAMES[tier - 1];
            graphics.text(this.font, this.font.plainSubstrByWidth(label, this.cellWidth - 26), x + 21, y + 6, shown ? ArdaTheme.TEXT : ArdaTheme.MUTED);
        }

        graphics.text(this.font, "Set the Toggle Dungeon HUD key in Minecraft Controls", this.px + 10, this.py + this.ph - 13, ArdaTheme.DIM);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean twice) {
        if (super.mouseClicked(event, twice)) {
            return true;
        } else if (event.button() != 0) {
            return false;
        } else {
            for (int tier = 1; tier <= DungeonTracker.TIERS; tier++) {
                int x = this.cellX(tier);
                int y = this.cellY(tier);
                if (event.x() >= x && event.x() < x + this.cellWidth && event.y() >= y && event.y() < y + CELL_HEIGHT) {
                    ArdaStore.data.shownDungeons[tier - 1] = !ArdaStore.data.shownDungeons[tier - 1];
                    ArdaStore.save();
                    return true;
                }
            }

            return false;
        }
    }
}
