package cn.aatu.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;

/** Known auxiliary boss events in Cataclysm 3.33; phase health bars remain health bars. */
public final class CataclysmBossMeters {
    private static final int REMNANT_HEALTH = 7;
    private static final int REMNANT_RAGE = 8;
    private static final int MALEDICTUS_HEALTH = 9;
    private static final int MALEDICTUS_RAGE = 10;
    private static final int BAR_WIDTH = 5;
    private static final int BAR_HEIGHT = 15;
    private static final int BAR_GAP = 3;
    private static final int LABEL_GAP = 2;
    private static final int LABEL_PADDING = 3;
    private static final int RAGE_MAX = 5;
    private static final float LABEL_SCALE = 0.65F;

    private CataclysmBossMeters() {}

    public static boolean isRage(BossEvent event) {
        int type = HudCompatibility.cataclysmRenderType(event);
        return type == REMNANT_RAGE || type == MALEDICTUS_RAGE;
    }

    public static boolean hasMeter(BossEvent event) {
        return auxiliaryType(HudCompatibility.cataclysmRenderType(event)) >= 0;
    }

    public static int auxiliaryType(int healthType) {
        return switch (healthType) {
            case REMNANT_HEALTH -> REMNANT_RAGE;
            case MALEDICTUS_HEALTH -> MALEDICTUS_RAGE;
            default -> -1;
        };
    }

    public static int outsideWidth(Font font) {
        int labelWidth = Mth.ceil(font.width(Component.translatable("aatu_ui.remnant_rage", RAGE_MAX)) * LABEL_SCALE);
        return BAR_GAP + BAR_WIDTH + LABEL_GAP + labelWidth + LABEL_PADDING;
    }

    public static Side side(Font font, int slot, int count, int x, int width, int height) {
        int gap = BossLayout.scaledGap();
        int baseX = HudPart.BOSS.x(width, height);
        int baseY = HudPart.BOSS.y(width, height);
        int columns = Math.max(1, (width - baseX + gap) / BossLayout.columnStep());
        int rows = Math.max(1, (height - baseY + gap) / BossLayout.rowStep());
        boolean horizontal = UiSettings.get().bossHorizontal;
        boolean neighborLeft = horizontal ? slot % columns > 0 : slot >= rows;
        boolean neighborRight = horizontal ? slot % columns < columns - 1 && slot + 1 < count : slot + rows < count;
        int required = HudPart.BOSS.scaled(outsideWidth(font));
        boolean left = x >= required && (!neighborLeft || gap >= required);
        boolean right = width - x - HudPart.BOSS.pixelWidth() >= required && (!neighborRight || gap >= required);
        if (UiSettings.get().remnantRageRight) {
            return right ? Side.RIGHT : left ? Side.LEFT : Side.INSIDE;
        }
        return left ? Side.LEFT : right ? Side.RIGHT : Side.INSIDE;
    }

    public static void render(GuiGraphics graphics, int x, int y, int absoluteX, int slot, LerpingBossEvent rage) {
        int color = HudCompatibility.cataclysmRenderType(rage) == MALEDICTUS_RAGE ? 0xFFB96CF3 : 0xFFF0A33B;
        draw(graphics, x, y, absoluteX, slot, BossLayout.displayedCount(), rage.getProgress(), color);
    }

    public static void preview(GuiGraphics graphics, int x, int y, int slot, int count) {
        draw(graphics, x, y, x, slot, count, 0.6F, 0xFFF0A33B);
    }

    private static void draw(GuiGraphics graphics, int x, int y, int absoluteX, int slot, int count, float progress, int color) {
        Font font = Minecraft.getInstance().font;
        float value = Mth.clamp(progress, 0, 1);
        Component label = Component.translatable("aatu_ui.remnant_rage", Math.round(value * RAGE_MAX));
        Side side = side(font, slot, count, absoluteX, graphics.guiWidth(), graphics.guiHeight());
        int barX = switch (side) {
            case LEFT -> x - BAR_GAP - BAR_WIDTH;
            case RIGHT -> x + 182 + BAR_GAP;
            case INSIDE -> x + 1;
        };
        int top = y + 1;
        graphics.fill(barX, top, barX + BAR_WIDTH, top + BAR_HEIGHT, 0xE0181B20);
        int filled = Math.round((BAR_HEIGHT - 2) * value);
        if (filled > 0) graphics.fill(barX + 1, top + BAR_HEIGHT - 1 - filled,
                barX + BAR_WIDTH - 1, top + BAR_HEIGHT - 1, color);
        float labelWidth = font.width(label) * LABEL_SCALE;
        float labelX = side == Side.LEFT ? barX - LABEL_GAP - labelWidth : barX + BAR_WIDTH + LABEL_GAP;
        float labelY = side == Side.INSIDE ? y + 10 : y + (17 - font.lineHeight * LABEL_SCALE) / 2.0F;
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(labelX, labelY, 0);
            graphics.pose().scale(LABEL_SCALE, LABEL_SCALE, 1);
            graphics.drawString(font, label, 0, 0, 0xFFFFFFFF, true);
        } finally {
            graphics.pose().popPose();
        }
    }

    public enum Side { LEFT, RIGHT, INSIDE }
}
