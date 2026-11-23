package cn.aatu.ui;

import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.EffectRenderingInventoryScreen;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.extensions.common.IClientMobEffectExtensions;

public final class EffectsHud {
    private static final int CELL_WIDTH = 25;
    private static final int MODERN_ROW_HEIGHT = 34;
    private static final int LEVEL_TOP_SPACE = 8;
    private static final int ICON_CENTER = 12;
    private static final float SMALL_TEXT_SCALE = 0.65F;
    private static final ResourceLocation NORMAL_BACKGROUND = ResourceLocation.withDefaultNamespace("hud/effect_background");
    private static final ResourceLocation AMBIENT_BACKGROUND = ResourceLocation.withDefaultNamespace("hud/effect_background_ambient");
    private static Player cachedPlayer;
    private static int cachedTick = Integer.MIN_VALUE;
    private static List<MobEffectInstance> cachedEffects = List.of();
    private static int cachedBeneficial;
    private static int cachedHarmful;
    private static final Map<MobEffectInstance, String> cachedDurations = new IdentityHashMap<>();

    private EffectsHud() {}

    public static void clear() {
        cachedPlayer = null;
        cachedTick = Integer.MIN_VALUE;
        cachedEffects = List.of();
        cachedBeneficial = 0;
        cachedHarmful = 0;
        cachedDurations.clear();
    }

