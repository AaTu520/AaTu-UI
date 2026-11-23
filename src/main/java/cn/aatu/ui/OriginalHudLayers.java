package cn.aatu.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

public final class OriginalHudLayers {
    private static int armorBottomOffset = 49;

    private OriginalHudLayers() {}

    public static int armorY(int height) {
        return height - armorBottomOffset;
    }

    public static void clear() {
        armorBottomOffset = 49;
    }

    public static void render(ResourceLocation layer, GuiGraphics graphics, Runnable original) {
        Minecraft minecraft = Minecraft.getInstance();
        HudPart part;
        int x = graphics.guiWidth() / 2 - 91;
        int y;
        if (layer.equals(VanillaGuiLayers.ARMOR_LEVEL)) {
            part = HudPart.ARMOR;
            armorBottomOffset = minecraft.gui.leftHeight;
            y = graphics.guiHeight() - armorBottomOffset;
        } else if (layer.equals(VanillaGuiLayers.PLAYER_HEALTH)) {
            part = HudPart.HEALTH;
            y = graphics.guiHeight() - minecraft.gui.leftHeight;
        } else if (layer.equals(VanillaGuiLayers.FOOD_LEVEL)) {
            part = HudPart.HUNGER;
            x = graphics.guiWidth() / 2 + 10;
            y = graphics.guiHeight() - minecraft.gui.rightHeight;
        } else if (layer.equals(VanillaGuiLayers.HOTBAR)) {
            part = HudPart.HOTBAR;
            y = graphics.guiHeight() - 22;
        } else if (ExperienceHud.isLayer(layer)) {
            part = HudPart.EXPERIENCE;
            y = graphics.guiHeight() - 36;
        } else {
            original.run();
            return;
        }
        if ((!part.placement().hasPosition() && part.scale() == 1)
                || (part != HudPart.ARMOR && part.placement().style() != HudStyle.ORIGINAL)) {
            original.run();
            return;
        }
        graphics.pose().pushPose();
        try {
            HudTransform.apply(graphics, part, part.x(graphics.guiWidth(), graphics.guiHeight()),
                    part.y(graphics.guiWidth(), graphics.guiHeight()), x, y);
            original.run();
        } finally {
            graphics.pose().popPose();
        }
    }
}
