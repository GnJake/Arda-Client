package com.gnjake.ardaclient.wheel;

import com.gnjake.ardaclient.ArdaClient;
import com.gnjake.ardaclient.ServerCommands;
import com.gnjake.ardaclient.config.ArdaStore;
import com.gnjake.ardaclient.dungeon.DungeonTracker;
import com.gnjake.ardaclient.hud.HudSettingsScreen;
import com.gnjake.ardaclient.gui.ArdaTheme;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

public final class WheelScreen extends Screen {
    private static final int SECTIONS = 6;
    private static final double OUTER_RADIUS = 112.0;
    private static final double INNER_RADIUS = 39.0;
    private static final double LABEL_RADIUS = 76.0;
    private static final double WEDGE = Math.PI * 2 / SECTIONS;
    /** Half the angle each wedge covers, leaving a small gap between neighbors. */
    private static final double WEDGE_HALF_WIDTH = WEDGE / 2 - 0.035;
    private static final int DIM_BACKGROUND = 0x52101415;
    private final long opened = System.nanoTime();
    private int selected = -1;
    private boolean closed;

    public WheelScreen() {
        super(Component.literal("Arda Client Wheel"));
    }

    private List<Section> sections() {
        return List.of(
            new Section("CHRONICLES", "Open Arda Chronicles", Items.WRITABLE_BOOK, () -> this.send(ArdaStore.data.settings.chroniclesCommand)),
            new Section("HUD", "Panels, color and transparency", Items.SPYGLASS, () -> this.minecraft.gui.setScreen(new HudSettingsScreen(null))),
            new Section("SKIRMISH", "Run /skirmish info", Items.CLOCK, () -> this.send(ArdaStore.data.settings.skirmishCommand)),
            new Section("DUNGEON", "Run /dungeon_info", Items.IRON_SWORD, () -> {
                this.minecraft.gui.setScreen(null);
                DungeonTracker.requestInfo();
            }),
            new Section("EVENTS", "Run /events schedule", Items.PAPER, () -> this.send("/events schedule")),
            new Section("COMMANDS", "Your command shortcuts", Items.COMMAND_BLOCK, () -> this.minecraft.gui.setScreen(new QuickCommandsScreen(null)))
        );
    }

    private void send(String command) {
        this.minecraft.gui.setScreen(null);
        ServerCommands.run(command);
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
        double scale = settings.scale * (0.92 + 0.08 * animation);
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        this.selected = sectionAt(mouseX, mouseY, this.width, this.height, scale);
        int outer = (int)(OUTER_RADIUS * scale);
        int inner = (int)(INNER_RADIUS * scale);
        graphics.fill(0, 0, this.width, this.height, DIM_BACKGROUND);
        this.drawWheel(graphics, centerX, centerY, inner, outer, settings.opacity * animation);
        this.drawCenter(graphics, centerX, centerY, inner - 3, settings.opacity * animation);
        List<Section> sections = this.sections();

        for (int index = 0; index < sections.size(); index++) {
            double angle = -Math.PI / 2 + index * WEDGE;
            double radius = LABEL_RADIUS * scale;
            int x = (int)(centerX + Math.cos(angle) * radius);
            int y = (int)(centerY + Math.sin(angle) * radius);
            Section section = sections.get(index);
            if (settings.icons) {
                graphics.pose().pushMatrix();
                graphics.pose().translate(x - 8, y - 8);
                graphics.pose().scale((float)Math.max(0.7, scale * 0.9));
                graphics.item(new ItemStack(section.icon), 0, 0);
                graphics.pose().popMatrix();
            }

            if (settings.labels) {
                this.small(graphics, section.name, x, y + (settings.icons ? 12 : 0), index == this.selected ? ArdaTheme.accentBright() : ArdaTheme.TEXT);
            }
        }

        Section hover = this.selected < 0 ? null : sections.get(this.selected);
        String center = hover == null ? "ARDA" : hover.name;
        if (hover == null) {
            ArdaTheme.mark(graphics, centerX, centerY - 18, ArdaTheme.accent());
        }

        graphics.centeredText(this.font, center, centerX, centerY - 5, hover == null ? ArdaTheme.accentBright() : ArdaTheme.TEXT);
        if (hover != null) {
            graphics.pose().pushMatrix();
            graphics.pose().translate(centerX, centerY + 8);
            graphics.pose().scale(0.55F);
            graphics.text(this.font, hover.subtitle, -this.font.width(hover.subtitle) / 2, 0, ArdaTheme.MUTED);
            graphics.pose().popMatrix();
        }
    }

