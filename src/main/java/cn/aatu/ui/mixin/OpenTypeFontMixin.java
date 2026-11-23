package cn.aatu.ui.mixin;

import cn.aatu.ui.FontFiles;
import cn.aatu.ui.font.SelectedFontRegistry;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.font.GlyphProvider;
import net.minecraft.client.gui.font.providers.TrueTypeGlyphProviderDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TrueTypeGlyphProviderDefinition.class)
public abstract class OpenTypeFontMixin {
    @Shadow @Final private ResourceLocation location;

    @WrapOperation(method = "load", at = @At(value = "INVOKE", target = "Ljava/lang/String;equals(Ljava/lang/Object;)Z", remap = false), require = 0)
    private boolean aatu$allowOpenTypeOutlines(String expected, Object actual, Operation<Boolean> original) {
        // FreeType can rasterize CFF outlines; keep vanilla's restriction for other packs.
        return original.call(expected, actual) || (location.equals(ResourceLocation.fromNamespaceAndPath("aatu_ui", "selected.ttf"))
                && "CFF".equals(actual));
    }

    @ModifyArg(method = "load", at = @At(value = "INVOKE", remap = false,
            target = "Lorg/lwjgl/util/freetype/FreeType;FT_New_Memory_Face(JLjava/nio/ByteBuffer;JLorg/lwjgl/PointerBuffer;)I"),
            index = 2, require = 0)
    private long aatu$selectCollectionFace(long original) {
        return location.equals(ResourceLocation.fromNamespaceAndPath("aatu_ui", "selected.ttf"))
                ? Math.max(0, Math.min(2047, FontFiles.selectedFaceIndex())) : original;
    }

    @Inject(method = "load", at = @At("RETURN"), require = 0)
    private void aatu$markSelectedProvider(ResourceManager manager, CallbackInfoReturnable<GlyphProvider> result) {
        if (location.equals(ResourceLocation.fromNamespaceAndPath("aatu_ui", "selected.ttf"))) {
            SelectedFontRegistry.mark(result.getReturnValue());
        }
    }
}
