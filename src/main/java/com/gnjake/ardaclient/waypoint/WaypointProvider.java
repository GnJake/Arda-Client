package com.gnjake.ardaclient.waypoint;

import java.util.List;

/**
 * A mod that can hold waypoints, such as Xaero's Minimap or Meteor Client. Each provider lives in its own package and
 * is only loaded when its mod is installed.
 */
public interface WaypointProvider {
    /** The saved setting value, such as {@code xaero}. */
    String id();

    /** The name shown to the player, such as "Xaero". */
    String name();

    /**
     * Adds a waypoint in Harad, whichever dimension the player is in, to the group {@code set} (Xaero's waypoint set).
     * Returns a message about what happened.
     */
    String add(String set, String name, String initials, int x, int y, int z);

    /** Removes every skirmish and mirage encampment waypoint Arda Client added, returning how many were removed. */
    int removeSkirmish();

    /**
     * Adds waypoints in Harad as one group, such as Xaero's "Camps" waypoint set, skipping any already there. Returns
     * a message about what happened.
     */
    String addGroup(String group, List<Marker> markers);

    /** One waypoint for {@link #addGroup}. {@code initials} is the short label Xaero shows on the map. */
    record Marker(String name, String initials, int x, int y, int z) {
    }
}
