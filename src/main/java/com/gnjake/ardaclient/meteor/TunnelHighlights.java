package com.gnjake.ardaclient.meteor;

import com.gnjake.ardaclient.config.ArdaStore;
import com.gnjake.ardaclient.gui.ArdaTheme;
import com.gnjake.ardaclient.structure.StructureLog;
import com.gnjake.ardaclient.structure.StructureLogger;
import com.gnjake.ardaclient.structure.TunnelPathfinder;
import java.util.List;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

/**
 * Tunnel X-ray, drawn with Meteor's renderer: logged tunnel spawners are outlined through walls (open ones in the
 * accent color, completed ones dimmed), and the path to the next one is drawn as highlighted floor blocks.
 */
public final class TunnelHighlights {
    private static final double RANGE = 256.0;
    private static final int PATH_RANGE = 64;

    @EventHandler
    private void onRender(Render3DEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!ArdaStore.data.tunnelXray || minecraft.player == null || !StructureLogger.inHarad(minecraft)) {
            return;
        }

        BlockPos here = minecraft.player.blockPosition();
        StructureLog.Structure target = TunnelPathfinder.target();
        for (StructureLog.Structure entry : StructureLog.of(StructureLog.Kind.TUNNEL)) {
            BlockPos pos = entry.pos();
            if (!pos.closerThan(here, RANGE)) {
                continue;
            }

            int rgb = entry.completed() ? ArdaTheme.DIM : entry == target ? ArdaTheme.accentBright() : ArdaTheme.accent();
            event.renderer.box(pos, color(rgb, entry.completed() ? 20 : 50), color(rgb, entry.completed() ? 120 : 255), ShapeMode.Both, 0);
        }

        List<BlockPos> path = TunnelPathfinder.path();
        if (!ArdaStore.data.tunnelPath || path.isEmpty()) {
            return;
        }

        int size = path.size();
        for (int index = 0; index < size; index++) {
            BlockPos node = path.get(index);
            if (node.closerThan(here, PATH_RANGE)) {
                int rgb = ArdaTheme.accentAt(size <= 1 ? 0.0 : index / (double)(size - 1));
                // A thin slab on the floor of each block along the way.
                event.depthRenderer.box(node.getX(), node.getY() + 0.02, node.getZ(), node.getX() + 1, node.getY() + 0.12, node.getZ() + 1,
                    color(rgb, 90), color(rgb, 200), ShapeMode.Both, 0);
            }
        }

        TunnelPathfinder.highlighted();
    }

    private static Color color(int rgb, int alpha) {
        return new Color((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, alpha);
    }
}
