package com.gnjake.ardaclient.wheel;

import com.gnjake.ardaclient.ArdaClient;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.InputConstants.Key;
import com.mojang.blaze3d.platform.InputConstants.Type;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

public final class QuickWheel {
    private static boolean suppressed;
    private static boolean heldScreen;

    private QuickWheel() {
    }

    public static void tick(Minecraft minecraft) {
        KeyMapping key = ArdaClient.wheelKey;
        if (key != null) {
            boolean down = physicalDown(minecraft, key.saveString()) || key.isDown();
            if (!down) {
                if (heldScreen && minecraft.gui.screen() instanceof WheelScreen wheel) {
                    wheel.select();
                }

                heldScreen = false;
                suppressed = false;
            } else if (minecraft.gui.screen() instanceof WheelScreen) {
                heldScreen = true;
            } else {
                if (!suppressed && minecraft.gui.screen() == null && minecraft.player != null) {
                    minecraft.gui.setScreen(new WheelScreen());
                    heldScreen = true;
                }
            }
        }
    }

    private static boolean physicalDown(Minecraft minecraft, String saved) {
        try {
            Key bound = InputConstants.getKey(saved);
            if (bound.getType() == Type.MOUSE) {
                return GLFW.glfwGetMouseButton(minecraft.getWindow().handle(), bound.getValue()) == GLFW.GLFW_PRESS;
            }

            if (bound.getType() == Type.KEYSYM) {
                return InputConstants.isKeyDown(minecraft.getWindow(), bound.getValue());
            }
        } catch (Exception ignored) {
            // An unparsable binding just means the key isn't held.
        }

        return false;
    }

    public static void suppress() {
        suppressed = true;
        heldScreen = false;
    }
}
