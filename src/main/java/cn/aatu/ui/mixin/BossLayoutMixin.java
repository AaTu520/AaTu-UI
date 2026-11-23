package cn.aatu.ui.mixin;

import cn.aatu.ui.BossLayout;
import cn.aatu.ui.BossLayoutHook;
import cn.aatu.ui.HudCompatibility;
import cn.aatu.ui.CataclysmBossMeters;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.platform.Window;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.BossEvent;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BossHealthOverlay.class)
public abstract class BossLayoutMixin implements BossLayoutHook {
    @Shadow @Final private Map<UUID, LerpingBossEvent> events;

    @Override
    public Map<UUID, LerpingBossEvent> aatu$bossEvents() {
        return events;
    }

    @WrapMethod(method = "render", require = 0)
    private void aatu$finishFrame(GuiGraphics graphics, Operation<Void> original) {
        try {
            original.call(graphics);
        } finally {
            BossLayout.endFrame();
        }
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE", remap = false,
            target = "Lnet/neoforged/neoforge/client/ClientHooks;onCustomizeBossEventProgress(Lnet/minecraft/client/gui/GuiGraphics;Lcom/mojang/blaze3d/platform/Window;Lnet/minecraft/client/gui/components/LerpingBossEvent;III)Lnet/neoforged/neoforge/client/event/CustomizeGuiOverlayEvent$BossEventProgress;"), require = 0)
    private CustomizeGuiOverlayEvent.BossEventProgress aatu$layoutEvent(GuiGraphics graphics, Window window, LerpingBossEvent boss,
                                                                         int x, int y, int increment,
                                                                         Operation<CustomizeGuiOverlayEvent.BossEventProgress> original) {
        if (!BossLayout.prepare(graphics, boss, x, y)) return original.call(graphics, window, boss, x, y, increment);
        graphics.pose().pushPose();
        try {
            BossLayout.applyTransform(graphics);
            CustomizeGuiOverlayEvent.BossEventProgress event = original.call(graphics, window, boss, x, y, increment);
            if (BossLayout.currentAuxiliary()) BossLayout.hideAuxiliary(event);
            return event;
        } finally {
            graphics.pose().popPose();
            BossLayout.leaveEvent();
        }
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/BossHealthOverlay;drawBar(Lnet/minecraft/client/gui/GuiGraphics;IILnet/minecraft/world/BossEvent;)V"), require = 0)
    private void aatu$moveVanillaBar(BossHealthOverlay overlay, GuiGraphics graphics, int x, int y, BossEvent boss,
                                     Operation<Void> original) {
        graphics.pose().pushPose();
        try {
            BossLayout.applyTransform(graphics);
            original.call(overlay, graphics, x, y, boss);
            if (BossLayout.currentRage() != null) {
                CataclysmBossMeters.render(graphics, x, y - 9,
                        x + BossLayout.deltaX(), BossLayout.currentSlot(), BossLayout.currentRage());
            }
        } finally {
            graphics.pose().popPose();
        }
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)I"), require = 0)
    private int aatu$moveVanillaTitle(GuiGraphics graphics, Font font, Component name, int x, int y, int color,
                                      Operation<Integer> original) {
        BossEvent boss = BossLayout.currentBoss();
        Component title = boss == null ? name : HudCompatibility.bossTitle(boss, name);
        int centeredX = x - (font.width(title) - font.width(name)) / 2;
        graphics.pose().pushPose();
        try {
            BossLayout.applyTransform(graphics);
            return original.call(graphics, font, title, centeredX, y, color);
        } finally {
            graphics.pose().popPose();
        }
    }

    @ModifyExpressionValue(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;guiHeight()I"), require = 0)
    private int aatu$allowConfiguredCount(int original) {
        return BossLayout.frameActive() ? Integer.MAX_VALUE : original;
    }
}
