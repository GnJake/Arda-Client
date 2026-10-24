package com.gnjake.ardaclient.structure;

import com.gnjake.ardaclient.ArdaClient;
import com.gnjake.ardaclient.config.ArdaStore;
import com.gnjake.ardaclient.gui.ArdaTheme;
import com.gnjake.ardaclient.waypoint.SkirmishWaypoints;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * Tunnel completions: a "Chronicle: Tunnel Tier N" message marks the nearest known spawner complete, until Harad's
 * next refresh. Tiers change with every refresh, so they aren't kept. Recording new structures is left to developer
 * builds ({@code structure.dev}).
 */
public final class StructureLogger {
    private static final Pattern TUNNEL_DONE = Pattern.compile("(?i)^\\s*Chronicle(?:\\s+Advanced!)?:?\\s*Tunnel\\s+Tier\\s+\\d+");
    private static final Pattern REFRESHED = Pattern.compile("(?i)\\[Harad\\]\\s*Camps/Loot/Tunnels/Caverns refreshed");
    private static final double COMPLETION_RANGE = 48.0;

    private StructureLogger() {
    }

    public static boolean inHarad(Minecraft minecraft) {
        return minecraft.level != null && minecraft.level.dimension() == SkirmishWaypoints.HARAD;
    }

    public static void onMessage(Component message) {
        Minecraft minecraft = Minecraft.getInstance();
        if (message == null || minecraft.player == null || minecraft.level == null) {
            return;
        }

        String text = message.getString().replaceAll("(?i)§[0-9a-fk-or]", "");
        if (REFRESHED.matcher(text).find()) {
            StructureLog.clearCompleted();
            return;
        }

        if (TUNNEL_DONE.matcher(text).find() && ArdaStore.data.tunnelCompletion && inHarad(minecraft)) {
            tunnelCompleted(minecraft);
        }
    }

    private static void tunnelCompleted(Minecraft minecraft) {
        BlockPos here = minecraft.player.blockPosition();
        StructureLog.Structure entry = StructureLog.nearest(StructureLog.Kind.TUNNEL, here, COMPLETION_RANGE);
        if (entry == null) {
            return;
        }

        entry.completedAt = System.currentTimeMillis();
        StructureLog.save();
        tell(minecraft, "Tunnel at " + coordinates(entry) + " complete.");
    }

    public static String coordinates(StructureLog.Structure entry) {
        return entry.x + ", " + entry.y + ", " + entry.z;
    }

    public static void tell(Minecraft minecraft, String text) {
        ArdaClient.LOG.info("Structures: {}", text);
        if (minecraft.player != null) {
            minecraft.player.sendSystemMessage(ArdaTheme.accentComponent("[Structures]").append(" ")
                .append(Component.literal(text).withColor(ArdaTheme.TEXT & 0xFFFFFF)));
        }
    }
}
