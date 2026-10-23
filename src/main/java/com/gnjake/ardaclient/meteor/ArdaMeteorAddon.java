package com.gnjake.ardaclient.meteor;

import com.gnjake.ardaclient.ArdaClient;
import java.util.List;
import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/**
 * Entry point Meteor Client calls when it is installed. Everything that touches Meteor's API lives in this package,
 * so Arda Client loads normally without Meteor. Adds an Arda Client module category and Arda Client HUD elements.
 */
public class ArdaMeteorAddon extends MeteorAddon {
    @Override
    public void onInitialize() {
        ArdaClient.LOG.info("Meteor Client detected, enabling Meteor features");
        List<ArdaModule> modules = ArdaModule.create();
        modules.forEach(Modules.get()::add);
        ClientTickEvents.END_CLIENT_TICK.register(client -> modules.forEach(ArdaModule::sync));

        Hud.get().register(ArdaHudElement.DUNGEONS);
        Hud.get().register(ArdaHudElement.CURRENCY);
        Hud.get().register(ArdaHudElement.TALLY);
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(ArdaModule.CATEGORY);
    }

    @Override
    public String getPackage() {
        return "com.gnjake.ardaclient.meteor";
    }

    @Override
    public GithubRepo getRepo() {
        return new GithubRepo("GnJake", "Arda-Client");
    }
}
