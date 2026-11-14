package com.gnjake.ardaclient.hud;

import java.util.function.Supplier;

/** A value a panel works out at most once a client tick, such as its lines of text, and draws every frame. */
final class PerTick<T> {
    private T value;
    private long at = -1L;

    T get(Supplier<T> compute) {
        long tick = HudPanel.currentTick();
        if (this.at != tick) {
            this.value = compute.get();
            this.at = tick;
        }

        return this.value;
    }
}
