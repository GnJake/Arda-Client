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
            return "Xaero's Minimap isn't ready yet. Try again in a moment.";
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
            return "Xaero's Minimap isn't ready yet. Try again in a moment.";
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
     * Xaero's waypoint world for Harad on this server. In Harad that is the world Xaero is showing; elsewhere it is
     * Harad's world with the same name as the current one, which Xaero creates if it is missing.
     */
    private MinimapWorld world() {
        MinimapSession session = BuiltInHudModules.MINIMAP.getCurrentSession();
        Minecraft minecraft = Minecraft.getInstance();
        if (session == null || minecraft.level == null) {
            return null;
        }

        MinimapWorldManager manager = session.getWorldManager();
        MinimapWorld current = manager.getCurrentWorld();
        if (minecraft.level.dimension() == SkirmishWaypoints.HARAD || current == null) {
            return current;
        }

        MinimapWorldRootContainer root = manager.getCurrentRootContainer();
        String directory = session.getDimensionHelper().getDimensionDirectoryName(SkirmishWaypoints.HARAD);
        XaeroPath containerPath = root.getPath().resolve(directory);
        MinimapWorld harad = manager.getWorld(containerPath.resolve(current.getNode()));
        if (harad == null) {
            MinimapWorldContainer container = manager.getWorldContainer(containerPath);
            harad = container.getFirstWorld();
            if (harad == null) {
                harad = container.addWorld(current.getNode());
            }
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
