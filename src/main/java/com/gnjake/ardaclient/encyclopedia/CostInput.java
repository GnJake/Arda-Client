package com.gnjake.ardaclient.encyclopedia;

import java.util.List;
import net.minecraft.client.gui.components.EditBox;

/** The four currency boxes on the draft screens. Any mix of currencies can be entered. */
final class CostInput {
    private static final long MAX = 1000000000L;

    private final EditBox bloodstones;
    private final EditBox zircon;
    private final EditBox forgedTungsten;
    private final EditBox blackGems;

    CostInput(EditBox bloodstones, EditBox zircon, EditBox forgedTungsten, EditBox blackGems) {
        this.bloodstones = bloodstones;
        this.zircon = zircon;
        this.forgedTungsten = forgedTungsten;
        this.blackGems = blackGems;
    }

    private List<EditBox> boxes() {
        return List.of(this.bloodstones, this.zircon, this.forgedTungsten, this.blackGems);
    }

    void onChange(Runnable action) {
        for (EditBox box : this.boxes()) {
            box.setResponder(value -> action.run());
        }
    }

    void setActive(boolean active) {
        for (EditBox box : this.boxes()) {
            box.active = active;
        }
    }

    /** The entered cost, or null if a box isn't a whole number up to a billion or every box is zero. */
    PriceBook.Cost read() {
        Long bs = count(this.bloodstones);
        Long zr = count(this.zircon);
        Long ft = count(this.forgedTungsten);
        Long bg = count(this.blackGems);
        if (bs == null || zr == null || ft == null || bg == null) {
            return null;
        }

        PriceBook.Cost cost = new PriceBook.Cost();
        cost.bloodstones = bs;
        cost.zircon = zr;
        cost.forgedTungsten = ft;
        cost.blackGems = bg;
        return cost.empty() ? null : cost;
    }

    private static Long count(EditBox box) {
        String text = box.getValue().trim();
        try {
            long n = text.isEmpty() ? 0L : Long.parseLong(text);
            return n >= 0L && n <= MAX ? n : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
