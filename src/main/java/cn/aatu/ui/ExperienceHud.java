package cn.aatu.ui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

public final class ExperienceHud {
    private static final ResourceLocation BACKGROUND = ResourceLocation.withDefaultNamespace("hud/experience_bar_background");
    private static final ResourceLocation PROGRESS = ResourceLocation.withDefaultNamespace("hud/experience_bar_progress");
    private static final int LEVEL_COLOR = 0xFF80FF20;
    private static final int FILL_COLOR = 0xFF9BCB59;
    private static final int TRACK_COLOR = 0xB0181B20;
    private static final int VANILLA_BAR_OFFSET = 7;
    private static final int BAR_LENGTH = 182;
    private static final int VERTICAL_LEVEL_HEIGHT = 12;
    private static final int MIN_VERTICAL_WIDTH = 14;

    private ExperienceHud() {}

    public static boolean isLayer(ResourceLocation layer) {
        return layer.equals(VanillaGuiLayers.EXPERIENCE_BAR) || layer.equals(VanillaGuiLayers.EXPERIENCE_LEVEL);
    }

    public static int logicalWidth() {
        UiSettings settings = UiSettings.get();
        if (!settings.experienceVertical) return BAR_LENGTH;
        if (!settings.showExperienceLevel) return MIN_VERTICAL_WIDTH;
        Minecraft minecraft = Minecraft.getInstance();
        int level = minecraft.player == null ? 12 : minecraft.player.experienceLevel;
        return Math.max(MIN_VERTICAL_WIDTH, minecraft.font.width(Integer.toString(level)) + 2);
    }

    public static int logicalHeight() {
        UiSettings settings = UiSettings.get();
        if (settings.experienceVertical) {
            return Math.max(9, (settings.showExperienceLevel ? VERTICAL_LEVEL_HEIGHT : 0)
                    + (settings.showExperienceBar ? BAR_LENGTH : 0));
        }
        return HudPart.EXPERIENCE.placement().style() == HudStyle.ORIGINAL ? 12 : 17;
    }

    public static void renderLayer(RenderGuiLayerEvent.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        HudPart part = HudPart.EXPERIENCE;
        UiSettings settings = UiSettings.get();
        boolean barLayer = event.getName().equals(VanillaGuiLayers.EXPERIENCE_BAR);
        if (!part.placement().visible || minecraft.screen instanceof HudEditorScreen) {
            event.setCanceled(true);
            return;
        }
        if (part.placement().style() == HudStyle.ORIGINAL && !settings.experienceVertical) {
            if (barLayer ? !settings.showExperienceBar : !settings.showExperienceLevel) event.setCanceled(true);
            return;
        }
        event.setCanceled(true);
        if (!barLayer || minecraft.options.hideGui || minecraft.player == null || minecraft.gameMode == null
                || !minecraft.gameMode.hasExperience() || minecraft.player.jumpableVehicle() != null) return;
        GuiGraphics graphics = event.getGuiGraphics();
        int x = part.x(graphics.guiWidth(), graphics.guiHeight());
        int y = part.y(graphics.guiWidth(), graphics.guiHeight());
        graphics.pose().pushPose();
        try {
            HudTransform.apply(graphics, part, x, y);
            draw(graphics, x, y, minecraft.player.experienceProgress, minecraft.player.experienceLevel);
        } finally {
            graphics.pose().popPose();
        }
    }

    public static void preview(GuiGraphics graphics, int x, int y) {
        var player = Minecraft.getInstance().player;
        draw(graphics, x, y, player == null ? 0.6F : player.experienceProgress,
                player == null ? 12 : player.experienceLevel);
    }

    private static void draw(GuiGraphics graphics, int x, int y, float progress, int level) {
        UiSettings settings = UiSettings.get();
        HudStyle style = HudPart.EXPERIENCE.placement().style();
        float fraction = Float.isFinite(progress) ? Mth.clamp(progress, 0, 1) : 0;
        if (settings.experienceVertical) {
            drawVertical(graphics, x, y, fraction, level, style);
            return;
        }
        if (settings.showExperienceBar) {
            drawTrack(graphics, x, y + (style == HudStyle.ORIGINAL ? VANILLA_BAR_OFFSET : 11), fraction, style);
        }
        if (settings.showExperienceLevel && level > 0) drawLevel(graphics, x, y + 1, BAR_LENGTH, level);
    }

    private static void drawVertical(GuiGraphics graphics, int x, int y, float progress, int level, HudStyle style) {
        UiSettings settings = UiSettings.get();
        int width = logicalWidth();
        if (settings.showExperienceBar) {
            int thickness = style == HudStyle.ORIGINAL ? 5 : 6;
            int labelHeight = settings.showExperienceLevel ? VERTICAL_LEVEL_HEIGHT : 0;
            graphics.pose().pushPose();
            try {
                // Rotate only the track so its progress rises from the bottom and the level stays upright.
                graphics.pose().translate(x + (width - thickness) / 2, y + labelHeight + BAR_LENGTH, 0);
                graphics.pose().mulPose(Axis.ZP.rotationDegrees(-90));
                drawTrack(graphics, 0, 0, progress, style);
            } finally {
                graphics.pose().popPose();
            }
        }
        if (settings.showExperienceLevel && level > 0) drawLevel(graphics, x, y + 1, width, level);
    }

    private static void drawTrack(GuiGraphics graphics, int x, int y, float progress, HudStyle style) {
        if (style == HudStyle.ORIGINAL) {
            RenderSystem.enableBlend();
            try {
                graphics.blitSprite(BACKGROUND, x, y, BAR_LENGTH, 5);
                int filled = Math.min(BAR_LENGTH, (int) (progress * (BAR_LENGTH + 1)));
                if (filled > 0) graphics.blitSprite(PROGRESS, BAR_LENGTH, 5, 0, 0, x, y, filled, 5);
            } finally {
                RenderSystem.disableBlend();
            }
        } else {
            graphics.fill(x, y, x + BAR_LENGTH, y + 6, TRACK_COLOR);
            int top = style == HudStyle.COMPACT ? y : y + 3;
            int filled = Math.round(BAR_LENGTH * progress);
            if (filled > 0) graphics.fill(x, top, x + filled, y + 6, FILL_COLOR);
        }
    }

    private static void drawLevel(GuiGraphics graphics, int x, int y, int width, int level) {
        var font = Minecraft.getInstance().font;
        String text = Integer.toString(level);
        int textX = x + (width - font.width(text)) / 2;
        graphics.drawString(font, text, textX + 1, y, 0xFF000000, false);
        graphics.drawString(font, text, textX - 1, y, 0xFF000000, false);
        graphics.drawString(font, text, textX, y + 1, 0xFF000000, false);
        graphics.drawString(font, text, textX, y - 1, 0xFF000000, false);
        graphics.drawString(font, text, textX, y, LEVEL_COLOR, false);
    }
}
