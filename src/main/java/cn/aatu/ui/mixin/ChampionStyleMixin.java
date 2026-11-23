package cn.aatu.ui.mixin;

import cn.aatu.ui.HudCompatibility;
import cn.aatu.ui.HudPart;
import cn.aatu.ui.HudStyle;
import cn.aatu.ui.HudTransform;
import cn.aatu.ui.ChampionAffixAnchor;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "top.theillusivec4.champions.client.util.HUDHelper", remap = false)
public abstract class ChampionStyleMixin {
    @Unique private static int aatu$rankColor = 0xFFFFFF;

    @WrapOperation(method = "lambda$renderHealthBar$0", at = @At(value = "INVOKE", remap = false,
            target = "Ltop/theillusivec4/champions/common/rank/Rank;getColor(Ljava/lang/String;)I"),
            remap = false, require = 0)
    private static int aatu$captureRankColor(String color, Operation<Integer> original) {
        aatu$rankColor = original.call(color);
        return aatu$rankColor;
    }

    @WrapOperation(method = "lambda$renderHealthBar$0", at = @At(value = "INVOKE", remap = false,
            target = "Lnet/minecraft/client/gui/GuiGraphics;blit(Lnet/minecraft/resources/ResourceLocation;IIFFIIII)V"),
            remap = false, require = 0)
    private static void aatu$styleChampionBar(GuiGraphics graphics, ResourceLocation texture, int x, int y,
                                              float u, float v, int width, int height, int textureWidth, int textureHeight,
                                              Operation<Void> original) {
        HudStyle style = HudPart.CHAMPION.placement().style();
        boolean championBar = texture.getNamespace().equals("champions") && texture.getPath().equals("textures/gui/bars.png");
        boolean championStar = texture.getNamespace().equals("champions") && texture.getPath().equals("textures/gui/staricon.png");
        if (!HudPart.CHAMPION.placement().visible && (championBar || championStar)) return;
        if (style == HudStyle.ORIGINAL || !championBar
                || (v != 60.0F && v != 65.0F)) {
            original.call(graphics, texture, x, y, u, v, width, height, textureWidth, textureHeight);
            return;
        }
        float[] shaderColor = RenderSystem.getShaderColor().clone();
        graphics.flush();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        try {
            if (v == 60.0F) {
                graphics.fill(x, y, x + width, y + height, 0xB0181B20);
            } else {
                int top = style == HudStyle.COMPACT ? y : y + 2;
                graphics.fill(x, top, x + Math.min(width, 182), y + height, 0xFF000000 | aatu$rankColor);
            }
            graphics.flush();
        } finally {
            RenderSystem.setShaderColor(shaderColor[0], shaderColor[1], shaderColor[2], shaderColor[3]);
        }
    }

    @WrapOperation(method = "lambda$renderHealthBar$0", at = @At(value = "INVOKE", ordinal = 0, remap = false,
            target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;FFIZ)I"),
            remap = false, require = 0)
    private static int aatu$starCount(GuiGraphics graphics, Font font, String text, float x, float y, int color, boolean shadow,
                                      Operation<Integer> original) {
        return HudPart.CHAMPION.placement().visible ? original.call(graphics, font, text, x, y, color, shadow) : 0;
    }

    @WrapOperation(method = "lambda$renderHealthBar$0", at = @At(value = "INVOKE", ordinal = 1, remap = false,
            target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;FFIZ)I"),
            remap = false, require = 0)
    private static int aatu$championName(GuiGraphics graphics, Font font, String text, float x, float y, int color, boolean shadow,
                                         Operation<Integer> original) {
        return HudPart.CHAMPION.placement().visible ? original.call(graphics, font, text, x, y, color, shadow) : 0;
    }

    @WrapOperation(method = "lambda$renderHealthBar$0", at = @At(value = "INVOKE", ordinal = 2, remap = false,
            target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;FFIZ)I"),
            remap = false, require = 0)
    private static int aatu$affixes(GuiGraphics graphics, Font font, String text, float x, float y, int color, boolean shadow,
                                    Operation<Integer> original) {
        if (!HudPart.CHAMPION_AFFIXES.placement().visible) return 0;
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        ChampionAffixAnchor.Position bar = ChampionAffixAnchor.championPosition(font, width, height, false);
        ChampionAffixAnchor.Position position = ChampionAffixAnchor.textPosition(font, text, width, height, false);
        if (position.scale() <= 0) return 0;
        graphics.pose().pushPose();
        try {
            HudTransform.undo(graphics, HudPart.CHAMPION, bar.x(), bar.y(),
                    width / 2 - 91 + HudCompatibility.championOffsetX(), 1 + HudCompatibility.championOffsetY());
            graphics.pose().translate(position.x(), position.y(), 0);
            graphics.pose().scale(position.scale(), position.scale(), 1);
            return original.call(graphics, font, text, 0.0F, 0.0F, 0xFF000000 | aatu$rankColor, shadow);
        } finally {
            graphics.pose().popPose();
        }
    }
}
