package com.gnjake.ardaclient.meteor;

import com.gnjake.ardaclient.ArdaClient;
import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;

/**
 * Entry point Meteor Client calls when it is installed. Everything that touches Meteor's API lives in this package,
 * so Arda Client loads normally without Meteor.
 */
public class ArdaMeteorAddon extends MeteorAddon {
    @Override
    public void onInitialize() {
        ArdaClient.LOG.info("Meteor Client detected, enabling Meteor features");
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
