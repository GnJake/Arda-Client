package com.gnjake.ardaclient.hud;

import com.gnjake.ardaclient.config.ArdaStore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * A movable, scalable on-screen panel. Sizes are in unscaled GUI pixels; positions are the scaled top-left corner.
 * When Meteor's HUD draws a panel, the built-in copy steps aside so it isn't shown twice.
 */
public abstract class HudPanel {
    protected static final int PAD = 4;
    /** How long after Meteor last drew a panel the built-in copy stays hidden. */
    private static final long EXTERNAL_TIMEOUT = 1000L;

    private long drawnExternallyAt;

    public abstract boolean enabled();

    protected abstract double scale();

    protected abstract int contentWidth(Font font, long now);

    protected abstract int contentHeight(Font font, long now);

    protected abstract int left(int screenWidth, int screenHeight, long now);

    protected abstract int top(int screenWidth, int screenHeight, long now);

    protected abstract void draw(GuiGraphicsExtractor graphics, Font font, int width, int height, long now);

    public abstract void moveTo(int x, int y);

    public abstract void reset();

    /** Background opacity chosen in HUD Settings. */
    protected static double opacity() {
        return ArdaStore.data.hudOpacity;
    }

    /** Whether the built-in panel is drawn: enabled, and not currently drawn by Meteor's HUD instead. */
    public boolean shown(long now) {
        return this.enabled() && now - this.drawnExternallyAt > EXTERNAL_TIMEOUT;
    }

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
        Bounds bounds = this.bounds(graphics.guiWidth(), graphics.guiHeight(), now);
        this.drawAt(graphics, bounds.x, bounds.y, this.scale(), now);
    }

    /** Unscaled width, for a HUD that positions the panel itself. */
    public int width(long now) {
        return this.contentWidth(Minecraft.getInstance().font, now);
    }

    public int height(long now) {
        return this.contentHeight(Minecraft.getInstance().font, now);
    }

    public void drawAt(GuiGraphicsExtractor graphics, double x, double y, double scale, long now) {
        Font font = Minecraft.getInstance().font;
        graphics.pose().pushMatrix();
        graphics.pose().translate((float)x, (float)y);
        graphics.pose().scale((float)scale);
        this.draw(graphics, font, this.contentWidth(font, now), this.contentHeight(font, now), now);
        graphics.pose().popMatrix();
    }

    /** Called by Meteor's HUD each time it draws this panel. */
    public void markDrawnExternally(long now) {
        this.drawnExternallyAt = now;
    }

    /** Where a panel docked below {@code above} goes, or the default corner if {@code above} isn't shown. */
    protected static int dockedLeft(HudPanel above, int screenWidth, int screenHeight, long now) {
        return above.shown(now) ? (int)above.bounds(screenWidth, screenHeight, now).x() : ArdaStore.DEFAULT_LEFT;
    }

    protected static int dockedTop(HudPanel above, int screenWidth, int screenHeight, long now) {
        if (!above.shown(now)) {
            return ArdaStore.DEFAULT_TOP;
        }

        Bounds bounds = above.bounds(screenWidth, screenHeight, now);
        return (int)Math.ceil(bounds.y() + bounds.height()) + 2;
    }

    public record Bounds(double x, double y, double width, double height) {
        public boolean contains(double mouseX, double mouseY) {
            return mouseX >= this.x && mouseX < this.x + this.width && mouseY >= this.y && mouseY < this.y + this.height;
        }
    }
}