    private void drawWheel(GuiGraphicsExtractor graphics, int centerX, int centerY, int inner, int outer, double opacity) {
        int innerSquared = inner * inner;
        int outerSquared = outer * outer;

        for (int dy = -outer; dy <= outer; dy++) {
            int run = -1;
            int section = -1;

            for (int dx = -outer; dx <= outer + 1; dx++) {
                int next = dx <= outer ? this.sectionForPixel(dx, dy, innerSquared, outerSquared) : -1;
                if (next != section) {
                    if (section >= 0) {
                        graphics.fill(centerX + run, centerY + dy, centerX + dx, centerY + dy + 1, this.wedgeColor(section == this.selected, opacity));
                    }

                    run = dx;
                    section = next;
                }
            }
        }
    }

    private int sectionForPixel(int dx, int dy, int innerSquared, int outerSquared) {
        int radius = dx * dx + dy * dy;
        return radius >= innerSquared && radius <= outerSquared ? sectionForAngle(Math.atan2(dy, dx)) : -1;
    }

    /** The wedge an angle falls in, or -1 if it is in the gap between two wedges. Wedge 0 points straight up. */
    private static int sectionForAngle(double angle) {
        int index = Math.floorMod((int)Math.floor((angle + Math.PI / 2 + WEDGE / 2) / WEDGE), SECTIONS);
        double center = -Math.PI / 2 + index * WEDGE;
        double difference = Math.atan2(Math.sin(angle - center), Math.cos(angle - center));
        return Math.abs(difference) < WEDGE_HALF_WIDTH ? index : -1;
    }

    private void drawCenter(GuiGraphicsExtractor graphics, int centerX, int centerY, int radius, double opacity) {
        int color = (int)(255.0 * Math.max(0.0, Math.min(1.0, opacity)) * 0.94) << 24 | 0x0E0F12;

        for (int y = -radius; y <= radius; y++) {
            int half = (int)Math.sqrt(radius * radius - y * y);
            graphics.fill(centerX - half, centerY + y, centerX + half + 1, centerY + y + 1, color);
        }
    }

    private int wedgeColor(boolean active, double opacity) {
        int rgb = active ? ArdaTheme.mix(0x15171B, ArdaStore.data.accentColor, 0.2) : 0x15171B;
        int alpha = (int)(255.0 * Math.max(0.0, Math.min(1.0, opacity)) * (active ? 0.92 : 0.86));
        return alpha << 24 | rgb;
    }

    private void small(GuiGraphicsExtractor graphics, String text, int x, int y, int color) {
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale(0.62F);
        graphics.text(this.font, text, -this.font.width(text) / 2, 0, color, true);
        graphics.pose().popMatrix();
    }

    public static int sectionAt(double mouseX, double mouseY, int width, int height, double scale) {
        double distance = Math.hypot(mouseX - width / 2.0, mouseY - height / 2.0);
        if (distance < INNER_RADIUS * scale || distance > OUTER_RADIUS * scale) {
            return -1;
        }

        return sectionForAngle(Math.atan2(mouseY - height / 2.0, mouseX - width / 2.0));
    }

    public void select() {
        if (!this.closed) {
            this.closed = true;
            List<Section> sections = this.sections();
            if (this.selected >= 0 && this.selected < sections.size()) {
                sections.get(this.selected).action.run();
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