    private static List<MobEffectInstance> activeEffects() {
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            cachedPlayer = null;
            cachedEffects = List.of();
            cachedBeneficial = 0;
            cachedHarmful = 0;
            cachedDurations.clear();
            return cachedEffects;
        }
        if (player != cachedPlayer || player.tickCount != cachedTick) {
            cachedPlayer = player;
            cachedTick = player.tickCount;
            cachedDurations.clear();
            List<MobEffectInstance> effects = new ArrayList<>(player.getActiveEffects());
            effects.removeIf(effect -> !effect.showIcon() || !IClientMobEffectExtensions.of(effect).isVisibleInGui(effect));
            effects.sort(Comparator.reverseOrder());
            cachedBeneficial = 0;
            cachedHarmful = 0;
            for (MobEffectInstance effect : effects) {
                if (effect.getEffect().value().isBeneficial()) cachedBeneficial++;
                else cachedHarmful++;
            }
            cachedEffects = List.copyOf(effects);
        }
        return cachedEffects;
    }

    private static int columns() {
        List<MobEffectInstance> effects = activeEffects();
        if (effects.isEmpty()) return 3;
        return Math.max(1, Math.max(cachedBeneficial, cachedHarmful));
    }

    public static int pixelWidth() {
        int count = columns();
        if (!UiSettings.get().effectVertical) return count * CELL_WIDTH;
        int groups = (cachedBeneficial > 0 ? 1 : 0) + (cachedHarmful > 0 ? 1 : 0);
        return Math.max(1, groups) * CELL_WIDTH;
    }

    public static int pixelHeight() {
        HudStyle style = HudPart.EFFECTS.placement().style();
        int rowHeight = rowHeight(style);
        return UiSettings.get().effectVertical ? columns() * rowHeight : rowHeight * 2;
    }

    private static int levelTopSpace(HudStyle style) {
        return style != HudStyle.ORIGINAL && UiSettings.get().effectLevels ? LEVEL_TOP_SPACE : 0;
    }

    private static int rowHeight(HudStyle style) {
        return (style == HudStyle.MODERN ? MODERN_ROW_HEIGHT : 26) + levelTopSpace(style);
    }

    public static void renderLayer(RenderGuiLayerEvent.Pre event) {
        HudPart part = HudPart.EFFECTS;
        Minecraft minecraft = Minecraft.getInstance();
        if (!part.placement().visible || minecraft.options.hideGui || minecraft.screen instanceof HudEditorScreen) {
            event.setCanceled(true);
            return;
        }
        GuiGraphics graphics = event.getGuiGraphics();
        if (part.placement().style() == HudStyle.ORIGINAL && !UiSettings.get().effectVertical) {
            if (!part.placement().hasPosition() && part.scale() == 1) return;
            event.setCanceled(true);
            graphics.pose().pushPose();
            try {
                HudTransform.apply(graphics, part, part.x(graphics.guiWidth(), graphics.guiHeight()),
                        part.y(graphics.guiWidth(), graphics.guiHeight()),
                        graphics.guiWidth() - pixelWidth(), minecraft.isDemo() ? 16 : 1);
                event.getLayer().render(graphics, event.getPartialTick());
            } finally {
                graphics.pose().popPose();
            }
            return;
        }
        event.setCanceled(true);
        if (minecraft.player == null || minecraft.screen instanceof EffectRenderingInventoryScreen screen && screen.canSeeEffects()) return;
        int x = part.x(graphics.guiWidth(), graphics.guiHeight());
        int y = part.y(graphics.guiWidth(), graphics.guiHeight());
        graphics.pose().pushPose();
        try {
            HudTransform.apply(graphics, part, x, y);
            renderEffects(graphics, x, y);
        } finally {
            graphics.pose().popPose();
        }
    }

    private static void renderEffects(GuiGraphics graphics, int x, int y) {
        Minecraft minecraft = Minecraft.getInstance();
        int positive = 0;
        int negative = 0;
        int width = pixelWidth();
        HudStyle style = HudPart.EFFECTS.placement().style();
        boolean compact = style == HudStyle.COMPACT;
        int rowHeight = rowHeight(style);
        int levelSpace = levelTopSpace(style);
        UiSettings settings = UiSettings.get();
        boolean vertical = settings.effectVertical;
        RenderSystem.enableBlend();
        try {
            for (MobEffectInstance effect : activeEffects()) {
                boolean beneficial = effect.getEffect().value().isBeneficial();
                int index = beneficial ? positive++ : negative++;
                int iconX = vertical ? x + (beneficial || cachedBeneficial == 0 ? 0 : CELL_WIDTH)
                        : x + width - CELL_WIDTH * (index + 1);
                int rowY = vertical ? y + index * rowHeight : y + (beneficial ? 0 : rowHeight);
                int iconY = rowY + levelSpace;
                graphics.blitSprite(effect.isAmbient() ? AMBIENT_BACKGROUND : NORMAL_BACKGROUND, iconX, iconY, 24, 24);
                IClientMobEffectExtensions renderer = IClientMobEffectExtensions.of(effect);
                float alpha = iconAlpha(effect);
                if (!renderer.renderGuiIcon(effect, minecraft.gui, graphics, iconX, iconY, 0, alpha)) {
                    graphics.setColor(1.0F, 1.0F, 1.0F, alpha);
                    drawIcon(graphics, iconX, iconY, effect.getEffect());
                    graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
                }
                if (style != HudStyle.ORIGINAL && settings.effectLevels) {
                    drawLevel(graphics, Integer.toString(effect.getAmplifier() + 1), iconX, rowY);
                }
                if (style != HudStyle.ORIGINAL && settings.effectDuration) {
                    float tickRate = minecraft.level == null ? 20.0F : minecraft.level.tickRateManager().tickrate();
                    String time = cachedDurations.computeIfAbsent(effect,
                            active -> MobEffectUtil.formatDuration(active, 1.0F, tickRate).getString());
                    drawCenteredSmall(graphics, time, iconX + ICON_CENTER, iconY + (compact ? 18 : 24), SMALL_TEXT_SCALE);
                }
            }
        } finally {
            graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.disableBlend();
        }
    }

    private static float iconAlpha(MobEffectInstance effect) {
        if (effect.isAmbient() || !effect.endsWithin(200)) return 1.0F;
        int duration = effect.getDuration();
        int remainingSeconds = 10 - duration / 20;
        return Mth.clamp(duration / 50.0F * 0.5F, 0.0F, 0.5F)
                + Mth.cos(duration * (float) Math.PI / 5.0F) * Mth.clamp(remainingSeconds / 10.0F * 0.25F, 0.0F, 0.25F);
    }

    private static void drawIcon(GuiGraphics graphics, int x, int y, Holder<MobEffect> effect) {
        TextureAtlasSprite icon = Minecraft.getInstance().getMobEffectTextures().get(effect);
        graphics.blit(x + 3, y + 3, 0, 18, 18, icon);
    }

    private static void drawLevel(GuiGraphics graphics, String value, int iconX, int rowY) {
        int width = Minecraft.getInstance().font.width(value);
        float scale = Math.min(SMALL_TEXT_SCALE, (CELL_WIDTH - 2.0F) / Math.max(1, width));
        drawCenteredSmall(graphics, value, iconX + ICON_CENTER, rowY, scale);
    }

    private static void drawCenteredSmall(GuiGraphics graphics, String value, int centerX, int y, float scale) {
        var font = Minecraft.getInstance().font;
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(centerX, y, 0);
            graphics.pose().scale(scale, scale, 1);
            graphics.drawString(font, value, -font.width(value) / 2, 0, 0xFFFFFFFF, true);
        } finally {
            graphics.pose().popPose();
        }
    }

    public static void preview(GuiGraphics graphics, int x, int y) {
        if (!activeEffects().isEmpty()) {
            renderEffects(graphics, x, y);
            return;
        }
        List<Holder<MobEffect>> examples = List.of(MobEffects.MOVEMENT_SPEED, MobEffects.DAMAGE_RESISTANCE, MobEffects.REGENERATION);
        HudStyle style = HudPart.EFFECTS.placement().style();
        int rowHeight = rowHeight(style);
        int levelSpace = levelTopSpace(style);
        for (int i = 0; i < examples.size(); i++) {
            int iconX = x + (UiSettings.get().effectVertical ? 0 : i * CELL_WIDTH);
            int rowY = y + (UiSettings.get().effectVertical ? i * rowHeight : 0);
            int iconY = rowY + levelSpace;
            graphics.blitSprite(NORMAL_BACKGROUND, iconX, iconY, 24, 24);
            drawIcon(graphics, iconX, iconY, examples.get(i));
            if (style != HudStyle.ORIGINAL) {
                if (UiSettings.get().effectLevels) drawLevel(graphics, "1", iconX, rowY);
                if (UiSettings.get().effectDuration) {
                    int timerY = style == HudStyle.COMPACT ? iconY + 18 : iconY + 24;
                    drawCenteredSmall(graphics, "1:00", iconX + ICON_CENTER, timerY, SMALL_TEXT_SCALE);
                }
            }
        }
    }
}
