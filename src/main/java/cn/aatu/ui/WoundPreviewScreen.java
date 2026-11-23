package cn.aatu.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class WoundPreviewScreen extends CrispScreen {
    private final Screen previous;
    private boolean enabled = true;

    public WoundPreviewScreen(Screen previous) {
        super(Component.translatable("aatu_ui.wound_preview"));
        this.previous = previous;
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(blurLabel(), button -> {
            enabled = !enabled;
            button.setMessage(blurLabel());
        }).bounds(width / 2 - 102, height - 25, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(width / 2 + 2, height - 25, 100, 20).build());
    }

    public boolean isEnabled() {
        return enabled;
    }

    private Component blurLabel() {
        return Component.translatable("aatu_ui.toggle", Component.translatable("aatu_ui.wound_preview_toggle"),
                Component.translatable(enabled ? "aatu_ui.enabled" : "aatu_ui.disabled"));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Keeping the scene visible makes the before/after comparison independent of menu blur.
        graphics.drawCenteredString(font, title, width / 2, 10, 0xFFFFFFFF);
        if (WoundScreenEffect.hasFailed()) {
            graphics.drawCenteredString(font, Component.translatable("aatu_ui.wound_preview_failed"),
                    width / 2, 25, 0xFFFF8080);
        } else if (minecraft.options.screenEffectScale().get() == 0.0) {
            graphics.drawCenteredString(font, Component.translatable("aatu_ui.wound_preview_scale_zero"),
                    width / 2, 25, 0xFFFFD080);
        }
        drawWidgets(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(previous);
    }
}
