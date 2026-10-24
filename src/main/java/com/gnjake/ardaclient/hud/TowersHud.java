package com.gnjake.ardaclient.hud;

import com.gnjake.ardaclient.config.ArdaStore;
import com.gnjake.ardaclient.gui.ArdaTheme;
import com.gnjake.ardaclient.structure.TowerEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** During Arda's Towers Event: who holds each tower and how many each clan has, then the final standings. */
public final class TowersHud extends HudPanel {
    public static final TowersHud INSTANCE = new TowersHud();
    private static final int ROW = 10;
    private static final int TITLE = 14;
    /** Shown in the HUD editor when no event is running. */
    private boolean preview;

    private TowersHud() {
    }

    @Override
    public boolean enabled() {
        return ArdaStore.data.towersHud;
    }

    @Override
    public boolean shown(long now) {
        return super.shown(now) && (this.preview || !ArdaStore.data.towersOnlyDuringEvent || TowerEvent.shown(now));
    }

    /** "Towers: Event", "Towers: Always" or "Towers: Off". */
    public static String modeLabel() {
        return "Towers: " + (!ArdaStore.data.towersHud ? "Off" : ArdaStore.data.towersOnlyDuringEvent ? "Event" : "Always");
    }

    /** Steps through only during the event, always, and off. */
    public static void cycleMode() {
        if (!ArdaStore.data.towersHud) {
            ArdaStore.data.towersHud = true;
            ArdaStore.data.towersOnlyDuringEvent = true;
        } else if (ArdaStore.data.towersOnlyDuringEvent) {
            ArdaStore.data.towersOnlyDuringEvent = false;
        } else {
            ArdaStore.data.towersHud = false;
        }

        ArdaStore.save();
    }

    /** Shows the panel even without an event, so it can be placed. */
    public void preview(boolean preview) {
        this.preview = preview;
    }

    /** The panel's lines below the title: {left, right, color}. */
    private static List<String[]> lines(long now) {
        List<String[]> lines = new ArrayList<>();
        if (TowerEvent.active() || TowerEvent.standings().isEmpty()) {
            for (int tower = 1; tower <= TowerEvent.TOWERS; tower++) {
                String clan = TowerEvent.holder(tower);
                lines.add(new String[]{"Tower " + tower, clan.isEmpty() ? "None" : clan, clan.isEmpty() ? "dim" : "clan"});
            }

            for (TowerEvent.Clan clan : TowerEvent.clans()) {
                lines.add(new String[]{clan.name(), clan.towers() + (clan.towers() == 1 ? " tower" : " towers"), "count"});
            }
        } else {
            for (TowerEvent.Standing standing : TowerEvent.standings()) {
                lines.add(new String[]{standing.place() + ". " + standing.clan(), String.format(Locale.ROOT, "%,d", standing.points()), "points"});
            }
        }

        return lines;
    }

    private static String title(long now) {
        long remaining = TowerEvent.remaining(now);
        if (remaining >= 0L) {
            return "Towers " + remaining / 60_000L + ":" + String.format(Locale.ROOT, "%02d", remaining / 1000L % 60L);
        }

        return TowerEvent.active() || TowerEvent.winner().isEmpty() ? "Towers" : "Towers: " + TowerEvent.winner() + " won";
    }

    @Override
    protected double scale() {
        return ArdaStore.data.towersScale;
    }

    @Override
    protected int contentWidth(Font font, long now) {
        int width = font.width(title(now));
        for (String[] line : lines(now)) {
            width = Math.max(width, font.width(line[0]) + 12 + font.width(line[1]));
        }

        width = Math.max(width, font.width(TowerEvent.prompt(now)) - 7);

        return width + PAD * 2 + 7;
    }

    @Override
    protected int contentHeight(Font font, long now) {
        return TITLE + lines(now).size() * ROW + 3 + (TowerEvent.prompt(now).isEmpty() ? 0 : ROW + 2);
    }

    @Override
    protected int left(int screenWidth, int screenHeight, long now) {
        return ArdaStore.data.towersDocked ? dockedLeft(above(now), screenWidth, screenHeight, now) : ArdaStore.data.towersLeft;
    }

    @Override
    protected int top(int screenWidth, int screenHeight, long now) {
        return ArdaStore.data.towersDocked ? dockedTop(above(now), screenWidth, screenHeight, now) : ArdaStore.data.towersTop;
    }

    /** The lowest panel above it in the default column. */
    private static HudPanel above(long now) {
        if (MithrilTallyHud.INSTANCE.shown(now)) {
            return MithrilTallyHud.INSTANCE;
        }

        return MithrilHud.INSTANCE.shown(now) ? MithrilHud.INSTANCE : MithrilHud.above(now);
    }

    @Override
    protected void draw(GuiGraphicsExtractor graphics, Font font, int width, int height, long now) {
        ArdaTheme.frame(graphics, 0, 0, width, height, opacity());
        ArdaTheme.mark(graphics, PAD + 2, 6);
        ArdaTheme.accentText(graphics, font, title(now), PAD + 7, 4, false);
        List<String[]> lines = lines(now);
        for (int index = 0; index < lines.size(); index++) {
            String[] line = lines.get(index);
            int y = TITLE + index * ROW;
            boolean towerRow = line[2].equals("dim") || line[2].equals("clan");
            graphics.text(font, line[0], PAD + 7, y, towerRow ? ArdaTheme.MUTED : ArdaTheme.TEXT);
            int color = line[2].equals("dim") ? ArdaTheme.DIM : line[2].equals("clan") ? ArdaTheme.accentBrightAt(index / (double)TowerEvent.TOWERS) : ArdaTheme.AMBER;
            graphics.text(font, line[1], width - PAD - font.width(line[1]), y, color);
        }

        String prompt = TowerEvent.prompt(now);
        if (!prompt.isEmpty()) {
            int y = TITLE + lines.size() * ROW;
            graphics.fill(1, y, width - 1, y + 1, ArdaTheme.fade(ArdaTheme.DIVIDER, opacity()));
            graphics.text(font, prompt, PAD, y + 3, ArdaTheme.MUTED);
        }
    }

    @Override
    public void moveTo(int x, int y) {
        ArdaStore.data.towersDocked = false;
        ArdaStore.data.towersLeft = Math.max(0, x);
        ArdaStore.data.towersTop = Math.max(0, y);
    }

    @Override
    public void reset() {
        ArdaStore.data.towersDocked = true;
        ArdaStore.data.towersLeft = ArdaStore.DEFAULT_LEFT;
        ArdaStore.data.towersTop = ArdaStore.DEFAULT_TOP;
    }
}
