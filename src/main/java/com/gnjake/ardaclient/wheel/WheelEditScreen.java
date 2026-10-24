package com.gnjake.ardaclient.wheel;

import com.gnjake.ardaclient.config.ArdaStore;
import com.gnjake.ardaclient.gui.ArdaButton;
import com.gnjake.ardaclient.gui.ArdaPanelScreen;
import com.gnjake.ardaclient.gui.ArdaTheme;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;

/**
 * Edits the quick wheel on a preview of the wheel: click a slot to pick it, then choose what it holds. Also sets how
 * many slots the wheel has, its size, and whether it shows labels and icons.
 */
public final class WheelEditScreen extends ArdaPanelScreen {
    private static final double MIN_SIZE = 0.65;
    private static final double MAX_SIZE = 1.35;
    private static final int CONTROLS_WIDTH = 176;

    private int selected;
    private ArdaButton slotName;
    private ArdaButton slotCount;
    private int controlsLeft;

    public WheelEditScreen(Screen parent) {
        super("Edit Wheel", parent);
    }

    @Override
    protected void init() {
        this.layout(424, 224);
        this.selected = Math.min(this.selected, ArdaStore.data.wheel.size() - 1);
        this.controlsLeft = this.px + this.pw - 10 - CONTROLS_WIDTH;
        int x = this.controlsLeft;
        int y = this.py + 52;
        this.button("<", x, y, 20, () -> this.cycle(-1));
        this.slotName = this.button("", x + 22, y, CONTROLS_WIDTH - 44, () -> this.cycle(1));
        this.button(">", x + CONTROLS_WIDTH - 20, y, 20, () -> this.cycle(1));
        y += 24;
        this.button("Clear Slot", x, y, CONTROLS_WIDTH, () -> this.setSlot(WheelSlots.NONE));

        y += 32;
        this.slotCount = this.button("", x + 22, y, CONTROLS_WIDTH - 44, () -> {
        });
        this.slotCount.active = false;
        this.button("-", x, y, 20, () -> this.resizeWheel(-1));
        this.button("+", x + CONTROLS_WIDTH - 20, y, 20, () -> this.resizeWheel(1));

        y += 24;
        ArdaButton size = this.button(sizeLabel(), x + 22, y, CONTROLS_WIDTH - 44, () -> {
        });
        size.active = false;
        this.button("-", x, y, 20, () -> {
            this.scale(-0.05);
            size.setMessage(sizeLabel());
        });
        this.button("+", x + CONTROLS_WIDTH - 20, y, 20, () -> {
            this.scale(0.05);
            size.setMessage(sizeLabel());
        });

        y += 24;
        int half = (CONTROLS_WIDTH - 4) / 2;
        this.toggle(x, y, half, "Labels", () -> ArdaStore.data.settings.labels, value -> ArdaStore.data.settings.labels = value);
        this.toggle(x + half + 4, y, half, "Icons", () -> ArdaStore.data.settings.icons, value -> ArdaStore.data.settings.icons = value);

        y += 24;
        this.button("Reset Wheel", x, y, CONTROLS_WIDTH, () -> {
            ArdaStore.data.wheel = new ArrayList<>(WheelSlots.DEFAULTS);
            ArdaStore.save();
            this.selected = 0;
            this.refresh();
        });
        this.refresh();
    }

    private WheelRenderer.Layout wheelLayout() {
        int count = ArdaStore.data.wheel.size();
        int areaWidth = this.controlsLeft - this.px - 20;
        int areaHeight = this.ph - 35;
        int outer = Math.min(areaWidth, areaHeight) / 2;
        // Pick the scale that makes this wheel's outer edge fit the preview area.
        double unit = WheelRenderer.layout(0, 0, 1.0, count).outer();
        return WheelRenderer.layout(this.px + 10 + areaWidth / 2, this.py + 30 + areaHeight / 2, outer / unit, count);
    }

    private void cycle(int direction) {
        List<String> options = WheelSlots.options();
        int current = options.indexOf(ArdaStore.data.wheel.get(this.selected));
        this.setSlot(options.get(current < 0 ? 0 : Math.floorMod(current + direction, options.size())));
    }

    private void setSlot(String slot) {
        ArdaStore.data.wheel.set(this.selected, slot);
        ArdaStore.save();
        this.refresh();
    }

    /** Adds or removes a slot: the teleports go first, and growing the wheel brings back what was removed. */
    private void resizeWheel(int change) {
        List<String> wheel = ArdaStore.data.wheel;
        if (change > 0 && wheel.size() < WheelSlots.MAX_SLOTS) {
            WheelSlots.grow(wheel);
        } else if (change < 0 && wheel.size() > WheelSlots.MIN_SLOTS) {
            WheelSlots.shrink(wheel);
        }

        this.selected = Math.min(this.selected, wheel.size() - 1);
        ArdaStore.save();
        this.refresh();
    }

    private void refresh() {
        String label = WheelSlots.label(ArdaStore.data.wheel.get(this.selected));
        this.slotName.setMessage(this.font.plainSubstrByWidth(label, CONTROLS_WIDTH - 52));
        this.slotCount.setMessage("Slots: " + ArdaStore.data.wheel.size());
    }

    private void scale(double step) {
        double size = Math.round((ArdaStore.data.settings.scale + step) * 20.0) / 20.0;
        ArdaStore.data.settings.scale = Math.max(MIN_SIZE, Math.min(MAX_SIZE, size));
        ArdaStore.save();
    }

    private static String sizeLabel() {
        return "Wheel Size " + Math.round(ArdaStore.data.settings.scale * 100.0) + "%";
    }

    private void toggle(int x, int y, int width, String name, BooleanSupplier get, Consumer<Boolean> set) {
        ArdaButton button = this.button(name + ": " + (get.getAsBoolean() ? "On" : "Off"), x, y, width, null);
        button.setAction(() -> {
            set.accept(!get.getAsBoolean());
            button.setMessage(name + ": " + (get.getAsBoolean() ? "On" : "Off"));
            ArdaStore.save();
        });
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        List<WheelScreen.Section> sections = ArdaStore.data.wheel.stream().map(WheelSlots::section).toList();
        WheelRenderer.Layout layout = this.wheelLayout();
        WheelRenderer.draw(graphics, this.font, layout, sections, this.selected, 1.0, ArdaStore.data.settings.labels, ArdaStore.data.settings.icons);
        int hovered = layout.sectionAt(mouseX, mouseY);
        int shown = hovered >= 0 ? hovered : this.selected;
        WheelScreen.Section section = sections.get(shown);
        WheelRenderer.drawCenter(graphics, this.font, layout, section, "EMPTY");

        int x = this.controlsLeft;
        ArdaTheme.accentText(graphics, this.font, "Slot " + (this.selected + 1) + " of " + sections.size(), x, this.py + 36);
        graphics.text(this.font, "Click a slot on the wheel to edit it.", this.px + 10, this.py + this.ph - 12, ArdaTheme.DIM);
        graphics.text(this.font, "Wheel", x, this.py + 98, ArdaTheme.MUTED);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean twice) {
        if (super.mouseClicked(event, twice)) {
            return true;
        }

        int slot = event.button() == 0 ? this.wheelLayout().sectionAt(event.x(), event.y()) : -1;
        if (slot < 0) {
            return false;
        }

        this.selected = slot;
        this.refresh();
        return true;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double dx, double dy) {
        if (this.wheelLayout().sectionAt(x, y) == this.selected && dy != 0) {
            this.cycle(dy > 0 ? -1 : 1);
            return true;
        }

        return super.mouseScrolled(x, y, dx, dy);
    }
}
