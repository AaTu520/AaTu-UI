package cn.aatu.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class StatBarRenderer {
    private static final ResourceLocation ORIGINAL_FRAME = frame("original");
    private static final ResourceLocation MODERN_FRAME = frame("modern");
    private static final int TEXT = 0xFFFFFFFF;
    private static final int TRACK = 0xEC111824;
    private static final int FILLED_TRACK = 0xB0181B20;
    private static final int LINE_TRACK = 0xA0505868;
    private static final int TEXT_INSET = 4;
    private static final int TEXT_TOP_INSET = 1;
    private static final int TEXT_VERTICAL_RESERVED = 5;

    private StatBarRenderer() {}

    private static ResourceLocation frame(String style) {
        return ResourceLocation.fromNamespaceAndPath(AaTuUi.ID, "hud/stats/" + style + "_frame");
    }

    public static void draw(GuiGraphics graphics, int x, int y, int width, int height, Component text, double progress, int color) {
        drawSegments(graphics, x, y, width, height, text, progress, color, 0, color);
    }

    public static void drawSegments(GuiGraphics graphics, int x, int y, int width, int height, Component text, double progress, int color,
                                   double extraProgress, int extraColor) {
        StatBarStyle style = StatBarStyle.from(HudPart.STATS.placement().style);
        double fraction = !Double.isFinite(progress) ? 0 : progress < 0 ? 1 : Math.clamp(progress, 0, 1);
        double extraFraction = Double.isFinite(extraProgress) ? Math.clamp(extraProgress, 0, 1 - fraction) : 0;
        if (style == StatBarStyle.COMPACT || style == StatBarStyle.THIN) {
            int top = style == StatBarStyle.THIN ? y + height - 1 : y;
            graphics.fill(x, top, x + width, y + height, style == StatBarStyle.THIN ? LINE_TRACK : FILLED_TRACK);
            int filled = (int) Math.round(width * fraction);
            int totalFilled = (int) Math.round(width * (fraction + extraFraction));
            if (filled > 0) graphics.fill(x, top, x + filled, y + height, color);
            if (totalFilled > filled) graphics.fill(x + filled, top, x + totalFilled, y + height, extraColor);
        } else {
            drawFramedBar(graphics, x, y, width, height, fraction, color, extraFraction, extraColor, style);
        }
        Font font = Minecraft.getInstance().font;
        float availableTextHeight = height - TEXT_VERTICAL_RESERVED;
        // Reserve the progress strip in every style so a style change keeps the same text size.
        float scale = Math.min(1, Math.min((width - TEXT_INSET * 2.0F) / Math.max(1, font.width(text)),
                availableTextHeight / font.lineHeight));
        int textY = style == StatBarStyle.MODERN || style == StatBarStyle.THIN
                ? y + TEXT_TOP_INSET + Math.round((availableTextHeight - font.lineHeight * scale) / 2.0F)
                : y + Math.round((height - font.lineHeight * scale) / 2.0F);
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(x + TEXT_INSET, textY, 0);
            graphics.pose().scale(scale, scale, 1);
            graphics.drawString(font, text, 0, 0, TEXT, true);
        } finally {
            graphics.pose().popPose();
        }
    }

    private static void drawFramedBar(GuiGraphics graphics, int left, int y, int width, int height,
                                      double fraction, int color, double extraFraction, int extraColor, StatBarStyle style) {
        int filled = (int) Math.round((width - 4) * fraction);
        int totalFilled = (int) Math.round((width - 4) * (fraction + extraFraction));
        graphics.fill(left + 1, y + 1, left + width - 1, y + height - 1, TRACK);
        if (style == StatBarStyle.MODERN) {
            if (filled > 0) graphics.fill(left + 2, y + height - 3, left + 2 + filled, y + height - 1, color);
            if (totalFilled > filled) graphics.fill(left + 2 + filled, y + height - 3,
                    left + 2 + totalFilled, y + height - 1, extraColor);
        } else {
            if (filled > 0) graphics.fillGradient(left + 2, y + 2, left + 2 + filled, y + height - 1, color, shade(color, 0.66F));
            if (totalFilled > filled) graphics.fillGradient(left + 2 + filled, y + 2,
                    left + 2 + totalFilled, y + height - 1, extraColor, shade(extraColor, 0.66F));
        }
        graphics.blitSprite(style == StatBarStyle.MODERN ? MODERN_FRAME : ORIGINAL_FRAME, left, y, width, height);
    }

    private static int shade(int color, float factor) {
        return 0xFF000000 | ((int) (((color >>> 16) & 255) * factor) << 16)
                | ((int) (((color >>> 8) & 255) * factor) << 8) | (int) ((color & 255) * factor);
    }
}
