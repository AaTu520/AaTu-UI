package cn.aatu.ui.mixin;

import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = {
        "com.github.L_Ender.cataclysm.client.event.ClientEvent",
        "net.miauczel.legendary_monsters.client.event.ClientEvent"
}, remap = false)
public abstract class ThirdPartyBossBarMixin {
    @Inject(method = "renderBossOverlay", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void aatu$useAaTuBossBar(CustomizeGuiOverlayEvent.BossEventProgress event, CallbackInfo callback) {
        callback.cancel();
    }
}
