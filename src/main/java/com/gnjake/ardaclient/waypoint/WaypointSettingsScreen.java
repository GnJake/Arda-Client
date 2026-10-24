package com.gnjake.ardaclient.waypoint;

import com.gnjake.ardaclient.config.ArdaStore;
import com.gnjake.ardaclient.gui.ArdaButton;
import com.gnjake.ardaclient.gui.ArdaPanelScreen;
import com.gnjake.ardaclient.gui.ArdaTheme;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

/** Which mod skirmish waypoint links add to, the height they go at, and clearing them. */
public final class WaypointSettingsScreen extends ArdaPanelScreen {
    private static final int Y_STEP = 5;
    private String status = "";

    public WaypointSettingsScreen(Screen parent) {
        super("Waypoint Settings", parent);
    }

    @Override
    protected void init() {
        this.layout(320, 152);
        int x = this.px + 10;
        int y = this.py + 31;
        int width = this.pw - 20;
        ArdaButton type = this.button(typeLabel(), x, y, width, null);
        type.setAction(() -> {
            List<String> choices = choices();
            int next = (choices.indexOf(current()) + 1) % choices.size();
            ArdaStore.data.waypointType = choices.get(next);
            ArdaStore.save();
            type.setMessage(typeLabel());
        });
        type.active = !SkirmishWaypoints.providers().isEmpty();

        y += 24;
        ArdaButton height = this.button(heightLabel(), x + 22, y, width - 44, () -> {
        });
        height.active = false;
        this.button("-", x, y, 20, () -> {
            this.moveY(-Y_STEP);
            height.setMessage(heightLabel());
        });
        this.button("+", x + width - 20, y, 20, () -> {
            this.moveY(Y_STEP);
            height.setMessage(heightLabel());
        });

        y += 24;
        this.button("Remove Skirmish Waypoints", x, y, width, () -> {
            int removed = SkirmishWaypoints.removeAll();
            this.status = "Removed " + removed + (removed == 1 ? " waypoint." : " waypoints.");
        });
    }

    /** The choices the type button cycles through: each installed waypoint mod, then off. */
    private static List<String> choices() {
        List<String> choices = new ArrayList<>();
        SkirmishWaypoints.providers().forEach(provider -> choices.add(provider.id()));
        choices.add(SkirmishWaypoints.OFF);
        return choices;
    }

    /** The setting in effect, which falls back to an installed mod when the saved one is missing. */
    private static String current() {
        WaypointProvider provider = SkirmishWaypoints.provider();
        return provider == null ? SkirmishWaypoints.OFF : provider.id();
    }

    private static String typeLabel() {
        WaypointProvider provider = SkirmishWaypoints.provider();
        if (SkirmishWaypoints.providers().isEmpty()) {
            return "Waypoints: No waypoint mod installed";
        }

        return "Waypoints: " + (provider == null ? "Off" : provider.name());
    }

    private static String heightLabel() {
        return "Height: Y " + ArdaStore.data.waypointY;
    }

    private void moveY(int step) {
        ArdaStore.data.waypointY = Math.max(SkirmishWaypoints.MIN_Y, Math.min(SkirmishWaypoints.MAX_Y, ArdaStore.data.waypointY + step));
        ArdaStore.save();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        int x = this.px + 10;
        int y = this.py + 108;
        graphics.text(this.font, "Skirmish messages get a waypoint link. Arda doesn't", x, y, ArdaTheme.MUTED);
        graphics.text(this.font, "give the height, so waypoints go at the Y set above.", x, y + 10, ArdaTheme.MUTED);
        ArdaTheme.accentText(graphics, this.font, this.status, x, y + 24);
    }
}
