package cn.aatu.ui.mixin;

import cn.aatu.ui.UiSettings;
import cn.aatu.ui.font.OutlineGlyph;
import cn.aatu.ui.font.ScaledGlyphCache;
import cn.aatu.ui.font.ScaledGlyphSet;
import cn.aatu.ui.font.SmoothTextRenderTypes;
import com.mojang.blaze3d.font.SheetGlyphInfo;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.client.gui.font.FontTexture;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FontSet.class)
public abstract class SmoothFontSetMixin implements ScaledGlyphSet {
    @Unique private static final Logger AATU_LOGGER = LogUtils.getLogger();
    @Shadow @Final private TextureManager textureManager;
    @Shadow @Final private ResourceLocation name;
    @Shadow private BakedGlyph missingGlyph;
    @Unique private final List<FontTexture> aatu$outlineTextures = new ArrayList<>();
    @Unique private final List<ResourceLocation> aatu$outlineIds = new ArrayList<>();
    @Unique private boolean aatu$smoothingFailed;
    @Unique private ScaledGlyphCache aatu$scaledCache;

    @Override
    public BakedGlyph aatu$scaledGlyph(int codePoint) {
        if (aatu$scaledCache == null) aatu$scaledCache = new ScaledGlyphCache(textureManager, name);
        return aatu$scaledCache.get(codePoint);
    }

    @Inject(method = "stitch", at = @At("HEAD"), cancellable = true)
    private void aatu$stitchOutline(SheetGlyphInfo glyph, CallbackInfoReturnable<BakedGlyph> result) {
        if (!(glyph instanceof OutlineGlyph) || !UiSettings.get().smoothFonts || aatu$smoothingFailed) return;
        try {
            for (FontTexture texture : aatu$outlineTextures) {
                BakedGlyph baked = texture.add(glyph);
                if (baked != null) {
                    texture.setFilter(true, false);
                    result.setReturnValue(baked);
                    return;
                }
            }
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aatu_ui",
                    "smooth/" + name.getNamespace() + "/" + name.getPath() + "/" + aatu$outlineTextures.size());
            FontTexture texture = new FontTexture(SmoothTextRenderTypes.create(id), false);
            textureManager.register(id, texture);
            aatu$outlineTextures.add(texture);
            aatu$outlineIds.add(id);
            BakedGlyph baked = texture.add(glyph);
            texture.setFilter(true, false);
            result.setReturnValue(baked == null ? missingGlyph : baked);
        } catch (RuntimeException exception) {
            aatu$smoothingFailed = true;
            AATU_LOGGER.warn("Smooth font atlas unavailable for {}; using the standard font atlas", name, exception);
        }
    }

    @Inject(method = "closeTextures", at = @At("HEAD"))
    private void aatu$closeOutlineTextures(CallbackInfo callback) {
        if (aatu$scaledCache != null) {
            aatu$scaledCache.close();
            aatu$scaledCache = null;
        }
        for (ResourceLocation id : aatu$outlineIds) textureManager.release(id);
        aatu$outlineIds.clear();
        aatu$outlineTextures.clear();
        aatu$smoothingFailed = false;
    }
}
