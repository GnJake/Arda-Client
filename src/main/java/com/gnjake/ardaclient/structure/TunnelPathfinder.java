package com.gnjake.ardaclient.structure;

import com.gnjake.ardaclient.config.ArdaStore;
import com.gnjake.ardaclient.gui.ArdaTheme;
import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.PriorityQueue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Finds a walking path from the player to the nearest tunnel spawner not yet completed, with A* over the blocks the
 * client has loaded, and marks it with a trail of particles. The search runs a little each tick so it never stalls
 * the game. A spawner beyond the loaded area gets a path to the loaded block closest to it, extended as the player
 * walks. It only runs underground.
 */
public final class TunnelPathfinder {
    /** Nodes expanded per tick. */
    private static final int BUDGET = 2000;
    private static final int MAX_NODES = 120_000;
    private static final long RECOMPUTE_AFTER = 15_000L;
    /** The path is found again once the player is this far from where it started. */
    private static final double MOVED = 6.0;
    /** Close enough to the spawner to count as there. */
    private static final double ARRIVED = 3.0;
    private static final int DRAW_RANGE = 48;
    /** Blocks of ground overhead that count as being underground. */
    private static final int UNDERGROUND_DEPTH = 6;
    private static final int[][] DIRECTIONS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
    /** Height changes tried for each step, in order: level, step up, then drops of up to three blocks. */
    private static final int[] HEIGHTS = {0, 1, -1, -2, -3};

    private static Search search;
    private static List<BlockPos> path = List.of();
    private static boolean complete;
    private static StructureLog.Structure target;
    private static BlockPos startedAt = BlockPos.ZERO;
    private static long foundAt;
    private static int ticks;
    /** When another renderer (Meteor's) last drew the path as blocks; particles are skipped meanwhile. */
    private static long highlightedAt;

    private TunnelPathfinder() {
    }

    /** A short description of the path, for the structures screen. */
    public static String status() {
        if (!ArdaStore.data.tunnelPath) {
            return "Path to next tunnel: Off";
        }

        if (minecraft().player != null && !underground(minecraft().level, minecraft().player.blockPosition())) {
            return "Path to next tunnel: shown once you are underground.";
        }

        if (target == null) {
            return "No tunnel left to run. Log tunnel spawners, or wait for Harad to refresh.";
        }

        if (search != null && path.isEmpty()) {
            return "Finding a path to " + target.name() + "...";
        }

        String where = target.name() + " at " + StructureLogger.coordinates(target);
        return complete ? path.size() + " blocks to " + where : "Partial path toward " + where + " (beyond loaded area)";
    }

    public static void tick(Minecraft minecraft) {
        if (!ArdaStore.data.tunnelPath || minecraft.player == null || minecraft.level == null || !StructureLogger.inHarad(minecraft)) {
            clear();
            return;
        }

        BlockPos here = minecraft.player.blockPosition();
        if (!underground(minecraft.level, here)) {
            clear();
            return;
        }

        StructureLog.Structure next = nextTunnel(here);
        if (next == null || next.pos().closerThan(here, ARRIVED)) {
            target = next;
            path = List.of();
            search = null;
            return;
        }

        long now = System.currentTimeMillis();
        boolean stale = next != target || !here.closerThan(startedAt, MOVED) || now - foundAt > RECOMPUTE_AFTER;
        if (search == null && stale) {
            target = next;
            startedAt = here;
            search = new Search(minecraft.level, here, next.pos());
        }

        if (search != null && search.step(BUDGET)) {
            path = search.result();
            complete = search.reached;
            foundAt = now;
            search = null;
        }

        if (++ticks % 6 == 0 && now - highlightedAt > 1000L) {
            draw(minecraft.level, here);
        }
    }

    /** Tunnels are underground; on the surface the path would only lead into the ground. */
    private static boolean underground(ClientLevel level, BlockPos pos) {
        return !level.canSeeSky(pos.above()) && level.getHeight(Heightmap.Types.WORLD_SURFACE, pos.getX(), pos.getZ()) - pos.getY() > UNDERGROUND_DEPTH;
    }

    /** The current path, from the player toward the next tunnel. */
    public static List<BlockPos> path() {
        return path;
    }

    /** The tunnel the path leads to, or null. */
    public static StructureLog.Structure target() {
        return target;
    }

    /** Called by a renderer that drew the path as highlighted blocks, so the particle trail stops. */
    public static void highlighted() {
        highlightedAt = System.currentTimeMillis();
    }

    private static Minecraft minecraft() {
        return Minecraft.getInstance();
    }

    private static void clear() {
        search = null;
        path = List.of();
        target = null;
    }

    /** The closest logged tunnel spawner that hasn't been completed since Harad refreshed. */
    private static StructureLog.Structure nextTunnel(BlockPos from) {
        StructureLog.Structure best = null;
        double bestDistance = Double.MAX_VALUE;
        for (StructureLog.Structure entry : StructureLog.of(StructureLog.Kind.TUNNEL)) {
            double distance = entry.pos().distSqr(from);
            if (!entry.completed() && distance < bestDistance) {
                best = entry;
                bestDistance = distance;
            }
        }

        return best;
    }

    private static void draw(ClientLevel level, BlockPos here) {
        int size = path.size();
        for (int index = 0; index < size; index++) {
            BlockPos node = path.get(index);
            if (node.closerThan(here, DRAW_RANGE)) {
                int color = ArdaTheme.accentAt(size <= 1 ? 0.0 : index / (double)(size - 1)) & 0xFFFFFF;
                level.addParticle(new DustParticleOptions(color, 2.5F), node.getX() + 0.5, node.getY() + 0.3, node.getZ() + 0.5, 0.0, 0.0, 0.0);
            }
        }
    }

