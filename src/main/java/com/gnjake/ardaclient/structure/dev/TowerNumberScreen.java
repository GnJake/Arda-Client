package com.gnjake.ardaclient.structure.dev;

import com.gnjake.ardaclient.gui.ArdaPanelScreen;
import com.gnjake.ardaclient.gui.ArdaTheme;
import com.gnjake.ardaclient.structure.StructureLog;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.BlockPos;
import org.lwjgl.glfw.GLFW;

/** Asks which tower a right-clicked lodestone belongs to, then logs it there. */
public final class TowerNumberScreen extends ArdaPanelScreen {
    private final BlockPos lodestone;
    private EditBox number;
    private String error = "";

    public TowerNumberScreen(BlockPos lodestone) {
        super("Log Tower", null);
        this.lodestone = lodestone;
    }

    @Override
    protected void init() {
        this.layout(220, 110);
        StructureLog.Structure existing = StructureLog.nearest(StructureLog.Kind.TOWER, this.lodestone, 2.0);
        String initial = existing == null ? "" : String.valueOf(existing.number);
        this.number = this.textField(this.px + 100, this.py + 39, this.pw - 112, "Number", initial, 4);
        this.setInitialFocus(this.number);
        int half = (this.pw - 30) / 2;
        this.button("Save", this.px + 10, this.py + 80, half, this::save);
        this.button("Cancel", this.px + 20 + half, this.py + 80, half, this::onClose);
    }

    private void save() {
        String text = this.number.getValue().strip();
        if (!text.matches("\\d{1,4}") || Integer.parseInt(text) <= 0) {
            this.error = "Enter the tower's number.";
            return;
        }

        StructureRecorder.towerLogged(StructureLog.setTower(Integer.parseInt(text), this.lodestone));
        this.onClose();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
            this.save();
            return true;
        }

        return super.keyPressed(event);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.text(this.font, "Tower number", this.px + 12, this.py + 45, ArdaTheme.MUTED);
        String line = this.error.isEmpty() ? "Lodestone at " + this.lodestone.getX() + ", " + this.lodestone.getY() + ", " + this.lodestone.getZ() : this.error;
        graphics.text(this.font, line, this.px + 12, this.py + 66, this.error.isEmpty() ? ArdaTheme.DIM : ArdaTheme.AMBER);
    }
}
