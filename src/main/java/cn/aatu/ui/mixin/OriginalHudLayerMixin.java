package cn.aatu.ui.mixin;

import cn.aatu.ui.OriginalHudLayers;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.neoforged.neoforge.client.gui.GuiLayerManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = GuiLayerManager.class, remap = false)
public abstract class OriginalHudLayerMixin {
    @WrapOperation(method = "renderInner", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/LayeredDraw$Layer;render(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V"), require = 0)
    private void aatu$moveOriginal(LayeredDraw.Layer layer, GuiGraphics graphics, DeltaTracker delta,
                                  Operation<Void> original, @Local GuiLayerManager.NamedLayer namedLayer) {
        // Moving the actual draw preserves other mods' layer events and cancellation.
        OriginalHudLayers.render(namedLayer.name(), graphics, () -> original.call(layer, graphics, delta));
    }
}
