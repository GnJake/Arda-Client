package com.gnjake.ardaclient.structure;

import com.gnjake.ardaclient.ArdaBuild;
import com.gnjake.ardaclient.config.ArdaStore;
import com.gnjake.ardaclient.gui.ArdaButton;
import com.gnjake.ardaclient.hud.TowersHud;
import com.gnjake.ardaclient.gui.ArdaPanelScreen;
import com.gnjake.ardaclient.gui.ArdaTheme;
import com.gnjake.ardaclient.waypoint.SkirmishWaypoints;
import com.gnjake.ardaclient.waypoint.WaypointProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;

/**
 * The structures logged in Harad, one kind at a time, with the loggers' switches and a button that adds the whole list
 * to Xaero's map (or Meteor) as one waypoint group.
 */
public final class StructureScreen extends ArdaPanelScreen {
    private static final int ROW = 11;
    private static final int DELETE_WIDTH = 14;
    private static final int STATUS_WIDTH = 40;
    /** The waypoint group that holds every kind of structure at once. */
    private static final String ALL_GROUP = "All Structures";

    private StructureLog.Kind kind = StructureLog.Kind.CAMP;
    private final ArdaButton[] tabs = new ArdaButton[StructureLog.Kind.values().length];
    /** Reset Done for tunnels; the Towers Event panel's switch on the Towers tab. */
    private ArdaButton extra;
    /** Adds the shown kind to the map in its own group. */
    private ArdaButton export;
    /** Removes the shown kind's waypoints from the map. */
    private ArdaButton clear;
    private int statusTop;
    private int listTop;
    private int rows;
    private int scroll;
    private String message = "";

    public StructureScreen(Screen parent) {
        super("Structures", parent);
    }

    @Override
    protected void init() {
        this.layout(460, 246);
        int x = this.px + 10;
        int y = this.py + 31;
        // Recording structures is only in developer builds; release builds ship the list.
        int toggles = ArdaBuild.DEV ? 5 : 2;
        int toggle = (this.pw - 20 - 6 * (toggles - 1)) / toggles;
        if (ArdaBuild.DEV) {
            x = this.toggle(x, y, toggle, "Camp Log", () -> ArdaStore.data.campLogger, value -> ArdaStore.data.campLogger = value) + 6;
            x = this.toggle(x, y, toggle, "Tunnel Log", () -> ArdaStore.data.tunnelLogger, value -> ArdaStore.data.tunnelLogger = value) + 6;
            x = this.toggle(x, y, toggle, "Tower Log", () -> ArdaStore.data.towerLogger, value -> ArdaStore.data.towerLogger = value) + 6;
        }

        x = this.toggle(x, y, toggle, "Completion", () -> ArdaStore.data.tunnelCompletion, value -> ArdaStore.data.tunnelCompletion = value) + 6;
        this.toggle(x, y, this.px + this.pw - 10 - x, "Path", () -> ArdaStore.data.tunnelPath, value -> ArdaStore.data.tunnelPath = value);

        y += 24;
        x = this.px + 10;
        int tab = (this.pw - 20 - 6 * 3) / 4;
        for (StructureLog.Kind each : StructureLog.Kind.values()) {
            this.tabs[each.ordinal()] = this.button(each.plural, x, y, tab, () -> {
                this.kind = each;
                this.scroll = 0;
                this.refreshTabs();
            });
            x += tab + 6;
        }

        // Adding and clearing waypoints, for the shown kind or every kind, then the tab's own button.
        y += 24;
        x = this.px + 10;
        int action = (this.pw - 20 - 6 * 4) / 5;
        boolean mapped = SkirmishWaypoints.provider() != null;
        this.export = this.button("", x, y, action, () -> this.export(this.kind.plural, List.of(this.kind)));
        ArdaButton all = this.button("Add All", x + action + 6, y, action, () -> this.export(ALL_GROUP, List.of(StructureLog.Kind.values())));
        this.clear = this.button("", x + 2 * (action + 6), y, action, () -> this.clear(List.of(this.kind)));
        ArdaButton clearAll = this.button("Clear All", x + 3 * (action + 6), y, action, () -> this.clear(List.of(StructureLog.Kind.values())));
        for (ArdaButton button : List.of(this.export, all, this.clear, clearAll)) {
            button.active = mapped;
        }

        x += 4 * (action + 6);
        this.extra = this.button("", x, y, this.px + this.pw - 10 - x, () -> {
            if (this.kind == StructureLog.Kind.TOWER) {
                TowersHud.cycleMode();
                this.refreshTabs();
            } else {
                StructureLog.clearCompleted();
                this.message = "Every tunnel is open again.";
            }
        });
        // The status line sits under the buttons, then the column names, then the list.
        this.statusTop = y + 25;
        this.listTop = y + 52;
        this.rows = Math.max(1, (this.py + this.ph - 8 - this.listTop) / ROW);
        this.refreshTabs();
    }

