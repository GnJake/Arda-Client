package com.gnjake.ardaclient.wheel;

import com.gnjake.ardaclient.ArdaClient;
import com.gnjake.ardaclient.config.ArdaStore;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import org.lwjgl.glfw.GLFW;

public final class WheelScreen extends Screen {
    private static final int DIM_BACKGROUND = 0x52101415;
    private final long opened = System.nanoTime();
    private int selected = -1;
    private boolean closed;

    public WheelScreen() {
        super(Component.literal("Arda Client Wheel"));
    }

    /** One entry per slot, clockwise from the top; null for an empty slot. */
    private List<Section> sections() {
        return ArdaStore.data.wheel.stream().map(WheelSlots::section).toList();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean isInGameUi() {
        return true;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int x, int y, float delta) {
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        ArdaStore.Settings settings = ArdaStore.data.settings;
        double age = (System.nanoTime() - this.opened) / 1.0E9;
        double animation = Math.min(1.0, age / 0.09);
        animation = 1.0 - Math.pow(1.0 - animation, 3.0);
        List<Section> sections = this.sections();
        WheelRenderer.Layout layout = WheelRenderer.layout(this.width / 2, this.height / 2, settings.scale * (0.92 + 0.08 * animation), sections.size());
        this.selected = layout.sectionAt(mouseX, mouseY);
        if (this.selected >= 0 && sections.get(this.selected) == null) {
            this.selected = -1;
        }

        graphics.fill(0, 0, this.width, this.height, DIM_BACKGROUND);
        WheelRenderer.draw(graphics, this.font, layout, sections, this.selected, settings.opacity * animation, settings.labels, settings.icons);
        WheelRenderer.drawCenter(graphics, this.font, layout, this.selected < 0 ? null : sections.get(this.selected), "ARDA");
    }

    public void select() {
        if (!this.closed) {
            this.closed = true;
            List<Section> sections = this.sections();
            Section section = this.selected >= 0 && this.selected < sections.size() ? sections.get(this.selected) : null;
            if (section != null) {
                section.action.run();
            } else {
                this.minecraft.gui.setScreen(null);
            }
        }
    }

    private void cancel() {
        this.closed = true;
        QuickWheel.suppress();
        this.minecraft.gui.setScreen(null);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            this.cancel();
        }

        return true;
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        if (ArdaClient.wheelKey.matches(event)) {
            this.select();
        }

        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean twice) {
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            this.cancel();
        }

        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (ArdaClient.wheelKey.matchesMouse(event)) {
            this.select();
        }

        return true;
    }

    @Override
    public void onClose() {
        if (!this.closed) {
            this.cancel();
        }
    }

    public record Section(String name, String subtitle, Item icon, Runnable action) {
    }
}
