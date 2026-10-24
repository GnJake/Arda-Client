package com.gnjake.ardaclient.hud;

import com.gnjake.ardaclient.config.ArdaStore;
import com.gnjake.ardaclient.gui.ArdaButton;
import com.gnjake.ardaclient.gui.ArdaTheme;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public final class HudPlacementScreen extends Screen {
    private static final List<HudPanel> PANELS = List.of(TowersHud.INSTANCE, MithrilTallyHud.INSTANCE, MithrilHud.INSTANCE, TallyHud.INSTANCE, CurrencyHud.INSTANCE, DungeonHud.INSTANCE);
    private final Screen parent;
    private HudPanel dragging;
    private double grabX;
    private double grabY;

    public HudPlacementScreen(Screen parent) {
        super(Component.literal("Move HUD"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        TowersHud.INSTANCE.preview(true);
        this.addRenderableWidget(new ArdaButton("Reset Positions", 8, this.height - 28, 105, 20, () -> {
            PANELS.forEach(HudPanel::reset);
            ArdaStore.save();
        }));
        this.addRenderableWidget(new ArdaButton("Done", this.width - 72, this.height - 28, 64, 20, this::onClose));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        long now = System.currentTimeMillis();

        for (HudPanel panel : PANELS) {
            if (panel.shown(now)) {
                panel.render(graphics);
                HudPanel.Bounds bounds = panel.bounds(this.width, this.height, now);
                if (panel == this.dragging || bounds.contains(mouseX, mouseY)) {
                    graphics.outline((int)bounds.x() - 1, (int)bounds.y() - 1, (int)Math.ceil(bounds.width()) + 2, (int)Math.ceil(bounds.height()) + 2, ArdaTheme.accentBright());
                }
            }
        }

        int messageWidth = 214;
        int x = (this.width - messageWidth) / 2;
        ArdaTheme.frame(graphics, x, this.height - 55, messageWidth, 23);
        graphics.centeredText(this.font, "Drag a panel to move it", this.width / 2, this.height - 48, ArdaTheme.TEXT);
        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean twice) {
        if (super.mouseClicked(event, twice)) {
            return true;
        } else if (event.button() != 0) {
            return false;
        } else {
            long now = System.currentTimeMillis();

            for (HudPanel panel : PANELS.reversed()) {
                HudPanel.Bounds bounds = panel.bounds(this.width, this.height, now);
                if (panel.shown(now) && bounds.contains(event.x(), event.y())) {
                    this.dragging = panel;
                    this.grabX = event.x() - bounds.x();
                    this.grabY = event.y() - bounds.y();
                    return true;
                }
            }

            return false;
        }
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (this.dragging == null) {
            return super.mouseDragged(event, dragX, dragY);
        } else {
            this.dragging.moveTo((int)Math.round(event.x() - this.grabX), (int)Math.round(event.y() - this.grabY));
            return true;
        }
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (this.dragging != null && event.button() == 0) {
            this.dragging = null;
            ArdaStore.save();
            return true;
        } else {
            return super.mouseReleased(event);
        }
    }

    @Override
    public void onClose() {
        TowersHud.INSTANCE.preview(false);
        ArdaStore.save();
        this.minecraft.gui.setScreen(this.parent);
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
}
