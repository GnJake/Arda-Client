package com.gnjake.ardaclient.structure.dev;

import com.gnjake.ardaclient.ArdaClient;
import com.gnjake.ardaclient.config.ArdaStore;
import com.gnjake.ardaclient.mixin.BossHealthOverlayAccessor;
import com.gnjake.ardaclient.structure.StructureLog;
import com.gnjake.ardaclient.structure.StructureLogger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.entity.TrialSpawnerBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Records Harad's structures for the bundled list, in developer builds only (release builds leave this package out):
 * <ul>
 *   <li>Camps and outposts: with a camp or outpost boss bar showing (or shown in the last two minutes), opening a chest
 *   logs that camp's number at the chest. A camp built on an outpost shows both bars, and one chest logs both.</li>
 *   <li>Tunnel spawners: every spawner in Harad is a tunnel's, so each one in the loaded chunks is logged, seen or not.</li>
 *   <li>Towers: right-clicking a lodestone asks for the tower's number and logs it at the lodestone.</li>
 * </ul>
 * Every new boss bar name is written to the log, so the patterns below can be checked against what Arda sends.
 */
public final class StructureRecorder {
    private static final Pattern CAMP = Pattern.compile("(?i)\\b(camp|outpost)\\b\\s*(?:#|no\\.?|number)?\\s*(\\d+)");
    /** A camp's boss bar may be gone by the time its loot chest is opened. */
    private static final long CAMP_MEMORY = 120_000L;
    private static final double CAMP_RANGE = 96.0;
    /** Spawners closer than this to a logged one are the same spawner. */
    private static final double SAME_SPAWNER = 6.0;
    /** The server may answer a lodestone click with its own menu; the tower prompt waits this long for it to close. */
    private static final long TOWER_PROMPT_WAIT = 30_000L;

    private static final Set<String> seenBars = new HashSet<>();
    /** Camp and outpost bars seen lately, by kind and number. */
    private static final Map<String, Sighting> sightings = new LinkedHashMap<>();
    private static int lastMenuId = Integer.MIN_VALUE;
    /** A lodestone waiting for its tower number, asked once no other screen is open. */
    private static BlockPos towerPrompt;
    private static long towerPromptAt;
    private static int ticks;

    private StructureRecorder() {
    }

