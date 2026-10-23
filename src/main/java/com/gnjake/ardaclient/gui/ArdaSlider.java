package com.gnjake.ardaclient.gui;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

/** A slider in the Arda Client style. The value runs from 0 to 1; {@code label} turns it into the text shown. */
public final class ArdaSlider extends AbstractSliderButton {
    private final DoubleFunction<String> label;
    private final DoubleConsumer onChange;

    public ArdaSlider(int x, int y, int width, int height, double value, DoubleFunction<String> label, DoubleConsumer onChange) {
        super(x, y, width, height, Component.literal(label.apply(value)), value);
        this.label = label;
        this.onChange = onChange;
    }

    @Override
    protected void updateMessage() {
        this.setMessage(Component.literal(this.label.apply(this.value)));
    }

    @Override
    protected void applyValue() {
        this.onChange.accept(this.value);
    }

    @Override
    public void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float dt) {
        boolean hot = this.isHoveredOrFocused();
        int x = this.getX();
        int y = this.getY();
        g.fill(x + 1, y + 1, this.getRight() - 1, this.getBottom() - 1, hot ? ArdaTheme.HOVER : ArdaTheme.ROW_ALT);
        g.outline(x, y, this.getWidth(), this.getHeight(), hot ? ArdaTheme.accent() : ArdaTheme.BORDER);
        int filled = (int)Math.round((this.getWidth() - 2) * this.value);
        g.fill(x + 1, this.getBottom() - 3, x + 1 + filled, this.getBottom() - 1, ArdaTheme.accent());
        int handle = x + 1 + Math.min(this.getWidth() - 5, Math.max(0, filled - 2));
        g.fill(handle, y + 2, handle + 3, this.getBottom() - 2, hot ? ArdaTheme.accentBright() : ArdaTheme.MUTED);
        g.centeredText(Minecraft.getInstance().font, this.getMessage(), x + this.getWidth() / 2, y + (this.getHeight() - 8) / 2, ArdaTheme.TEXT);
    }
}
