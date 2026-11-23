package cn.aatu.ui.mixin;

import cn.aatu.ui.font.FontManagerRefreshGuard;
import cn.aatu.ui.font.FontQuality;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.minecraft.client.gui.font.FontManager;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(FontManager.class)
public abstract class FontManagerRefreshMixin implements FontManagerRefreshGuard {
    @Unique private CompletableFuture<Void> aatu$pendingRefresh;
    @Unique private volatile boolean aatu$refreshGuardActive;

    @Override
    public boolean aatu$hasRefreshGuard() {
        return aatu$refreshGuardActive;
    }

    @WrapMethod(method = "reload", require = 0)
    private CompletableFuture<Void> aatu$serializeRefresh(PreparableReloadListener.PreparationBarrier barrier,
            ResourceManager resources, ProfilerFiller prepareProfiler, ProfilerFiller applyProfiler,
            Executor prepareExecutor, Executor applyExecutor, Operation<CompletableFuture<Void>> original) {
        synchronized (this) {
            aatu$refreshGuardActive = true;
            CompletableFuture<Void> previous = aatu$pendingRefresh;
            if (previous == null || previous.isDone()) {
                aatu$pendingRefresh = original.call(barrier, resources, prepareProfiler, applyProfiler, prepareExecutor, applyExecutor)
                        .thenRun(FontQuality::fontApplied);
            } else {
                // A resource-pack reload may arrive while a font-only refresh is still preparing.
                aatu$pendingRefresh = previous.handle((value, failure) -> (Void) null)
                        .thenComposeAsync(value -> original.call(barrier, resources, prepareProfiler, applyProfiler,
                                prepareExecutor, applyExecutor).thenRun(FontQuality::fontApplied), applyExecutor);
            }
            return aatu$pendingRefresh;
        }
    }
}