    /** Called by reflection from {@code ArdaClient} when this developer-only class is present. */
    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(StructureRecorder::tick);
        UseBlockCallback.EVENT.register(StructureRecorder::onUseBlock);
    }

    /** The boss bars on screen, as plain text. */
    public static List<String> bossBars(Minecraft minecraft) {
        List<String> names = new ArrayList<>();
        BossHealthOverlayAccessor overlay = (BossHealthOverlayAccessor)minecraft.gui.hud.getBossOverlay();
        overlay.ardaClient$events().values().forEach(bar -> names.add(bar.getName().getString().replaceAll("(?i)§[0-9a-fk-or]", "").strip()));
        return names;
    }

    private static void tick(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;
        if (player == null || level == null) {
            return;
        }

        List<String> bars = bossBars(minecraft);
        for (String bar : bars) {
            // Countdowns and kill counts change every second; only log the bar once.
            if (seenBars.add(bar.replaceAll("\\s*\\([^)]*\\)", "").replaceAll("\\d+\\s*(minutes?|[hms]\\b)", "").strip())) {
                ArdaClient.LOG.info("Boss bar: {}", bar);
            }
        }

        if (!StructureLogger.inHarad(minecraft)) {
            return;
        }

        long now = System.currentTimeMillis();
        sightings.values().forEach(sighting -> sighting.showing = false);
        sightings.values().removeIf(sighting -> now - sighting.seenAt > CAMP_MEMORY);
        for (String bar : bars) {
            Matcher camp = CAMP.matcher(bar);
            if (!bar.toLowerCase(Locale.ROOT).contains("mirage") && camp.find()) {
                StructureLog.Kind kind = camp.group(1).equalsIgnoreCase("outpost") ? StructureLog.Kind.OUTPOST : StructureLog.Kind.CAMP;
                int number = Integer.parseInt(camp.group(2));
                sightings.remove(kind.id + number);
                sightings.put(kind.id + number, new Sighting(kind, number, now, player.blockPosition()));
            }
        }

        if (towerPrompt != null) {
            if (now - towerPromptAt > TOWER_PROMPT_WAIT) {
                towerPrompt = null;
            } else if (minecraft.gui.screen() == null) {
                minecraft.gui.setScreen(new TowerNumberScreen(towerPrompt));
                towerPrompt = null;
            }
        }

        AbstractContainerMenu menu = player.containerMenu;
        if (menu.containerId != lastMenuId) {
            lastMenuId = menu.containerId;
            if (menu != player.inventoryMenu && ArdaStore.data.campLogger) {
                chestOpened(minecraft, level, player, now);
            }
        }

        if (++ticks % 40 == 0 && ArdaStore.data.tunnelLogger) {
            for (BlockPos spawner : loadedSpawners(minecraft, level)) {
                if (StructureLog.nearest(StructureLog.Kind.TUNNEL, spawner, SAME_SPAWNER) == null) {
                    StructureLog.Structure entry = StructureLog.add(StructureLog.Kind.TUNNEL, 0, spawner);
                    StructureLogger.tell(minecraft, "Logged a tunnel spawner at " + StructureLogger.coordinates(entry) + ".");
                }
            }
        }
    }

    /**
     * Logs the camps and outposts around an opened chest: every one whose bar is showing, or else the one seen last.
     */
    private static void chestOpened(Minecraft minecraft, ClientLevel level, LocalPlayer player, long now) {
        if (sightings.isEmpty() || !(minecraft.hitResult instanceof BlockHitResult hit)) {
            return;
        }

        BlockPos pos = hit.getBlockPos();
        if (!(level.getBlockEntity(pos) instanceof Container)) {
            return;
        }

        List<Sighting> near = new ArrayList<>();
        for (Sighting sighting : sightings.values()) {
            if (pos.distSqr(sighting.pos) <= CAMP_RANGE * CAMP_RANGE) {
                near.add(sighting);
            }
        }

        List<Sighting> showing = near.stream().filter(sighting -> sighting.showing).toList();
        List<Sighting> chosen = !showing.isEmpty() ? showing : near.isEmpty() ? List.of() : List.of(near.getLast());
        for (Sighting sighting : chosen) {
            if (StructureLog.find(sighting.kind, sighting.number) == null) {
                StructureLog.Structure entry = StructureLog.add(sighting.kind, sighting.number, pos);
                StructureLogger.tell(minecraft, "Logged " + entry.name() + " at " + StructureLogger.coordinates(entry) + ".");
            }
        }
    }

    /** A right-click on a block: a lodestone in Harad asks for a tower number. The click still goes to the server. */
    private static InteractionResult onUseBlock(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
        Minecraft minecraft = Minecraft.getInstance();
        if (level.isClientSide() && hand == InteractionHand.MAIN_HAND && player == minecraft.player && ArdaStore.data.towerLogger
            && StructureLogger.inHarad(minecraft) && level.getBlockState(hit.getBlockPos()).is(Blocks.LODESTONE)) {
            towerPrompt = hit.getBlockPos().immutable();
            towerPromptAt = System.currentTimeMillis();
        }

        return InteractionResult.PASS;
    }

    static void towerLogged(StructureLog.Structure entry) {
        StructureLogger.tell(Minecraft.getInstance(), "Logged " + entry.name() + " at " + StructureLogger.coordinates(entry) + ".");
    }

    /** Every spawner in the chunks the client has loaded, found by their block entities, so walls don't matter. */
    public static List<BlockPos> loadedSpawners(Minecraft minecraft, ClientLevel level) {
        List<BlockPos> found = new ArrayList<>();
        ChunkPos center = ChunkPos.containing(minecraft.player.blockPosition());
        int radius = minecraft.options.getEffectiveRenderDistance() + 1;
        for (int x = center.x() - radius; x <= center.x() + radius; x++) {
            for (int z = center.z() - radius; z <= center.z() + radius; z++) {
                LevelChunk chunk = level.getChunkSource().getChunk(x, z, ChunkStatus.FULL, false);
                if (chunk != null) {
                    chunk.getBlockEntities().forEach((pos, entity) -> {
                        if (entity instanceof SpawnerBlockEntity || entity instanceof TrialSpawnerBlockEntity) {
                            found.add(pos.immutable());
                        }
                    });
                }
            }
        }

        return found;
    }

    /** A camp or outpost bar: where the player was when it last showed. */
    private static final class Sighting {
        final StructureLog.Kind kind;
        final int number;
        final long seenAt;
        final BlockPos pos;
        boolean showing = true;

        Sighting(StructureLog.Kind kind, int number, long seenAt, BlockPos pos) {
            this.kind = kind;
            this.number = number;
            this.seenAt = seenAt;
            this.pos = pos;
        }
    }
}
