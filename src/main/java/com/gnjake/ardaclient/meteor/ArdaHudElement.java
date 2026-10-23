package com.gnjake.ardaclient.meteor;

import com.gnjake.ardaclient.hud.CurrencyHud;
import com.gnjake.ardaclient.hud.DungeonHud;
import com.gnjake.ardaclient.hud.HudPanel;
import com.gnjake.ardaclient.hud.TallyHud;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudGroup;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import net.minecraft.client.Minecraft;

/**
 * Shows an Arda Client panel in Meteor's HUD editor, so it can be placed alongside the rest of a Meteor HUD. While a
 * Meteor element draws a panel, Arda Client's own copy of it is hidden.
 */
public final class ArdaHudElement extends HudElement {
    public static final HudGroup GROUP = new HudGroup("Arda Client");

    public static final HudElementInfo<ArdaHudElement> DUNGEONS = new HudElementInfo<>(GROUP, "arda-dungeons", "Dungeons",
        "Dungeon timers from /dungeon_info.", () -> new ArdaHudElement(ArdaHudElement.DUNGEONS, DungeonHud.INSTANCE));
    public static final HudElementInfo<ArdaHudElement> CURRENCY = new HudElementInfo<>(GROUP, "arda-currency", "Currency Counter",
        "Your currency in Black Gems and Bloodstones.", () -> new ArdaHudElement(ArdaHudElement.CURRENCY, CurrencyHud.INSTANCE));
    public static final HudElementInfo<ArdaHudElement> TALLY = new HudElementInfo<>(GROUP, "arda-tally", "Currency Tally",
        "Currency earned over the last hour, day and week.", () -> new ArdaHudElement(ArdaHudElement.TALLY, TallyHud.INSTANCE));

    private final HudPanel panel;
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final Setting<Double> scale = this.sgGeneral.add(new DoubleSetting.Builder()
        .name("scale")
        .description("Size of the panel. 1 matches Minecraft's GUI scale.")
        .defaultValue(1.0)
        .min(0.25)
        .sliderRange(0.25, 3.0)
        .build()
    );

    private ArdaHudElement(HudElementInfo<ArdaHudElement> info, HudPanel panel) {
        super(info);
        this.panel = panel;
    }

    @Override
    public void render(HudRenderer renderer) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null && !this.isInEditor()) {
            return;
        }

        long now = System.currentTimeMillis();
        // Meteor draws in screen pixels, while panels are laid out in GUI pixels.
        double size = this.scale.get() * minecraft.getWindow().getGuiScale();
        this.setSize(this.panel.width(now) * size, this.panel.height(now) * size);
        this.panel.drawAt(renderer.graphics, this.x, this.y, size, now);
        this.panel.markDrawnExternally(now);
    }
}
