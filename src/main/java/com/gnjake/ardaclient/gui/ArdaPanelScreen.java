package com.gnjake.ardaclient.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** A centered Arda Client panel with a title bar and a back arrow that returns to the parent screen. */
public abstract class ArdaPanelScreen extends Screen {
    protected final Screen parent;
    protected int px;
    protected int py;
    protected int pw;
    protected int ph;

    protected ArdaPanelScreen(String title, Screen parent) {
        super(Component.literal(title));
        this.parent = parent;
    }

    protected void layout(int wantedWidth, int wantedHeight) {
        this.pw = Math.min(wantedWidth, this.width - 20);
        this.ph = Math.min(wantedHeight, this.height - 24);
        this.px = (this.width - this.pw) / 2;
        this.py = (this.height - this.ph) / 2;
    }

    protected ArdaButton button(String text, int x, int y, int w, Runnable run) {
        return this.addRenderableWidget(new ArdaButton(text, x, y, w, 20, run));
    }

    protected EditBox textField(int x, int y, int w, String hint, String initial, int maxLength) {
        EditBox box = this.addRenderableWidget(new EditBox(this.font, x, y, w, 20, Component.literal(hint)));
        box.setHint(Component.literal(hint));
        box.setMaxLength(maxLength);
        box.setValue(initial == null ? "" : initial);
        return box;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float dt) {
        ArdaTheme.frame(g, this.px, this.py, this.pw, this.ph);
        ArdaTheme.header(g, this.px, this.py, this.pw, 25);
        ArdaTheme.mark(g, this.px + 12, this.py + 10);
        ArdaTheme.titleText(g, this.font, this.title.getString(), this.px + 22, this.py + 9);
        g.text(this.font, "<", this.px + this.pw - 15, this.py + 9, ArdaTheme.MUTED);
        super.extractRenderState(g, mx, my, dt);
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
    public void extractBackground(GuiGraphicsExtractor g, int x, int y, float dt) {
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean twice) {
        if (e.button() == 0 && e.x() > this.px + this.pw - 28 && e.y() > this.py && e.y() < this.py + 25) {
            this.onClose();
            return true;
        } else {
            return super.mouseClicked(e, twice);
        }
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(this.parent);
    }
}
