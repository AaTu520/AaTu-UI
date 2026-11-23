package cn.aatu.ui.mixin;

import cn.aatu.ui.font.OutlineGlyph;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(targets = "com.mojang.blaze3d.font.TrueTypeGlyphProvider$Glyph$1", remap = false)
public abstract class OutlineGlyphMixin implements OutlineGlyph {}
