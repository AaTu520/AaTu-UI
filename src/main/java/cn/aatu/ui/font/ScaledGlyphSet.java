package cn.aatu.ui.font;

import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import org.jetbrains.annotations.Nullable;

public interface ScaledGlyphSet {
    @Nullable BakedGlyph aatu$scaledGlyph(int codePoint);
}
