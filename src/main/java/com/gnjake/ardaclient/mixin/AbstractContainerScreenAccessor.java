package com.gnjake.ardaclient.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Where a container screen is drawn, so the currency crafting panel can sit beside the crafting table. */
@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {
    @Accessor("leftPos")
    int ardaClient$left();

    @Accessor("topPos")
    int ardaClient$top();

    @Accessor("imageWidth")
    int ardaClient$width();
}
