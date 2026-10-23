package com.gnjake.ardaclient.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** A movable, scalable on-screen panel. Sizes are in unscaled GUI pixels; positions are the scaled top-left corner. */
public abstract class HudPanel {
    protected static final int PAD = 4;

    public abstract boolean enabled();

    protected abstract double scale();

    protected abstract int contentWidth(Font font, long now);

    protected abstract int contentHeight(Font font, long now);

    protected abstract int left(int screenWidth, int screenHeight, long now);

    protected abstract int top(int screenWidth, int screenHeight, long now);

    protected abstract void draw(GuiGraphicsExtractor graphics, Font font, int width, int height, long now);

    public abstract void moveTo(int x, int y);

    public abstract void reset();

    public Bounds bounds(int screenWidth, int screenHeight, long now) {
        Font font = Minecraft.getInstance().font;
        double width = this.contentWidth(font, now) * this.scale();
        double height = this.contentHeight(font, now) * this.scale();
        double x = Math.min(Math.max(0.0, screenWidth - width), this.left(screenWidth, screenHeight, now));
        double y = Math.min(Math.max(0.0, screenHeight - height), this.top(screenWidth, screenHeight, now));
        return new Bounds(x, y, width, height);
    }

    public void render(GuiGraphicsExtractor graphics) {
        long now = System.currentTimeMillis();
        Font font = Minecraft.getInstance().font;
        Bounds bounds = this.bounds(graphics.guiWidth(), graphics.guiHeight(), now);
        graphics.pose().pushMatrix();
        graphics.pose().translate((float)bounds.x, (float)bounds.y);
        graphics.pose().scale((float)this.scale());
        this.draw(graphics, font, this.contentWidth(font, now), this.contentHeight(font, now), now);
        graphics.pose().popMatrix();
    }

    public record Bounds(double x, double y, double width, double height) {
        public boolean contains(double mouseX, double mouseY) {
            return mouseX >= this.x && mouseX < this.x + this.width && mouseY >= this.y && mouseY < this.y + this.height;
        }
    }
}
