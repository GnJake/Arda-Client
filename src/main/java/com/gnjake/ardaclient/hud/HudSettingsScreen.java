package com.gnjake.ardaclient.hud;

import com.gnjake.ardaclient.config.ArdaStore;
import com.gnjake.ardaclient.currency.CurrencyTracker;
import com.gnjake.ardaclient.dungeon.DungeonTracker;
import com.gnjake.ardaclient.gui.ArdaButton;
import com.gnjake.ardaclient.gui.ArdaPanelScreen;
import com.gnjake.ardaclient.gui.ArdaSlider;
import com.gnjake.ardaclient.gui.ArdaTheme;
import java.util.Arrays;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;

/** Toggles, scale, position, color and transparency for the HUD panels. Kept short enough for GUI scale 4. */
public final class HudSettingsScreen extends ArdaPanelScreen {
    private static final int ROW_STEP = 24;
    private static final int GRID_COLUMNS = 3;
    private static final int GRID_ROWS = (DungeonTracker.TIERS + GRID_COLUMNS - 1) / GRID_COLUMNS;
    private static final int CELL_HEIGHT = 13;
    private static final int CELL_GAP = 1;
    private static final int SWATCH = 14;
    private static final int SWATCH_GAP = 3;

    private int left;
    private int gridTop;
    private int cellWidth;
    private int swatchTop;
    private boolean confirmReset;
    private EditBox hex;

    public HudSettingsScreen(Screen parent) {
        super("HUD Settings", parent);
    }

    @Override
    protected void init() {
        this.layout(424, 192);
        this.left = this.px + 10;
        int width = this.pw - 20;
        this.cellWidth = (width - (GRID_COLUMNS - 1) * 4) / GRID_COLUMNS;

        int y = this.py + 31;
        int x = this.toggle(this.left, y, "Dungeons", () -> ArdaStore.data.dungeonHud, value -> ArdaStore.data.dungeonHud = value);
        x = this.scaleControls(x + 6, y, () -> ArdaStore.data.dungeonScale, value -> ArdaStore.data.dungeonScale = value);
        this.button("Show All", x + 6, y, 62, () -> this.setAll(true));
        this.button("Hide All", x + 74, y, 62, () -> this.setAll(false));

        this.gridTop = y + ROW_STEP;
        y = this.gridTop + GRID_ROWS * (CELL_HEIGHT + CELL_GAP) + 4;
        x = this.toggle(this.left, y, "Currency", () -> ArdaStore.data.currencyHud, value -> ArdaStore.data.currencyHud = value);
        x = this.scaleControls(x + 6, y, () -> ArdaStore.data.currencyScale, value -> ArdaStore.data.currencyScale = value);
        this.toggle(x + 6, y, "Ender Chest", 136, () -> ArdaStore.data.currencyEnderChest, value -> ArdaStore.data.currencyEnderChest = value);

        y += ROW_STEP;
        x = this.toggle(this.left, y, "Tally", () -> ArdaStore.data.tallyHud, value -> ArdaStore.data.tallyHud = value);
        ArdaButton reset = this.button("Reset Tally", x + 6, y, 120, null);
        reset.setAction(() -> {
            if (this.confirmReset) {
                CurrencyTracker.resetTally();
                this.confirmReset = false;
                reset.setMessage("Reset Tally");
            } else {
                this.confirmReset = true;
                reset.setMessage("Click to confirm");
            }
        });
        this.button("Move HUD", x + 132, y, 136, () -> this.minecraft.gui.setScreen(new HudPlacementScreen(this)));

        y += ROW_STEP;
        this.swatchTop = y + 3;
        int hexX = this.left + 34 + ArdaTheme.PRESETS.length * (SWATCH + SWATCH_GAP) + 4;
        this.hex = this.textField(hexX, y, 60, "Hex", String.format(Locale.ROOT, "%06X", ArdaStore.data.accentColor), 7);
        this.hex.setResponder(this::applyHex);
        int sliderX = hexX + 66;
        this.addRenderableWidget(new ArdaSlider(sliderX, y, this.px + this.pw - 10 - sliderX, 20, ArdaStore.data.hudOpacity,
            value -> "Background " + Math.round(value * 100.0) + "%", value -> ArdaStore.data.hudOpacity = value));
    }

    private int toggle(int x, int y, String name, BooleanSupplier get, Consumer<Boolean> set) {
        return this.toggle(x, y, name, 96, get, set);
    }

