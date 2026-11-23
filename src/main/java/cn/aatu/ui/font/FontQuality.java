package cn.aatu.ui.font;

import cn.aatu.ui.UiSettings;
import cn.aatu.ui.mixin.MinecraftFontManagerAccessor;
import com.mojang.logging.LogUtils;
import java.util.concurrent.CompletableFuture;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.font.FontManager;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.util.Mth;
import net.minecraft.util.profiling.InactiveProfiler;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

public final class FontQuality {
    private static final int MIN_OVERSAMPLE = 2;
    private static final int MAX_OVERSAMPLE = 4;
    private static final int STABLE_TICKS = 20;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final PreparableReloadListener.PreparationBarrier FONT_ONLY_BARRIER = new PreparableReloadListener.PreparationBarrier() {
        @Override
        public <T> CompletableFuture<T> wait(T prepared) {
            return CompletableFuture.completedFuture(prepared);
        }
    };
    private static volatile int preparedOversample;
    private static volatile int preparedCandidate;
    private static volatile int failedOversample;
    private static int pendingOversample;
    private static int stableTicks;
    private static volatile CompletableFuture<Void> reloadInFlight;

    private FontQuality() {}

    public static int currentOversample() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getWindow() == null) return 3;
        return Mth.clamp((int) Math.round(minecraft.getWindow().getGuiScale()), MIN_OVERSAMPLE, MAX_OVERSAMPLE);
    }

    public static void fontPrepared(int oversample) {
        preparedCandidate = oversample;
    }

    public static void fontApplied() {
        preparedOversample = preparedCandidate;
        failedOversample = 0;
    }

    @Nullable
    public static CompletableFuture<Void> currentRefresh() {
        return reloadInFlight;
    }

    public static boolean isRefreshing() {
        CompletableFuture<Void> current = reloadInFlight;
        return current != null && !current.isDone();
    }

    public static void tick(Minecraft minecraft) {
        UiSettings settings = UiSettings.get();
        if (preparedOversample == 0 || !settings.smoothFonts
                || (!settings.systemFontReference && settings.selectedFont.isBlank())
                || (settings.systemFontReference && settings.systemFontFile.isBlank())) return;
        int current = currentOversample();
        if (current == preparedOversample) {
            stableTicks = 0;
            failedOversample = 0;
            return;
        }
        if (pendingOversample != current) {
            pendingOversample = current;
            stableTicks = 0;
            failedOversample = 0;
        }
        if (failedOversample == current) return;
        if (++stableTicks >= STABLE_TICKS && (reloadInFlight == null || reloadInFlight.isDone())) {
            stableTicks = 0;
            refresh(minecraft).whenComplete((unused, failure) -> {
                if (failure != null) {
                    failedOversample = current;
                }
            });
        }
    }

    public static synchronized CompletableFuture<Void> refresh(Minecraft minecraft) {
        failedOversample = 0;
        try {
            if (!(minecraft instanceof MinecraftFontManagerAccessor accessor)) {
                throw new IllegalStateException("Minecraft font manager is unavailable");
            }
            FontManager fonts = accessor.aatu$fontManager();
            if (!(fonts instanceof FontManagerRefreshGuard guard) || !guard.aatu$hasRefreshGuard()) {
                throw new IllegalStateException("Font refresh coordination is unavailable");
            }
            reloadInFlight = fonts.reload(FONT_ONLY_BARRIER, minecraft.getResourceManager(),
                    InactiveProfiler.INSTANCE, InactiveProfiler.INSTANCE, Util.backgroundExecutor(), minecraft)
                    .whenComplete((unused, failure) -> {
                        if (failure != null) LOGGER.warn("Font-only refresh failed", failure);
                    });
        } catch (RuntimeException exception) {
            LOGGER.warn("Unable to start font-only refresh", exception);
            reloadInFlight = CompletableFuture.failedFuture(exception);
        }
        return reloadInFlight;
    }
}
