package cn.aatu.ui;

import net.minecraft.client.gui.GuiGraphics;

public final class HudTransform {
    private HudTransform() {}

    public static void apply(GuiGraphics graphics, HudPart part, float x, float y) {
        apply(graphics, part, x, y, x, y);
    }

    public static void apply(GuiGraphics graphics, HudPart part, float x, float y, float sourceX, float sourceY) {
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(part.scale(), part.scale(), 1);
        graphics.pose().translate(-sourceX, -sourceY, 0);
    }

    public static void undo(GuiGraphics graphics, HudPart part, float x, float y, float sourceX, float sourceY) {
        // Affixes share the champion render call but have their own position and scale.
        graphics.pose().translate(sourceX, sourceY, 0);
        graphics.pose().scale(1 / part.scale(), 1 / part.scale(), 1);
        graphics.pose().translate(-x, -y, 0);
    }
}
