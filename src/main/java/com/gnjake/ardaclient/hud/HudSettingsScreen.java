package com.gnjake.ardaclient.hud;

import com.gnjake.ardaclient.config.ArdaStore;
import com.gnjake.ardaclient.currency.CurrencyTracker;
import com.gnjake.ardaclient.dungeon.DungeonTracker;
import com.gnjake.ardaclient.gui.ArdaButton;
import com.gnjake.ardaclient.gui.ArdaPanelScreen;
import com.gnjake.ardaclient.gui.ArdaSlider;
import com.gnjake.ardaclient.gui.ArdaTheme;
import java.util.Arrays;
import java.util.List;
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
    private static final int ROW_STEP = 22;
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
    /** Which gradient color the swatches and hex box set: 0 is the main accent, then each further color. */
    private int editingColor;
    private int previewLeft;
    private int previewTop;
    private EditBox hex;

    public HudSettingsScreen(Screen parent) {
        super("HUD Settings", parent);
    }

    @Override
    protected void init() {
        this.layout(424, 228);
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
        x = this.toggle(this.left, y, "Mithril", () -> ArdaStore.data.mithrilHud, value -> ArdaStore.data.mithrilHud = value);
        x = this.scaleControls(x + 6, y, () -> ArdaStore.data.mithrilScale, value -> ArdaStore.data.mithrilScale = value);
        x = this.toggle(x + 6, y, "Mithril Tally", 100, () -> ArdaStore.data.mithrilTallyHud, value -> ArdaStore.data.mithrilTallyHud = value);
        this.resetButton(x + 6, y, this.px + this.pw - 16 - x, "Reset Mithril", CurrencyTracker.MITHRIL);

        y += ROW_STEP;
        x = this.toggle(this.left, y, "Tally", () -> ArdaStore.data.tallyHud, value -> ArdaStore.data.tallyHud = value);
        this.resetButton(x + 6, y, 90, "Reset Tally", CurrencyTracker.CURRENCY);
        this.button("Move HUD", x + 102, y, 80, () -> this.minecraft.gui.setScreen(new HudPlacementScreen(this)));
        ArdaButton towers = this.button(TowersHud.modeLabel(), x + 188, y, this.px + this.pw - 10 - x - 188, null);
        towers.setAction(() -> {
            TowersHud.cycleMode();
            towers.setMessage(TowersHud.modeLabel());
        });

        y += ROW_STEP;
        this.swatchTop = y + 3;
        int hexX = this.left + 34 + ArdaTheme.PRESETS.length * (SWATCH + SWATCH_GAP) + 4;
        this.hex = this.textField(hexX, y, 60, "Hex", String.format(Locale.ROOT, "%06X", this.editedColor()), 7);
        this.hex.setResponder(this::applyHex);
        int sliderX = hexX + 66;
        this.addRenderableWidget(new ArdaSlider(sliderX, y, this.px + this.pw - 10 - sliderX, 20, ArdaStore.data.hudOpacity,
            value -> "Background " + Math.round(value * 100.0) + "%", value -> ArdaStore.data.hudOpacity = value));

        y += ROW_STEP;
        x = this.toggle(this.left, y, "Gradient", () -> ArdaStore.data.accentGradient, value -> ArdaStore.data.accentGradient = value);
        ArdaButton editing = this.button(this.editingLabel(), x + 98, y, 108, null);
        ArdaButton count = this.button(countLabel(), x + 28, y, 68, () -> {
        });
        count.active = false;
        Runnable refresh = () -> {
            this.editingColor = Math.min(this.editingColor, ArdaStore.data.gradientColors.size());
            count.setMessage(countLabel());
            editing.setMessage(this.editingLabel());
            this.hex.setValue(String.format(Locale.ROOT, "%06X", this.editedColor()));
            ArdaStore.save();
        };
        this.button("-", x + 6, y, 20, () -> {
            if (ArdaStore.data.gradientColors.size() > 1) {
                ArdaStore.data.gradientColors.removeLast();
            }

            refresh.run();
        });
        this.button("+", x + 98 + 108 + 2, y, 20, () -> {
            List<Integer> colors = ArdaStore.data.gradientColors;
            if (colors.size() < ArdaStore.MAX_GRADIENT_COLORS - 1) {
                // A new color starts as the next preset after the last one, so it is visibly different.
                int last = colors.getLast();
                int index = 0;
                for (int i = 0; i < ArdaTheme.PRESETS.length; i++) {
                    if (ArdaTheme.PRESETS[i] == last) {
                        index = i + 1;
                    }
                }

                colors.add(ArdaTheme.PRESETS[index % ArdaTheme.PRESETS.length]);
                this.editingColor = colors.size();
            }

            refresh.run();
        });
        editing.setAction(() -> {
            this.editingColor = (this.editingColor + 1) % (ArdaStore.data.gradientColors.size() + 1);
            refresh.run();
        });
        this.previewLeft = x + 98 + 108 + 28;
        this.previewTop = y;
    }

    private static String countLabel() {
        return "Colors: " + (ArdaStore.data.gradientColors.size() + 1);
    }

    private String editingLabel() {
        return this.editingColor == 0 ? "Setting: Main" : "Setting: Color " + (this.editingColor + 1);
    }

    private int editedColor() {
        return this.editingColor == 0 ? ArdaStore.data.accentColor : ArdaStore.data.gradientColors.get(this.editingColor - 1);
    }

    /** A button that clears one tally, after a second click to confirm. */
    private void resetButton(int x, int y, int width, String label, CurrencyTracker tracker) {
        boolean[] confirming = {false};
        ArdaButton reset = this.button(label, x, y, width, null);
        reset.setAction(() -> {
            if (confirming[0]) {
                tracker.resetTally();
                reset.setMessage(label);
            } else {
                reset.setMessage("Confirm");
            }

            confirming[0] = !confirming[0];
        });
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
            this.setEditedColor(Integer.parseInt(digits, 16));
        }
    }

    private void setEditedColor(int rgb) {
        if (this.editingColor == 0) {
            ArdaStore.data.accentColor = rgb;
        } else {
            ArdaStore.data.gradientColors.set(this.editingColor - 1, rgb);
        }
    }

    private void setAccent(int rgb) {
        this.setEditedColor(rgb);
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
            graphics.text(this.font, number, x + 18 - this.font.width(number), y + 3, shown ? ArdaTheme.accentBrightAt((tier - 1) / (double)(DungeonTracker.TIERS - 1)) : ArdaTheme.DIM);
            String name = this.font.plainSubstrByWidth(DungeonTracker.NAMES[tier - 1], this.cellWidth - 26);
            graphics.text(this.font, name, x + 23, y + 3, shown ? ArdaTheme.TEXT : ArdaTheme.MUTED);
        }

        graphics.text(this.font, "Color", this.left, this.swatchTop + 3, ArdaTheme.MUTED);
        for (int i = 0; i < ArdaTheme.PRESETS.length; i++) {
            int x = this.swatchX(i);
            boolean selected = ArdaTheme.PRESETS[i] == this.editedColor();
            boolean hover = mouseX >= x && mouseX < x + SWATCH && mouseY >= this.swatchTop && mouseY < this.swatchTop + SWATCH;
            graphics.fill(x, this.swatchTop, x + SWATCH, this.swatchTop + SWATCH, 0xFF000000 | ArdaTheme.PRESETS[i]);
            if (selected || hover) {
                graphics.outline(x - 1, this.swatchTop - 1, SWATCH + 2, SWATCH + 2, selected ? ArdaTheme.TEXT : ArdaTheme.MUTED);
            }
        }

        // A preview of the accents: the gradient when it is on, the main color alone when it is off.
        int previewRight = this.px + this.pw - 10;
        graphics.outline(this.previewLeft, this.previewTop, previewRight - this.previewLeft, 20, ArdaTheme.BORDER);
        ArdaTheme.accentLine(graphics, this.previewLeft + 1, this.previewTop + 1, previewRight - 1, this.previewTop + 19);
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
