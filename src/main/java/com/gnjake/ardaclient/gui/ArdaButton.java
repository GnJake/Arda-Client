package com.gnjake.ardaclient.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

public final class ArdaButton extends AbstractButton {
    private Runnable action;

    public ArdaButton(String text, int x, int y, int width, int height, Runnable action) {
        super(x, y, width, height, Component.literal(text));
        this.action = action;
    }

    /** Sets what the button does, for buttons whose action needs a reference to the button itself. */
    public void setAction(Runnable action) {
        this.action = action;
    }

    public void setMessage(String text) {
        this.setMessage(Component.literal(text));
    }

    @Override
    public void onPress(InputWithModifiers input) {
        if (this.action != null) {
            this.action.run();
        }
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor g, int mx, int my, float dt) {
        boolean hot = this.isHoveredOrFocused();
        int bg = this.active ? (hot ? ArdaTheme.HOVER : ArdaTheme.ROW_ALT) : ArdaTheme.DISABLED;
        int border = hot && this.active ? ArdaTheme.accent() : ArdaTheme.BORDER;
        int text = this.active ? ArdaTheme.TEXT : ArdaTheme.MUTED;
        g.fill(this.getX() + 1, this.getY() + 1, this.getRight() - 1, this.getBottom() - 1, bg);
        g.outline(this.getX(), this.getY(), this.getWidth(), this.getHeight(), border);
        if (hot && this.active) {
            g.fill(this.getX() + 1, this.getBottom() - 2, this.getRight() - 1, this.getBottom() - 1, ArdaTheme.accent());
        }

        g.centeredText(Minecraft.getInstance().font, this.getMessage(), this.getX() + this.getWidth() / 2, this.getY() + (this.getHeight() - 8) / 2, text);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        this.defaultButtonNarrationText(output);
    }
}
