package cn.aatu.ui.mixin;

import cn.aatu.ui.font.SelectedFontRegistry;
import cn.aatu.ui.font.SelectedOutlineGlyph;
import com.mojang.blaze3d.font.TrueTypeGlyphProvider;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(targets = "com.mojang.blaze3d.font.TrueTypeGlyphProvider$Glyph", remap = false)
public abstract class SelectedGlyphMixin implements SelectedOutlineGlyph {
    @Shadow @Final private TrueTypeGlyphProvider this$0;

    @Override
    public boolean aatu$isSelectedFont() {
        return SelectedFontRegistry.contains(this$0);
    }
}
