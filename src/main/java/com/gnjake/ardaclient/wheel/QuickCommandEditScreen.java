package com.gnjake.ardaclient.wheel;

import com.gnjake.ardaclient.ServerCommands;
import com.gnjake.ardaclient.config.ArdaStore;
import com.gnjake.ardaclient.gui.ArdaPanelScreen;
import com.gnjake.ardaclient.gui.ArdaTheme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

public final class QuickCommandEditScreen extends ArdaPanelScreen {
    private final ArdaStore.QuickCommand existing;
    private EditBox name;
    private EditBox command;
    private EditBox icon;

    public QuickCommandEditScreen(Screen parent, ArdaStore.QuickCommand existing) {
        super(existing == null ? "New Command" : "Edit Command", parent);
        this.existing = existing;
    }

    @Override
    protected void init() {
        this.layout(370, 185);
        ArdaStore.QuickCommand current = this.existing == null ? new ArdaStore.QuickCommand() : this.existing;
        int x = this.px + 100;
        int width = this.pw - 112;
        int y = this.py + 39;
        this.name = this.textField(x, y, width, "", current.name, 64);
        this.command = this.textField(x, y + 38, width, "", current.command, ServerCommands.MAX_LENGTH);
        this.icon = this.textField(x, y + 76, width, "", current.icon, 128);
        this.button("Save", this.px + 12, this.py + 149, 163, this::save);
        this.button("Cancel", this.px + 195, this.py + 149, 163, this::onClose);
    }

    private void save() {
        String nameText = this.name.getValue().trim();
        String commandText = this.command.getValue().trim();
        String iconId = this.icon.getValue().trim();
        if (!nameText.isEmpty() && ServerCommands.isSafe(commandText)) {
            if (!iconId.isEmpty()) {
                try {
                    if (BuiltInRegistries.ITEM.getValue(Identifier.parse(iconId)) == null) {
                        return;
                    }
                } catch (Exception ignored) {
                    return;
                }
            }

            ArdaStore.QuickCommand value = this.existing == null ? new ArdaStore.QuickCommand() : this.existing;
            value.name = nameText;
            value.command = commandText;
            value.icon = iconId;
            if (this.existing == null) {
                value.order = ArdaStore.data.commands.size();
                ArdaStore.data.commands.add(value);
            }

            ArdaStore.save();
            this.onClose();
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.text(this.font, "Name", this.px + 12, this.py + 45, ArdaTheme.MUTED);
        graphics.text(this.font, "Command", this.px + 12, this.py + 83, ArdaTheme.MUTED);
        graphics.text(this.font, "Icon ID", this.px + 12, this.py + 121, ArdaTheme.MUTED);
    }
}