    private void refreshTabs() {
        for (StructureLog.Kind each : StructureLog.Kind.values()) {
            this.tabs[each.ordinal()].active = each != this.kind;
        }

        this.export.setMessage("Add " + this.kind.plural);
        this.clear.setMessage("Clear " + this.kind.plural);
        this.extra.setMessage(this.kind == StructureLog.Kind.TOWER ? TowersHud.modeLabel().replace("Towers: ", "Panel: ") : "Reset Done");
    }

    private int toggle(int x, int y, int width, String name, BooleanSupplier get, Consumer<Boolean> set) {
        ArdaButton button = this.button(name + ": " + (get.getAsBoolean() ? "On" : "Off"), x, y, width, null);
        button.setAction(() -> {
            set.accept(!get.getAsBoolean());
            button.setMessage(name + ": " + (get.getAsBoolean() ? "On" : "Off"));
            ArdaStore.save();
        });
        return x + width;
    }

    /** Adds every structure of the given kinds to the waypoint mod, in one group. */
    private void export(String group, List<StructureLog.Kind> kinds) {
        WaypointProvider provider = SkirmishWaypoints.provider();
        if (provider == null) {
            return;
        }

        try {
            this.message = provider.addGroup(group, markers(kinds));
        } catch (RuntimeException | LinkageError e) {
            this.message = "Could not add the waypoints; see the log.";
            com.gnjake.ardaclient.ArdaClient.LOG.warn("Could not add {} waypoints", group, e);
        }
    }

    /**
     * Removes the structure waypoints of the given kinds that Arda Client added, from each kind's group and from
     * "All Structures". Waypoints the player made themselves stay.
     */
    private void clear(List<StructureLog.Kind> kinds) {
        WaypointProvider provider = SkirmishWaypoints.provider();
        if (provider == null) {
            return;
        }

        try {
            int removed = provider.removeGroup(ALL_GROUP, markers(kinds));
            for (StructureLog.Kind each : kinds) {
                removed += provider.removeGroup(each.plural, markers(List.of(each)));
            }

            String what = kinds.size() == 1 ? kinds.getFirst().singular.toLowerCase(java.util.Locale.ROOT) : "structure";
            this.message = "Removed " + removed + " " + what + " waypoint" + (removed == 1 ? "" : "s") + " from " + provider.name() + ".";
        } catch (RuntimeException | LinkageError e) {
            this.message = "Could not remove the waypoints; see the log.";
            com.gnjake.ardaclient.ArdaClient.LOG.warn("Could not remove structure waypoints", e);
        }
    }

