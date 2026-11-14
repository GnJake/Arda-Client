package com.gnjake.ardaclient.xaero;

import com.gnjake.ardaclient.ArdaClient;
import com.gnjake.ardaclient.waypoint.SkirmishWaypoints;
import com.gnjake.ardaclient.waypoint.WaypointProvider;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import xaero.common.minimap.waypoints.Waypoint;
import xaero.hud.minimap.BuiltInHudModules;
import xaero.hud.minimap.module.MinimapSession;
import xaero.hud.minimap.waypoint.WaypointColor;
import xaero.hud.minimap.waypoint.set.WaypointSet;
import xaero.hud.minimap.world.MinimapWorld;
import xaero.hud.minimap.world.MinimapWorldManager;
import xaero.hud.minimap.world.container.MinimapWorldContainer;
import xaero.hud.minimap.world.container.MinimapWorldRootContainer;
import xaero.hud.path.XaeroPath;

/**
 * Skirmish waypoints for Xaero's Minimap (or Better PVP, which shares its waypoints), also shown on Xaero's World Map.
 * They go in a "Skirmish" waypoint set, so they are easy to find and remove. Only this package uses Xaero's classes,
 * and it is only loaded when Xaero's Minimap is installed.
 */
public final class XaeroWaypoints implements WaypointProvider {
    private static final String NOT_READY = "Xaero's Minimap isn't ready yet. Try again in a moment.";

    /** Why {@link #world()} last came back empty. */
    private String problem = NOT_READY;

    /** Called only once {@link SkirmishWaypoints#xaeroInstalled()} has checked that Xaero's Minimap is there. */
    public static void register() {
        SkirmishWaypoints.register(new XaeroWaypoints());
    }

    @Override
    public String id() {
        return "xaero";
    }

    @Override
    public String name() {
        return "Xaero";
    }

    @Override
    public String add(String setName, String name, String initials, int x, int y, int z) {
        MinimapWorld world = this.world();
        if (world == null) {
            return this.problem;
        }

        WaypointSet set = world.getWaypointSet(setName);
        if (set == null) {
            world.addWaypointSet(setName);
            set = world.getWaypointSet(setName);
        }

        for (Waypoint existing : set.getWaypoints()) {
            if (existing.getX() == x && existing.getZ() == z && existing.getName().equals(name)) {
                return name + " is already in Xaero's \"" + setName + "\" waypoints.";
            }
        }

        set.add(new Waypoint(x, y, z, name, initials, WaypointColor.RED));
        this.save(world);
        return "Added " + name + " at " + x + ", " + y + ", " + z + " to Xaero's \"" + setName + "\" waypoints in Harad.";
    }

    @Override
    public String addGroup(String group, List<Marker> markers) {
        MinimapWorld world = this.world();
        if (world == null) {
            return this.problem;
        }

        WaypointSet set = world.getWaypointSet(group);
        if (set == null) {
            world.addWaypointSet(group);
            set = world.getWaypointSet(group);
        }

        int added = 0;
        for (Marker marker : markers) {
            boolean present = false;
            for (Waypoint existing : set.getWaypoints()) {
                present |= existing.getX() == marker.x() && existing.getY() == marker.y() && existing.getZ() == marker.z();
            }

            if (!present) {
                set.add(new Waypoint(marker.x(), marker.y(), marker.z(), marker.name(), marker.initials(), WaypointColor.YELLOW));
                added++;
            }
        }

        this.save(world);
        return "Added " + added + " of " + markers.size() + " to Xaero's \"" + group + "\" waypoints in Harad.";
    }

    @Override
    public int removeGroup(String group, List<Marker> markers) {
        MinimapWorld world = this.world();
        WaypointSet set = world == null ? null : world.getWaypointSet(group);
        if (set == null) {
            return 0;
        }

        List<Waypoint> ours = new ArrayList<>();
        for (Waypoint waypoint : set.getWaypoints()) {
            if (markers.stream().anyMatch(marker -> marker.x() == waypoint.getX() && marker.y() == waypoint.getY() && marker.z() == waypoint.getZ())) {
                ours.add(waypoint);
            }
        }

        set.removeAll(ours);
        // An empty set goes too, unless Xaero is showing it.
        if (set.isEmpty() && !group.equals(world.getCurrentWaypointSetId())) {
            world.removeWaypointSet(group);
        }

        this.save(world);
        return ours.size();
    }

    @Override
    public int removeSkirmish() {
        MinimapWorld world = this.world();
        if (world == null) {
            return 0;
        }

        int removed = 0;
        for (SkirmishWaypoints.Target target : SkirmishWaypoints.Target.values()) {
            WaypointSet set = world.getWaypointSet(target.set);
            if (set == null) {
                continue;
            }

            List<Waypoint> ours = new ArrayList<>();
            for (Waypoint waypoint : set.getWaypoints()) {
                if (waypoint.getName().startsWith(target.set)) {
                    ours.add(waypoint);
                }
            }

            set.removeAll(ours);
            removed += ours.size();
        }

        this.save(world);
        return removed;
    }

    /**
     * Xaero's waypoint world for Harad on this server: the first world in Harad's dimension folder, which Xaero
     * creates if it is missing. Null, with {@link #problem} saying why, when it can't be found; a waypoint is never
     * put anywhere else, or it would show in whatever dimension the player is in.
     */
    private MinimapWorld world() {
        MinimapSession session = BuiltInHudModules.MINIMAP.getCurrentSession();
        Minecraft minecraft = Minecraft.getInstance();
        MinimapWorld current = session == null || minecraft.level == null ? null : session.getWorldManager().getCurrentWorld();
        if (current == null) {
            this.problem = NOT_READY;
            return null;
        }

        MinimapWorldManager manager = session.getWorldManager();
        String directory = session.getDimensionHelper().getDimensionDirectoryName(SkirmishWaypoints.HARAD);
        MinimapWorldRootContainer root = current.getContainer().getRoot();
        MinimapWorldContainer container = null;
        for (MinimapWorldContainer each : root.getSubContainers()) {
            if (each.getLastNode().equalsIgnoreCase(directory)) {
                container = each;
            }
        }

        if (container == null) {
            container = manager.getWorldContainer(root.getPath().resolve(directory));
        }

        // In Harad, the world Xaero is showing is the one to use; elsewhere, Harad's first world.
        MinimapWorld harad = current.getContainer() == container ? current : container.getFirstWorld();
        if (harad == null) {
            harad = container.addWorld(current.getNode());
        }

        String where = harad.getContainer().getLastNode();
        ArdaClient.LOG.info("Xaero waypoints go to {} (player in {}, Xaero showing {})", harad.getFullPath(), minecraft.level.dimension().identifier(),
            current.getFullPath());
        if (!where.equalsIgnoreCase(directory) || harad == current && minecraft.level.dimension() != SkirmishWaypoints.HARAD) {
            ArdaClient.LOG.warn("Xaero's Harad waypoints resolved to {}, not {}; nothing was added", harad.getFullPath(), directory);
            this.problem = "Couldn't find Xaero's Harad waypoints, so nothing was added. See the log.";
            return null;
        }

        if (harad.getDimId() == null) {
            harad.setDimId(SkirmishWaypoints.HARAD);
        }

        return harad;
    }

    private void save(MinimapWorld world) {
        try {
            BuiltInHudModules.MINIMAP.getCurrentSession().getWorldManagerIO().saveWorld(world);
        } catch (Exception e) {
            ArdaClient.LOG.warn("Could not save Xaero's waypoints", e);
        }
    }
}
