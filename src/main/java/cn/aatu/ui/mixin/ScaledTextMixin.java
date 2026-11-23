package cn.aatu.ui.mixin;

import cn.aatu.ui.UiSettings;
import cn.aatu.ui.font.FontQuality;
import cn.aatu.ui.font.ScaledGlyphSet;
import cn.aatu.ui.font.SelectedOutlineGlyph;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.font.GlyphInfo;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "net.minecraft.client.gui.Font$StringRenderOutput")
public abstract class ScaledTextMixin {
    @Shadow @Final private Matrix4f pose;
    @Unique private static final float AATU_SCALE_THRESHOLD = 1.5F;

    @WrapOperation(method = "accept", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/font/FontSet;getGlyph(I)Lnet/minecraft/client/gui/font/glyphs/BakedGlyph;"), require = 0)
    private BakedGlyph aatu$sharpenScaledGlyph(FontSet fontSet, int codePoint, Operation<BakedGlyph> original) {
        if (!UiSettings.get().smoothFonts || FontQuality.isRefreshing()
                || !fontSet.name().equals(Minecraft.DEFAULT_FONT) || !aatu$isScaledGui()
                || !(fontSet instanceof ScaledGlyphSet scaled)) return original.call(fontSet, codePoint);
        GlyphInfo safeGlyph = fontSet.getGlyphInfo(codePoint, true);
        if (safeGlyph != fontSet.getGlyphInfo(codePoint, false)
                || !(safeGlyph instanceof SelectedOutlineGlyph selected) || !selected.aatu$isSelectedFont()) {
            return original.call(fontSet, codePoint);
        }
        BakedGlyph glyph = scaled.aatu$scaledGlyph(codePoint);
        return glyph == null ? original.call(fontSet, codePoint) : glyph;
    }

    @Unique
    private boolean aatu$isScaledGui() {
        Matrix4f projection = RenderSystem.getProjectionMatrix();
        if (projection == null || Math.abs(projection.m33() - 1.0F) > 0.001F) return false;
        float xScale = (float) Math.hypot(pose.m00(), pose.m01());
        float yScale = (float) Math.hypot(pose.m10(), pose.m11());
        return Float.isFinite(xScale) && Float.isFinite(yScale) && Math.max(xScale, yScale) >= AATU_SCALE_THRESHOLD;
    }
}