    /** One waypoint per structure of the given kinds, named and placed the way {@link #export} adds them. */
    /** Waypoint markers for every logged structure of these kinds, named as they are when added to a waypoint mod. */
    public static List<WaypointProvider.Marker> markers(List<StructureLog.Kind> kinds) {
        List<WaypointProvider.Marker> markers = new ArrayList<>();
        for (StructureLog.Kind each : kinds) {
            List<StructureLog.Structure> entries = StructureLog.of(each);
            for (int index = 0; index < entries.size(); index++) {
                StructureLog.Structure entry = entries.get(index);
                // Tunnels have no number of their own, so they go by their place in the list.
                int number = each == StructureLog.Kind.TUNNEL ? index + 1 : entry.number;
                String name = each == StructureLog.Kind.TUNNEL ? "Tunnel " + number : entry.name();
                markers.add(new WaypointProvider.Marker(name, each.initial + number, entry.x, entry.y, entry.z));
            }
        }

        return markers;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        ArdaButton current = this.tabs[this.kind.ordinal()];
        ArdaTheme.accentLine(graphics, current.getX() + 1, current.getBottom() - 2, current.getRight() - 1, current.getBottom() - 1);

        int left = this.px + 10;
        int right = this.px + this.pw - 10;
        String status = this.message.isEmpty() ? TunnelPathfinder.status() : this.message;
        graphics.text(this.font, this.font.plainSubstrByWidth(status, right - left), left, this.statusTop, ArdaTheme.MUTED);

        List<StructureLog.Structure> entries = StructureLog.of(this.kind);
        int y = this.listTop - 12;
        graphics.text(this.font, "Number", left, y, ArdaTheme.DIM);
        graphics.text(this.font, "Coordinates", left + 90, y, ArdaTheme.DIM);
        graphics.text(this.font, "Distance", left + 210, y, ArdaTheme.DIM);
        if (this.kind == StructureLog.Kind.TUNNEL) {
            graphics.text(this.font, "Status", right - DELETE_WIDTH - STATUS_WIDTH, y, ArdaTheme.DIM);
        } else if (this.kind == StructureLog.Kind.TOWER) {
            graphics.text(this.font, "Held by", right - DELETE_WIDTH - STATUS_WIDTH - 20, y, ArdaTheme.DIM);
        }

        if (entries.isEmpty()) {
            graphics.text(this.font, ArdaBuild.DEV ? "None logged yet. Turn on a logger above and run some." : "None known yet.", left, this.listTop, ArdaTheme.DIM);
            return;
        }

        this.scroll = Math.max(0, Math.min(this.scroll, entries.size() - this.rows));
        boolean inHarad = StructureLogger.inHarad(this.minecraft);
        for (int row = 0; row < this.rows && this.scroll + row < entries.size(); row++) {
            StructureLog.Structure entry = entries.get(this.scroll + row);
            y = this.listTop + row * ROW;
            boolean hover = mouseY >= y - 1 && mouseY < y + ROW - 1 && mouseX >= left && mouseX < right;
            if (hover || (row & 1) == 1) {
                graphics.fill(left - 2, y - 2, right + 2, y + ROW - 2, hover ? ArdaTheme.HOVER : ArdaTheme.ROW_ALT);
            }

            String number = String.valueOf(this.kind == StructureLog.Kind.TUNNEL ? this.scroll + row + 1 : entry.number);
            graphics.text(this.font, number, left, y, ArdaTheme.accentBrightAt(row / (double)Math.max(1, this.rows - 1)));
            graphics.text(this.font, StructureLogger.coordinates(entry), left + 90, y, ArdaTheme.TEXT);
            if (inHarad && this.minecraft.player != null) {
                int distance = (int)Math.sqrt(entry.pos().distSqr(this.minecraft.player.blockPosition()));
                graphics.text(this.font, distance + " blocks", left + 210, y, ArdaTheme.MUTED);
            }

            if (this.kind == StructureLog.Kind.TOWER) {
                String clan = TowerEvent.holder(entry.number);
                graphics.text(this.font, clan.isEmpty() ? "None" : clan, right - DELETE_WIDTH - STATUS_WIDTH - 20, y, clan.isEmpty() ? ArdaTheme.DIM : ArdaTheme.AMBER);
            }

            if (this.kind == StructureLog.Kind.TUNNEL) {
                graphics.text(this.font, entry.completed() ? "Done" : "Open", right - DELETE_WIDTH - STATUS_WIDTH, y,
                    entry.completed() ? ArdaTheme.GREEN : ArdaTheme.AMBER);
            }

            if (ArdaBuild.DEV) {
                graphics.text(this.font, "X", right - 8, y, hover && mouseX >= right - DELETE_WIDTH ? ArdaTheme.accentBright() : ArdaTheme.DIM);
            }
        }

        if (entries.size() > this.rows) {
            int height = this.rows * ROW;
            int thumb = Math.max(8, height * this.rows / entries.size());
            int thumbTop = this.listTop - 2 + (height - thumb) * this.scroll / (entries.size() - this.rows);
            ArdaTheme.scrollbar(graphics, right + 4, this.listTop - 2, height, thumbTop, thumb);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean twice) {
        if (super.mouseClicked(event, twice)) {
            return true;
        }

        int left = this.px + 10;
        int right = this.px + this.pw - 10;
        if (event.button() != 0 || event.x() < left || event.x() >= right || event.y() < this.listTop - 1) {
            return false;
        }

        int row = (int)((event.y() - this.listTop + 1) / ROW);
        List<StructureLog.Structure> entries = StructureLog.of(this.kind);
        if (row >= this.rows || this.scroll + row >= entries.size()) {
            return false;
        }

        StructureLog.Structure entry = entries.get(this.scroll + row);
        if (ArdaBuild.DEV && event.x() >= right - DELETE_WIDTH) {
            StructureLog.remove(entry);
            this.message = "Removed " + entry.name() + ".";
        } else if (this.kind == StructureLog.Kind.TUNNEL && event.x() >= right - DELETE_WIDTH - STATUS_WIDTH) {
            entry.completedAt = entry.completed() ? 0L : System.currentTimeMillis();
            StructureLog.save();
        }

        return true;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double dx, double dy) {
        this.scroll = Math.max(0, this.scroll - (int)Math.signum(dy));
        return true;
    }
}
