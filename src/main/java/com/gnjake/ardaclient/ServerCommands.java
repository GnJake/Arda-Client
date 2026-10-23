package com.gnjake.ardaclient;

import net.minecraft.client.Minecraft;

/** Sends commands to the server. Only single, short commands go out, so a shortcut can't chain several. */
public final class ServerCommands {
    public static final int MAX_LENGTH = 256;

    private ServerCommands() {
    }

    public static boolean isSafe(String command) {
        return !command.isBlank()
            && !command.contains("\n")
            && !command.contains("\r")
            && !command.contains(";")
            && command.length() <= MAX_LENGTH;
    }

    public static void run(String raw) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getConnection() == null) {
            return;
        }

        String command = raw == null ? "" : raw.trim();
        if (command.startsWith("/")) {
            command = command.substring(1);
        }

        if (isSafe(command)) {
            minecraft.getConnection().sendCommand(command);
        }
    }
}