    /** A* from the player's feet to any block next to the spawner. */
    private static final class Search {
        private final ClientLevel level;
        private final BlockPos goal;
        private final PriorityQueue<Node> open = new PriorityQueue<>();
        private final Long2DoubleOpenHashMap cost = new Long2DoubleOpenHashMap();
        private final Long2LongOpenHashMap cameFrom = new Long2LongOpenHashMap();
        private final LongOpenHashSet closed = new LongOpenHashSet();
        private long best;
        private double bestDistance = Double.MAX_VALUE;
        private long end;
        private boolean reached;

        Search(ClientLevel level, BlockPos start, BlockPos goal) {
            this.level = level;
            this.goal = goal;
            long key = start.asLong();
            this.cost.defaultReturnValue(Double.MAX_VALUE);
            this.cost.put(key, 0.0);
            this.open.add(new Node(key, this.estimate(start)));
            this.best = key;
            this.end = key;
        }

        /** Expands up to {@code budget} nodes, returning true once the search is over. */
        boolean step(int budget) {
            for (int count = 0; count < budget; count++) {
                Node node = this.open.poll();
                if (node == null || this.closed.size() >= MAX_NODES) {
                    this.end = this.best;
                    return true;
                }

                if (!this.closed.add(node.pos)) {
                    continue;
                }

                BlockPos pos = BlockPos.of(node.pos);
                double distance = this.estimate(pos);
                if (distance < this.bestDistance) {
                    this.bestDistance = distance;
                    this.best = node.pos;
                }

                if (Math.abs(pos.getY() - this.goal.getY()) <= 3 && horizontal(pos, this.goal) <= 2.5) {
                    this.end = node.pos;
                    this.reached = true;
                    return true;
                }

                this.expand(pos, node.pos);
            }

            return false;
        }

        private void expand(BlockPos pos, long key) {
            double here = this.cost.get(key);
            for (int[] direction : DIRECTIONS) {
                boolean diagonal = direction[0] != 0 && direction[1] != 0;
                if (diagonal && !(this.clear(pos.offset(direction[0], 0, 0)) && this.clear(pos.offset(0, 0, direction[1])))) {
                    continue;
                }

                for (int height : HEIGHTS) {
                    BlockPos next = pos.offset(direction[0], height, direction[1]);
                    if (!this.canStep(pos, next, height)) {
                        continue;
                    }

                    double step = (diagonal ? 1.414 : 1.0) + (height > 0 ? 0.6 : -height * 0.3);
                    this.consider(key, next, here + step);
                    break;
                }
            }

            if (this.climbable(pos)) {
                for (int height : new int[]{1, -1}) {
                    BlockPos next = pos.above(height);
                    if (this.standable(next) || this.climbable(next)) {
                        this.consider(key, next, here + 1.2);
                    }
                }
            }
        }

        private boolean canStep(BlockPos from, BlockPos to, int height) {
            if (!this.standable(to)) {
                return false;
            }

            if (height > 0) {
                // Jumping up needs headroom above the current spot.
                return this.passable(from.above(2));
            }

            // Dropping down needs the space above the landing spot to be open all the way down.
            for (int y = 0; y > height; y--) {
                if (!this.clear(to.above(-height + y))) {
                    return false;
                }
            }

            return true;
        }

        private void consider(long from, BlockPos next, double total) {
            long key = next.asLong();
            if (total < this.cost.get(key) && !this.closed.contains(key)) {
                this.cost.put(key, total);
                this.cameFrom.put(key, from);
                this.open.add(new Node(key, total + this.estimate(next)));
            }
        }

        private double estimate(BlockPos pos) {
            double dx = Math.abs(pos.getX() - this.goal.getX());
            double dz = Math.abs(pos.getZ() - this.goal.getZ());
            return Math.max(dx, dz) + 0.414 * Math.min(dx, dz) + Math.abs(pos.getY() - this.goal.getY()) * 0.8;
        }

        private static double horizontal(BlockPos a, BlockPos b) {
            return Math.hypot(a.getX() - b.getX(), a.getZ() - b.getZ());
        }

        /** Somewhere the player can stand: two open blocks with something to stand on, or a ladder or water. */
        private boolean standable(BlockPos pos) {
            if (!this.level.isLoaded(pos) || !this.clear(pos)) {
                return false;
            }

            BlockState below = this.level.getBlockState(pos.below());
            return !below.getCollisionShape(this.level, pos.below()).isEmpty() && !below.getFluidState().is(FluidTags.LAVA)
                || this.climbable(pos) || this.level.getFluidState(pos).is(FluidTags.WATER);
        }

        /** Room for the player's feet and head. */
        private boolean clear(BlockPos pos) {
            return this.passable(pos) && this.passable(pos.above());
        }

        private boolean passable(BlockPos pos) {
            BlockState state = this.level.getBlockState(pos);
            return state.getCollisionShape(this.level, pos).isEmpty() && !state.getFluidState().is(FluidTags.LAVA);
        }

        private boolean climbable(BlockPos pos) {
            return this.level.getBlockState(pos).is(BlockTags.CLIMBABLE);
        }

        List<BlockPos> result() {
            List<BlockPos> nodes = new ArrayList<>();
            long key = this.end;
            nodes.add(BlockPos.of(key));
            while (this.cameFrom.containsKey(key)) {
                key = this.cameFrom.get(key);
                nodes.add(BlockPos.of(key));
            }

            Collections.reverse(nodes);
            return List.copyOf(nodes);
        }

        private record Node(long pos, double score) implements Comparable<Node> {
            @Override
            public int compareTo(Node other) {
                return Double.compare(this.score, other.score);
            }
        }
    }
}