    private int toggle(int x, int y, String name, int width, BooleanSupplier get, Consumer<Boolean> set) {
        ArdaButton button = this.button(label(name, get), x, y, width, null);
        button.setAction(() -> {
            set.accept(!get.getAsBoolean());
            button.setMessage(label(name, get));
            ArdaStore.save();
        });
        return x + width;
    }

    private static String label(String name, BooleanSupplier get) {
        return name + ": " + (get.getAsBoolean() ? "On" : "Off");
    }

    private int scaleControls(int x, int y, DoubleSupplier get, DoubleConsumer set) {
        ArdaButton label = this.button(scaleLabel(get), x + 22, y, 76, () -> {
        });
        label.active = false;
        this.button("-", x, y, 20, () -> {
            set.accept(ArdaStore.scale(get.getAsDouble() - 0.05));
            label.setMessage(scaleLabel(get));
            ArdaStore.save();
        });
        this.button("+", x + 100, y, 20, () -> {
            set.accept(ArdaStore.scale(get.getAsDouble() + 0.05));
            label.setMessage(scaleLabel(get));
            ArdaStore.save();
        });
        return x + 120;
    }

    private static String scaleLabel(DoubleSupplier get) {
        return "Scale " + Math.round(get.getAsDouble() * 100.0) + "%";
    }

    private void setAll(boolean shown) {
        Arrays.fill(ArdaStore.data.shownDungeons, shown);
        ArdaStore.save();
    }

    private void applyHex(String text) {
        String digits = text.strip().replace("#", "");
        if (digits.matches("[0-9a-fA-F]{6}")) {
            ArdaStore.data.accentColor = Integer.parseInt(digits, 16);
        }
    }

    private void setAccent(int rgb) {
        ArdaStore.data.accentColor = rgb;
        this.hex.setValue(String.format(Locale.ROOT, "%06X", rgb));
        ArdaStore.save();
    }

    private int cellX(int tier) {
        return this.left + (tier - 1) % GRID_COLUMNS * (this.cellWidth + 4);
    }

    private int cellY(int tier) {
        return this.gridTop + (tier - 1) / GRID_COLUMNS * (CELL_HEIGHT + CELL_GAP);
    }

    private int swatchX(int index) {
        return this.left + 34 + index * (SWATCH + SWATCH_GAP);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        for (int tier = 1; tier <= DungeonTracker.TIERS; tier++) {
            int x = this.cellX(tier);
            int y = this.cellY(tier);
            boolean shown = ArdaStore.data.shownDungeons[tier - 1];
            boolean hover = mouseX >= x && mouseX < x + this.cellWidth && mouseY >= y && mouseY < y + CELL_HEIGHT;
            ArdaTheme.row(graphics, x, y, this.cellWidth, CELL_HEIGHT, hover, shown);
            String number = String.valueOf(tier);
            graphics.text(this.font, number, x + 18 - this.font.width(number), y + 3, shown ? ArdaTheme.accentBright() : ArdaTheme.DIM);
            String name = this.font.plainSubstrByWidth(DungeonTracker.NAMES[tier - 1], this.cellWidth - 26);
            graphics.text(this.font, name, x + 23, y + 3, shown ? ArdaTheme.TEXT : ArdaTheme.MUTED);
        }

        graphics.text(this.font, "Color", this.left, this.swatchTop + 3, ArdaTheme.MUTED);
        for (int i = 0; i < ArdaTheme.PRESETS.length; i++) {
            int x = this.swatchX(i);
            boolean selected = ArdaTheme.PRESETS[i] == ArdaStore.data.accentColor;
            boolean hover = mouseX >= x && mouseX < x + SWATCH && mouseY >= this.swatchTop && mouseY < this.swatchTop + SWATCH;
            graphics.fill(x, this.swatchTop, x + SWATCH, this.swatchTop + SWATCH, 0xFF000000 | ArdaTheme.PRESETS[i]);
            if (selected || hover) {
                graphics.outline(x - 1, this.swatchTop - 1, SWATCH + 2, SWATCH + 2, selected ? ArdaTheme.TEXT : ArdaTheme.MUTED);
            }
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean twice) {
        if (super.mouseClicked(event, twice)) {
            return true;
        }

        if (event.button() != 0) {
            return false;
        }

        for (int i = 0; i < ArdaTheme.PRESETS.length; i++) {
            int x = this.swatchX(i);
            if (event.x() >= x && event.x() < x + SWATCH && event.y() >= this.swatchTop && event.y() < this.swatchTop + SWATCH) {
                this.setAccent(ArdaTheme.PRESETS[i]);
                return true;
            }
        }

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

    @Override
    public void onClose() {
        ArdaStore.save();
        super.onClose();
    }
}
