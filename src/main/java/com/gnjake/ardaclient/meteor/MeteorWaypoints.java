package com.gnjake.ardaclient.meteor;

import com.gnjake.ardaclient.structure.StructureLog;
import com.gnjake.ardaclient.structure.StructureScreen;
import com.gnjake.ardaclient.waypoint.SkirmishWaypoints;
import com.gnjake.ardaclient.waypoint.WaypointProvider;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import meteordevelopment.meteorclient.systems.waypoints.Waypoint;
import meteordevelopment.meteorclient.systems.waypoints.Waypoints;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.Dimension;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Skirmish and structure waypoints for Meteor Client. They are named "Skirmish Level N", so they are easy to find and
 * remove. Meteor only knows the three vanilla dimensions and saves them as Overworld, so they are hidden everywhere but
 * Harad, or they would show in the real Overworld too.
 */
public final class MeteorWaypoints implements WaypointProvider {
    /** How often waypoints added since are shown or hidden too. */
    private static final int CHECK_EVERY = 100;

    private ResourceKey<Level> lastDimension;
    private int ticks;

    /** Shows Arda's waypoints in Harad and hides them elsewhere, whenever the player changes dimension. */
    public void tick(Minecraft minecraft) {
        if (minecraft.level == null) {
            this.lastDimension = null;
            return;
        }

        ResourceKey<Level> dimension = minecraft.level.dimension();
        if (dimension == this.lastDimension && ++this.ticks % CHECK_EVERY != 0) {
            return;
        }

        this.lastDimension = dimension;
        boolean inHarad = dimension == SkirmishWaypoints.HARAD;
        List<Marker> structures = StructureScreen.markers(List.of(StructureLog.Kind.values()));
        for (Waypoint waypoint : Waypoints.get()) {
            if (waypoint.visible.get() != inHarad && ours(waypoint, structures)) {
                waypoint.visible.set(inHarad);
            }
        }
    }

    /** A skirmish or mirage waypoint by its name, or a structure one by its name and position. */
    private static boolean ours(Waypoint waypoint, List<Marker> structures) {
        String name = waypoint.name.get();
        if (Arrays.stream(SkirmishWaypoints.Target.values()).anyMatch(target -> name.startsWith(target.set))) {
            return true;
        }

        BlockPos pos = waypoint.getPos();
        return structures.stream().anyMatch(marker -> marker.name().equals(name) && new BlockPos(marker.x(), marker.y(), marker.z()).equals(pos));
    }

    @Override
    public String id() {
        return "meteor";
    }

    @Override
    public String name() {
        return "Meteor";
    }

    @Override
    public String add(String set, String name, String initials, int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        for (Waypoint existing : Waypoints.get()) {
            if (existing.name.get().equals(name) && existing.getPos().getX() == x && existing.getPos().getZ() == z) {
                return name + " is already in Meteor's waypoints.";
            }
        }

        // Meteor only knows the three vanilla dimensions, and counts Harad as the Overworld.
        Waypoint waypoint = new Waypoint.Builder().name(name).pos(pos).dimension(Dimension.Overworld).build();
        waypoint.color.set(new SettingColor(198, 40, 40));
        Waypoints.get().add(waypoint);
        this.lastDimension = null;
        return "Added " + name + " at " + x + ", " + y + ", " + z + " to Meteor's waypoints.";
    }

    @Override
    public String addGroup(String group, List<Marker> markers) {
        int added = 0;
        for (Marker marker : markers) {
            boolean present = false;
            for (Waypoint existing : Waypoints.get()) {
                present |= existing.getPos().equals(new BlockPos(marker.x(), marker.y(), marker.z()));
            }

            if (!present) {
                Waypoint waypoint = new Waypoint.Builder().name(marker.name()).pos(new BlockPos(marker.x(), marker.y(), marker.z()))
                    .dimension(Dimension.Overworld).build();
                waypoint.color.set(new SettingColor(249, 168, 37));
                Waypoints.get().add(waypoint);
                added++;
            }
        }

        this.lastDimension = null;
        return "Added " + added + " of " + markers.size() + " " + group.toLowerCase() + " to Meteor's waypoints.";
    }

    @Override
    public int removeGroup(String group, List<Marker> markers) {
        // Meteor has no groups, so a waypoint is ours when both its name and position match a marker.
        List<Waypoint> ours = new ArrayList<>();
        for (Waypoint waypoint : Waypoints.get()) {
            String name = waypoint.name.get();
            BlockPos pos = waypoint.getPos();
            if (markers.stream().anyMatch(marker -> marker.name().equals(name) && new BlockPos(marker.x(), marker.y(), marker.z()).equals(pos))) {
                ours.add(waypoint);
            }
        }

        Waypoints.get().removeAll(ours);
        return ours.size();
    }

    @Override
    public int removeSkirmish() {
        List<Waypoint> ours = new ArrayList<>();
        for (Waypoint waypoint : Waypoints.get()) {
            String name = waypoint.name.get();
            if (Arrays.stream(SkirmishWaypoints.Target.values()).anyMatch(target -> name.startsWith(target.set))) {
                ours.add(waypoint);
            }
        }

        Waypoints.get().removeAll(ours);
        return ours.size();
    }
}
