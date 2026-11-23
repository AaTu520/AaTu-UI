package cn.aatu.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public abstract class CrispScreen extends Screen {
    protected CrispScreen(Component title) {
        super(title);
    }

    protected void drawBackdrop(GuiGraphics graphics, float partialTick) {
        if (minecraft.level == null) renderPanorama(graphics, partialTick);
        graphics.fill(0, 0, width, height, 0x66000000);
    }

    protected void drawWidgets(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        for (Renderable widget : renderables) widget.render(graphics, mouseX, mouseY, partialTick);
    }
}
