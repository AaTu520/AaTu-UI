package cn.aatu.ui;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.slf4j.Logger;

public final class WoundScreenEffect {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation WOUND = ResourceLocation.fromNamespaceAndPath("champions", "wound");
    private static final String SHADER = "aatu_ui:wound_edges";
    private static final float FADE_STEP = 0.1F;
    private static LocalPlayer trackedPlayer;
    private static float previousStrength;
    private static float strength;
    private static boolean failed;
    private static boolean loggedDetection;
    private static boolean loggedWoundRender;
    private static boolean loggedPreviewRender;
    private static RenderTarget mainTarget;
    private static RenderTarget intermediateTarget;
    private static PostPass horizontalPass;
    private static PostPass verticalPass;
    private static Uniform horizontalStrength;
    private static Uniform verticalStrength;

    private WoundScreenEffect() {}

    public static void registerReloadListener(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) resources -> clear());
    }

    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        boolean preview = minecraft.screen instanceof WoundPreviewScreen;
        if (player != trackedPlayer) {
            clear();
            trackedPlayer = player;
        }
        if (!UiSettings.get().woundEdgeBlur || player == null
                || (!preview && (player.isDeadOrDying() || player.isSpectator()))) {
            clear();
            return;
        }
        boolean wounded = false;
        for (var effect : player.getActiveEffects()) {
            if (WOUND.equals(BuiltInRegistries.MOB_EFFECT.getKey(effect.getEffect().value()))) {
                wounded = true;
                break;
            }
        }
        if (wounded && !loggedDetection) {
            loggedDetection = true;
            LOGGER.info("Wound edge blur: detected champions:wound, screenEffectScale={}",
                    minecraft.options.screenEffectScale().get());
        }
        previousStrength = strength;
        strength = Mth.clamp(strength + (wounded ? FADE_STEP : -FADE_STEP), 0, 1);
        if (strength == 0 && previousStrength == 0 && !preview) releaseBuffers();
    }

    public static void render(RenderGuiEvent.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean preview = minecraft.screen instanceof WoundPreviewScreen;
        if (failed || !UiSettings.get().woundEdgeBlur || minecraft.level == null || minecraft.player == null
                || minecraft.options.hideGui || minecraft.getOverlay() != null) return;
        if (!preview && (minecraft.player != trackedPlayer || minecraft.player.isDeadOrDying()
                || minecraft.player.isSpectator())) return;
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float amount = minecraft.screen instanceof WoundPreviewScreen screen
                ? (screen.isEnabled() ? 1.0F : 0.0F)
                : Mth.lerp(partialTick, previousStrength, strength);
        amount *= minecraft.options.screenEffectScale().get().floatValue();
        if (amount <= 0) return;
        RenderTarget target = minecraft.getMainRenderTarget();
        if (target.width < 1 || target.height < 1) return;
        event.getGuiGraphics().flush();
        int previousFilter = target.filterMode;
        try {
            prepareBuffers(minecraft, target);
            // An independent pass keeps the game's other post effects and the later HUD draw intact.
            RenderSystem.disableBlend();
            RenderSystem.disableDepthTest();
            target.setFilterMode(GL11.GL_LINEAR);
            horizontalStrength.set(amount);
            verticalStrength.set(amount);
            horizontalPass.process(0);
            verticalPass.process(0);
            if (preview ? !loggedPreviewRender : !loggedWoundRender) {
                if (preview) loggedPreviewRender = true;
                else loggedWoundRender = true;
                LOGGER.info("Wound edge blur: submitted both passes (source={}, size={}x{}, strength={})",
                        preview ? "preview" : "wound", target.width, target.height, amount);
            }
        } catch (IOException | RuntimeException exception) {
            failed = true;
            releaseBuffers();
            LOGGER.warn("Wound edge blur is disabled until resource reload or the option is toggled off", exception);
        } finally {
            target.setFilterMode(previousFilter);
            target.bindWrite(true);
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.enableDepthTest();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
        }
    }

    private static void prepareBuffers(Minecraft minecraft, RenderTarget target) throws IOException {
        if (mainTarget == target && intermediateTarget != null && horizontalPass != null && verticalPass != null) {
            if (intermediateTarget.width == target.width && intermediateTarget.height == target.height) return;
            intermediateTarget.resize(target.width, target.height, Minecraft.ON_OSX);
        } else {
            releaseBuffers();
            mainTarget = target;
            // This pass samples color only; a second depth attachment would waste GPU memory.
            intermediateTarget = new TextureTarget(target.width, target.height, false, Minecraft.ON_OSX);
            horizontalPass = new PostPass(minecraft.getResourceManager(), SHADER, target, intermediateTarget, true);
            verticalPass = new PostPass(minecraft.getResourceManager(), SHADER, intermediateTarget, target, true);
        }
        intermediateTarget.setFilterMode(GL11.GL_LINEAR);
        Matrix4f projection = new Matrix4f().setOrtho(0, target.width, 0, target.height, 0.1F, 1000);
        horizontalStrength = configurePass(horizontalPass, projection, 1.0F, 0.0F);
        verticalStrength = configurePass(verticalPass, projection, 0.0F, 1.0F);
    }

    private static Uniform configurePass(PostPass pass, Matrix4f projection, float x, float y) throws IOException {
        requiredUniform(pass, "ProjMat");
        requiredUniform(pass, "InSize");
        requiredUniform(pass, "OutSize");
        pass.setOrthoMatrix(projection);
        requiredUniform(pass, "Direction").set(x, y);
        return requiredUniform(pass, "Strength");
    }

    private static Uniform requiredUniform(PostPass pass, String name) throws IOException {
        Uniform uniform = pass.getEffect().getUniform(name);
        // A missing required uniform must not silently leave a successful-looking, invisible effect.
        if (uniform == null) throw new IOException("Wound edge blur shader is missing uniform: " + name);
        return uniform;
    }

    public static boolean hasFailed() {
        return failed;
    }

    public static void clear() {
        previousStrength = 0;
        strength = 0;
        trackedPlayer = null;
        failed = false;
        loggedDetection = false;
        loggedWoundRender = false;
        loggedPreviewRender = false;
        releaseBuffers();
    }

    private static void releaseBuffers() {
        if (horizontalPass != null) horizontalPass.close();
        if (verticalPass != null) verticalPass.close();
        if (intermediateTarget != null) intermediateTarget.destroyBuffers();
        horizontalPass = null;
        verticalPass = null;
        horizontalStrength = null;
        verticalStrength = null;
        intermediateTarget = null;
        mainTarget = null;
    }
}
