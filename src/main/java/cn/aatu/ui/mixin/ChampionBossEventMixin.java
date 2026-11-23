package cn.aatu.ui.mixin;

import cn.aatu.ui.HudPart;
import cn.aatu.ui.ChampionAffixAnchor;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "top.theillusivec4.champions.common.event.ChampionEventsHandler", remap = false)
public abstract class ChampionBossEventMixin {
    @Inject(method = "onBossBarEvent", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void aatu$keepBossBars(CustomizeGuiOverlayEvent.BossEventProgress event, CallbackInfo callback) {
        // A group needs the normal Boss bar to remain as its anchor.
        if (!HudPart.CHAMPION.placement().visible || ChampionAffixAnchor.groupAttached()) callback.cancel();
    }
}
