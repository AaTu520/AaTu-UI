package cn.aatu.ui.mixin;

import cn.aatu.ui.HudCompatibility;
import cn.aatu.ui.HudEditorScreen;
import cn.aatu.ui.HudPart;
import cn.aatu.ui.HudStyle;
import cn.aatu.ui.HudTransform;
import cn.aatu.ui.SpellLayout;
import cn.aatu.ui.UiSettings;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec2;
import net.spell_engine.client.gui.HudRenderHelper.SpellHotBarWidget.ViewModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "net.spell_engine.client.gui.HudRenderHelper$SpellHotBarWidget", remap = false)
public abstract class SpellHotbarMixin {
    @WrapMethod(method = "render", remap = false, require = 0)
    private static void aatu$positionSpellBar(GuiGraphics graphics, int width, int height, ViewModel viewModel,
                                             Operation<Void> original) {
        Minecraft minecraft = Minecraft.getInstance();
        HudCompatibility.recordSpellCount(viewModel.spells().size());
        if (minecraft.screen != null && minecraft.screen.getClass().getName().equals("net.spell_engine.client.gui.HudConfigScreen")) {
            original.call(graphics, width, height, viewModel);
            return;
        }
        if (!HudPart.SPELLS.placement().visible || minecraft.screen instanceof HudEditorScreen) return;
        if (viewModel.spells().isEmpty() || (!UiSettings.get().spellVertical
                && !HudPart.SPELLS.placement().hasPosition() && HudPart.SPELLS.scale() == 1)) {
            original.call(graphics, width, height, viewModel);
            return;
        }
        Vec2 center = HudCompatibility.currentSpellCenter();
        if (center == null) {
            graphics.pose().pushPose();
            try {
                graphics.pose().translate(-100000, -100000, 0);
                original.call(graphics, width, height, new ViewModel(List.of(viewModel.spells().get(0))));
            } finally {
                graphics.pose().popPose();
            }
            center = HudCompatibility.currentSpellCenter();
        }
        if (center == null) {
            original.call(graphics, width, height, viewModel);
            return;
        }

        if (UiSettings.get().spellVertical) {
            int x = HudPart.SPELLS.x(width, height);
            int y = HudPart.SPELLS.y(width, height);
            float nativeX = center.x - SpellLayout.SLOT_STEP / 2.0F;
            float nativeY = center.y - SpellLayout.SLOT_HEIGHT / 2.0F;
            for (int slot = 0; slot < viewModel.spells().size(); slot++) {
                graphics.pose().pushPose();
                try {
                    HudTransform.apply(graphics, HudPart.SPELLS, x,
                            y + (SpellLayout.VERTICAL_LABEL_SPACE + slot * SpellLayout.VERTICAL_ROW_HEIGHT) * HudPart.SPELLS.scale(),
                            nativeX, nativeY);
                    original.call(graphics, width, height, new ViewModel(List.of(viewModel.spells().get(slot))));
                } finally {
                    graphics.pose().popPose();
                }
            }
            return;
        }

        float originalX = center.x - viewModel.spells().size() * SpellLayout.SLOT_STEP / 2.0F;
        float originalY = center.y - SpellLayout.SLOT_HEIGHT / 2.0F;
        graphics.pose().pushPose();
        try {
            HudTransform.apply(graphics, HudPart.SPELLS, HudPart.SPELLS.x(width, height),
                    HudPart.SPELLS.y(width, height), originalX, originalY);
            original.call(graphics, width, height, viewModel);
        } finally {
            graphics.pose().popPose();
        }
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE", remap = false,
            target = "Lnet/minecraft/client/gui/GuiGraphics;blit(Lnet/minecraft/resources/ResourceLocation;IIFFIIII)V"),
            remap = false, require = 0)
    private static void aatu$styleSpellBackground(GuiGraphics graphics, ResourceLocation texture, int x, int y,
                                                   float u, float v, int width, int height, int textureWidth, int textureHeight,
                                                   Operation<Void> original) {
        HudStyle style = HudPart.SPELLS.placement().style();
        Minecraft client = Minecraft.getInstance();
        boolean nativeEditor = client.screen != null && client.screen.getClass().getName().equals("net.spell_engine.client.gui.HudConfigScreen");
        boolean hotbarBackground = texture.getNamespace().equals("minecraft")
                && texture.getPath().equals("textures/gui/sprites/hud/hotbar.png");
        if (style == HudStyle.ORIGINAL || nativeEditor || !hotbarBackground) {
            original.call(graphics, texture, x, y, u, v, width, height, textureWidth, textureHeight);
            return;
        }
        int background = style == HudStyle.COMPACT ? 0xD03A4663 : 0xD0181B20;
        graphics.fill(x, y, x + width, y + height, background);
        if (style == HudStyle.MODERN) graphics.fill(x, y + height - 2, x + width, y + height, 0xFF8198C9);
    }
}
