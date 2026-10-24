package com.gnjake.ardaclient.meteor;

import com.gnjake.ardaclient.waypoint.SkirmishWaypoints;
import com.gnjake.ardaclient.waypoint.WaypointProvider;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import meteordevelopment.meteorclient.systems.waypoints.Waypoint;
import meteordevelopment.meteorclient.systems.waypoints.Waypoints;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.Dimension;
import net.minecraft.core.BlockPos;

/** Skirmish waypoints for Meteor Client. They are named "Skirmish Level N", so they are easy to find and remove. */
public final class MeteorWaypoints implements WaypointProvider {
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

        return "Added " + added + " of " + markers.size() + " " + group.toLowerCase() + " to Meteor's waypoints.";
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
