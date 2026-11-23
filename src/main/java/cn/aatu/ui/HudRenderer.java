package cn.aatu.ui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.fml.ModList;

public final class HudRenderer {
    private static final ResourceLocation CHAMPION_LAYER = ResourceLocation.fromNamespaceAndPath("champions", "health_overlay");
    private static final ResourceLocation CHAMPION_BAR_TEXTURE = ResourceLocation.fromNamespaceAndPath("champions", "textures/gui/bars.png");
    private static final ResourceLocation CHAMPION_STAR_TEXTURE = ResourceLocation.fromNamespaceAndPath("champions", "textures/gui/staricon.png");
    private static final int CHAMPION_PREVIEW_COLOR = 0xFFAA8BEA;
    private static final int CHAMPION_PREVIEW_STARS = 3;
    private static final int BACKGROUND = 0xB0181B20;
    private static final int HEALTH_COLOR = 0xFFEF5969;
    private static final int FOOD_COLOR = 0xFFF5B853;

    private HudRenderer() {}

    public static void layer(RenderGuiLayerEvent.Pre event) {
        ResourceLocation name = event.getName();
        if (name.equals(VanillaGuiLayers.PLAYER_HEALTH)) {
            renderHealthLayer(event, HudPart.HEALTH);
        } else if (name.equals(VanillaGuiLayers.FOOD_LEVEL)) {
            renderHealthLayer(event, HudPart.HUNGER);
        } else if (name.equals(VanillaGuiLayers.ARMOR_LEVEL)) {
            prepareOriginalLayer(event, HudPart.ARMOR);
        } else if (name.equals(VanillaGuiLayers.HOTBAR)) {
            renderHotbarLayer(event);
        } else if (ExperienceHud.isLayer(name)) {
            ExperienceHud.renderLayer(event);
        } else if (name.equals(VanillaGuiLayers.BOSS_OVERLAY)) {
            renderBossLayer(event);
        } else if (name.equals(CHAMPION_LAYER)) {
            renderChampionLayer(event);
        } else if (name.equals(VanillaGuiLayers.EFFECTS)) {
            EffectsHud.renderLayer(event);
        }
    }

    private static void renderHotbarLayer(RenderGuiLayerEvent.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gameMode == null) return;
        if (minecraft.screen instanceof HudEditorScreen) {
            event.setCanceled(true);
            return;
        }
        if (minecraft.gameMode.getPlayerMode() == GameType.SPECTATOR) return;
        HudPart part = HudPart.HOTBAR;
        if (!part.placement().visible) {
            event.setCanceled(true);
        } else if (!UiSettings.get().hotbarVertical && part.placement().style() == HudStyle.ORIGINAL) {
            prepareOriginalLayer(event, part);
        } else {
            event.setCanceled(true);
            HotbarHud.render(event.getGuiGraphics(), event.getPartialTick());
        }
    }

    private static void renderHealthLayer(RenderGuiLayerEvent.Pre event, HudPart part) {
        if (part.placement().style() == HudStyle.ORIGINAL) {
            prepareOriginalLayer(event, part);
        } else {
            event.setCanceled(true);
            renderPlayerBar(event.getGuiGraphics(), part);
        }
    }

    private static void renderBossLayer(RenderGuiLayerEvent.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!HudPart.BOSS.placement().visible || minecraft.screen instanceof HudEditorScreen) {
            event.setCanceled(true);
            BossLayout.endFrame();
        } else if (minecraft.gui.getBossOverlay() instanceof BossLayoutHook) {
            BossLayout.beginFrame();
        } else if (HudPart.BOSS.placement().style() == HudStyle.ORIGINAL) {
            renderOriginalLayer(event, HudPart.BOSS, event.getGuiGraphics().guiWidth() / 2 - 91, 3);
        }
    }

    public static void layerDone(RenderGuiLayerEvent.Post event) {
        if (event.getName().equals(VanillaGuiLayers.BOSS_OVERLAY)) BossLayout.endFrame();
    }

    private static void prepareOriginalLayer(RenderGuiLayerEvent.Pre event, HudPart part) {
        if (!part.placement().visible || Minecraft.getInstance().screen instanceof HudEditorScreen) event.setCanceled(true);
    }

    private static void renderOriginalLayer(RenderGuiLayerEvent.Pre event, HudPart part, int originalX, int originalY) {
        if (!part.placement().visible || Minecraft.getInstance().screen instanceof HudEditorScreen) {
            event.setCanceled(true);
            return;
        }
        if (!part.placement().hasPosition() && part.scale() == 1) return;
        GuiGraphics graphics = event.getGuiGraphics();
        event.setCanceled(true);
        graphics.pose().pushPose();
        try {
            HudTransform.apply(graphics, part, part.x(graphics.guiWidth(), graphics.guiHeight()),
                    part.y(graphics.guiWidth(), graphics.guiHeight()), originalX, originalY);
            event.getLayer().render(graphics, event.getPartialTick());
        } finally {
            graphics.pose().popPose();
        }
    }

    private static void renderPlayerBar(GuiGraphics graphics, HudPart part) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.hideGui || minecraft.screen instanceof HudEditorScreen || minecraft.gameMode == null || !minecraft.gameMode.canHurtPlayer()
                || !(minecraft.getCameraEntity() instanceof Player player) || !part.placement().visible) return;
        if (part == HudPart.HUNGER && player.getVehicle() instanceof LivingEntity) return;

        int x = part.x(graphics.guiWidth(), graphics.guiHeight());
        int y = part.y(graphics.guiWidth(), graphics.guiHeight());
        graphics.pose().pushPose();
        try {
            HudTransform.apply(graphics, part, x, y);
            drawPlayerBar(graphics, part, player, x, y);
        } finally {
            graphics.pose().popPose();
        }
    }

    private static void drawPlayerBar(GuiGraphics graphics, HudPart part, Player player, int x, int y) {
        Minecraft minecraft = Minecraft.getInstance();
        if (part == HudPart.HEALTH) {
            float maximum = Math.max(1, player.getMaxHealth());
            float health = Mth.clamp(player.getHealth(), 0, maximum);
            int color = HudColors.health(player);
            int absorption = Math.max(0, Mth.ceil(player.getAbsorptionAmount()));
            String value = Mth.ceil(health) + "/" + Mth.ceil(maximum) + (absorption > 0 ? " +" + absorption : "");
            drawBar(graphics, x, y, part.width, health / maximum, color, value, part.placement().style());
            if (absorption > 0) graphics.fill(x, y + 8, x + part.width, y + 10, 0xFFE5BA54);
            if (!part.placement().hasPosition()) minecraft.gui.leftHeight += part.scaled(10);
        } else {
            int food = player.getFoodData().getFoodLevel();
            drawBar(graphics, x, y, part.width, Mth.clamp(food / 20.0F, 0, 1), HudColors.hunger(player), food + "/20", part.placement().style());
            if (!part.placement().hasPosition()) minecraft.gui.rightHeight += part.scaled(10);
        }
    }

    private static void renderChampionLayer(RenderGuiLayerEvent.Pre event) {
        UiSettings.get().prepareChampionAffixes(event.getGuiGraphics().guiWidth(), event.getGuiGraphics().guiHeight());
        UiSettings.Placement placement = HudPart.CHAMPION.placement();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.hideGui || minecraft.screen instanceof HudEditorScreen
                || (!placement.visible && !HudPart.CHAMPION_AFFIXES.placement().visible)) {
            event.setCanceled(true);
            HudCompatibility.championHidden();
            return;
        }
        GuiGraphics graphics = event.getGuiGraphics();
        int originalX = graphics.guiWidth() / 2 - 91 + HudCompatibility.championOffsetX();
        int originalY = 1 + HudCompatibility.championOffsetY();
        ChampionAffixAnchor.Position position = ChampionAffixAnchor.championPosition(minecraft.font,
                graphics.guiWidth(), graphics.guiHeight(), false);
        event.setCanceled(true);
        graphics.pose().pushPose();
        try {
            HudTransform.apply(graphics, HudPart.CHAMPION, position.x(), position.y(), originalX, originalY);
            event.getLayer().render(graphics, event.getPartialTick());
        } finally {
            graphics.pose().popPose();
        }
        HudCompatibility.championMoved(originalX, originalY, position.x(), position.y(), HudPart.CHAMPION.scale());
    }

    public static void bossGuard(CustomizeGuiOverlayEvent.BossEventProgress event) {
        if (!BossLayout.insideEvent()) return;
        if (BossLayout.currentAuxiliary()) BossLayout.hideAuxiliary(event);
        else if (!BossLayout.currentVisible()) BossLayout.stopAfterCurrent(event);
    }

    public static void boss(CustomizeGuiOverlayEvent.BossEventProgress event) {
        if (event.isCanceled() || BossLayout.currentAuxiliary() || HudPart.BOSS.placement().style() == HudStyle.ORIGINAL) return;
        event.setCanceled(true);
        if (!HudPart.BOSS.placement().visible || Minecraft.getInstance().screen instanceof HudEditorScreen) return;
        GuiGraphics graphics = event.getGuiGraphics();
        int x = BossLayout.insideEvent() ? event.getX() : HudPart.BOSS.x(graphics.guiWidth(), graphics.guiHeight());
        int y = BossLayout.insideEvent() ? event.getY() - 9
                : HudPart.BOSS.y(graphics.guiWidth(), graphics.guiHeight()) + event.getY() - 12;
        graphics.pose().pushPose();
        try {
            if (!BossLayout.insideEvent()) HudTransform.apply(graphics, HudPart.BOSS, x, y);
            drawBossEvent(graphics, event, x, y);
        } finally {
            graphics.pose().popPose();
        }
    }

    private static void drawBossEvent(GuiGraphics graphics, CustomizeGuiOverlayEvent.BossEventProgress event, int x, int y) {
        BossEvent boss = event.getBossEvent();
        drawBoss(graphics, x, y, boss.getProgress(), bossColor(boss.getColor()),
                HudCompatibility.bossTitle(boss, boss.getName()), HudPart.BOSS.placement().style());
        if (BossLayout.currentRage() != null) {
            CataclysmBossMeters.render(graphics, x, y, x + BossLayout.deltaX(), BossLayout.currentSlot(), BossLayout.currentRage());
        }
        int notches = switch (boss.getOverlay()) {
            case NOTCHED_6 -> 6;
            case NOTCHED_10 -> 10;
            case NOTCHED_12 -> 12;
            case NOTCHED_20 -> 20;
            default -> 0;
        };
        for (int i = 1; i < notches; i++) {
            int notch = x + Math.round(182.0F * i / notches);
            graphics.fill(notch, y + 12, notch + 1, y + 16, 0xB0181B20);
        }
    }

    private static int bossColor(BossEvent.BossBarColor color) {
        return switch (color) {
            case PINK -> 0xFFED82B7;
            case BLUE -> 0xFF6CAAF5;
            case RED -> 0xFFEA6572;
            case GREEN -> 0xFF79C989;
            case YELLOW -> 0xFFE9C65F;
            case PURPLE -> 0xFFAA8BEA;
            case WHITE -> 0xFFDCE1E7;
        };
    }

    private static void drawBar(GuiGraphics graphics, int x, int y, int width, float fraction, int color, String value, HudStyle style) {
        graphics.fill(x, y, x + width, y + 10, BACKGROUND);
        int fillTop = style == HudStyle.COMPACT ? y + 1 : y + 8;
        int fillBottom = style == HudStyle.COMPACT ? y + 9 : y + 10;
        graphics.fill(x + 1, fillTop, x + 1 + Math.round((width - 2) * Mth.clamp(fraction, 0, 1)), fillBottom, color);
        graphics.drawString(Minecraft.getInstance().font, value, x + 3, y, 0xFFFFFFFF, true);
    }

    private static void drawBoss(GuiGraphics graphics, int x, int y, float fraction, int color, Component name, HudStyle style) {
        graphics.drawCenteredString(Minecraft.getInstance().font, name, x + 91, y, 0xFFFFFFFF);
        graphics.fill(x, y + 11, x + 182, y + 17, BACKGROUND);
        int top = style == HudStyle.COMPACT ? y + 11 : y + 12;
        int bottom = style == HudStyle.COMPACT ? y + 17 : y + 16;
        graphics.fill(x + 1, top, x + 1 + Math.round(180 * Mth.clamp(fraction, 0, 1)), bottom, color);
    }

    private static void previewChampion(GuiGraphics graphics, int x, int y, HudStyle style) {
        // Use the same 26-pixel layout and texture regions as Champions 2.1.11.3 HUDHelper.
        int barY = y + 20;
        int filledWidth = (int) (183 * 0.7F);
        float[] previousColor = RenderSystem.getShaderColor().clone();
        graphics.flush();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableBlend();
        try {
            RenderSystem.setShaderColor(((CHAMPION_PREVIEW_COLOR >> 16) & 255) / 255.0F,
                    ((CHAMPION_PREVIEW_COLOR >> 8) & 255) / 255.0F,
                    (CHAMPION_PREVIEW_COLOR & 255) / 255.0F, 1.0F);
            if (style == HudStyle.ORIGINAL) {
                graphics.blit(CHAMPION_BAR_TEXTURE, x, barY, 0.0F, 60.0F, 182, 5, 256, 256);
                graphics.blit(CHAMPION_BAR_TEXTURE, x, barY, 0.0F, 65.0F, filledWidth, 5, 256, 256);
            }
            int starX = x + 86 - 5 * (CHAMPION_PREVIEW_STARS - 1);
            for (int star = 0; star < CHAMPION_PREVIEW_STARS; star++) {
                graphics.blit(CHAMPION_STAR_TEXTURE, starX + star * 10, y, 0.0F, 0.0F, 9, 9, 9, 9);
            }
            graphics.flush();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            if (style != HudStyle.ORIGINAL) {
                graphics.fill(x, barY, x + 182, barY + 5, BACKGROUND);
                int top = style == HudStyle.COMPACT ? barY : barY + 2;
                graphics.fill(x, top, x + filledWidth, barY + 5, CHAMPION_PREVIEW_COLOR);
            }
            graphics.drawCenteredString(Minecraft.getInstance().font, Component.translatable(HudPart.CHAMPION.label),
                    x + 91, y + 11, CHAMPION_PREVIEW_COLOR);
            graphics.flush();
        } finally {
            RenderSystem.setShaderColor(previousColor[0], previousColor[1], previousColor[2], previousColor[3]);
            RenderSystem.disableBlend();
        }
    }

    public static void preview(GuiGraphics graphics, HudPart part) {
        int x = part.x(graphics.guiWidth(), graphics.guiHeight());
        int y = part.y(graphics.guiWidth(), graphics.guiHeight());
        if (part == HudPart.CHAMPION) {
            ChampionAffixAnchor.Position position = ChampionAffixAnchor.championPosition(Minecraft.getInstance().font,
                    graphics.guiWidth(), graphics.guiHeight(), true);
            x = Math.round(position.x());
            y = Math.round(position.y());
        }
        graphics.pose().pushPose();
        try {
            // Boss rows and attached affixes have individually resolved screen positions.
            if (part != HudPart.BOSS && part != HudPart.CHAMPION_AFFIXES) HudTransform.apply(graphics, part, x, y);
            previewContent(graphics, part, x, y);
        } finally {
            graphics.pose().popPose();
        }
    }

    private static void previewContent(GuiGraphics graphics, HudPart part, int x, int y) {
        HudStyle style = part.placement().style();
        switch (part) {
            case HEALTH, HUNGER -> {
                if (style == HudStyle.ORIGINAL) {
                    ResourceLocation icon = ResourceLocation.withDefaultNamespace(part == HudPart.HEALTH ? "hud/heart/full" : "hud/food_full");
                    for (int i = 0; i < 10; i++) graphics.blitSprite(icon, x + i * 8, y, 9, 9);
                } else {
                    drawBar(graphics, x, y, part.width, part == HudPart.HEALTH ? 0.8F : 0.75F,
                            part == HudPart.HEALTH ? HudColors.health(Minecraft.getInstance().player) : HudColors.hunger(Minecraft.getInstance().player),
                            part == HudPart.HEALTH ? "16/20" : "15/20", style);
                }
            }
            case ARMOR -> {
                ResourceLocation icon = ResourceLocation.withDefaultNamespace("hud/armor_full");
                for (int i = 0; i < 10; i++) graphics.blitSprite(icon, x + i * 8, y, 9, 9);
            }
            case EXPERIENCE -> ExperienceHud.preview(graphics, x, y);
            case BOSS -> {
                int count = Math.min(3, UiSettings.get().bossLimit == 0 ? 3 : Math.max(1, UiSettings.get().bossLimit));
                BossLayout.Position bottom = null;
                int bottomSlot = 0;
                for (int slot = 0; slot < count; slot++) {
                    BossLayout.Position position = BossLayout.position(slot, x, y, graphics.guiWidth(), graphics.guiHeight());
                    int barY = position.y() + part.scaled(style == HudStyle.ORIGINAL ? 9 : 11);
                    int barHeight = part.scaled(style == HudStyle.ORIGINAL ? 5 : 6);
                    if (position.x() + part.pixelWidth() > 0 && position.x() < graphics.guiWidth()
                            && barY + barHeight > 0 && barY < graphics.guiHeight()
                            && (bottom == null || position.y() >= bottom.y())) {
                        bottom = position;
                        bottomSlot = slot;
                    }
                    graphics.pose().pushPose();
                    try {
                        HudTransform.apply(graphics, part, position.x(), position.y());
                        if (style == HudStyle.ORIGINAL) {
                            graphics.drawCenteredString(Minecraft.getInstance().font, Component.translatable(part.label), position.x() + 91, position.y(), 0xFFFFFFFF);
                            graphics.blitSprite(ResourceLocation.withDefaultNamespace("boss_bar/purple_background"), position.x(), position.y() + 9, 182, 5);
                            graphics.blitSprite(ResourceLocation.withDefaultNamespace("boss_bar/purple_progress"), position.x(), position.y() + 9, 126, 5);
                        } else {
                            drawBoss(graphics, position.x(), position.y(), 0.7F, 0xFFAA8BEA, Component.translatable(part.label), style);
                        }
                    } finally {
                        graphics.pose().popPose();
                    }
                }
                if (bottom != null && ModList.get().isLoaded("cataclysm")) {
                    graphics.pose().pushPose();
                    try {
                        HudTransform.apply(graphics, part, bottom.x(), bottom.y());
                        CataclysmBossMeters.preview(graphics, bottom.x(), bottom.y(), bottomSlot, count);
                    } finally {
                        graphics.pose().popPose();
                    }
                }
            }
            case CHAMPION -> {
                if (part.placement().visible) previewChampion(graphics, x, y, style);
            }
            case CHAMPION_AFFIXES -> {
                Font font = Minecraft.getInstance().font;
                String text = Component.translatable("aatu_ui.affixes").getString();
                ChampionAffixAnchor.Position position = ChampionAffixAnchor.textPosition(font, text, graphics.guiWidth(), graphics.guiHeight(), true);
                if (position.scale() > 0) {
                    graphics.pose().pushPose();
                    try {
                        graphics.pose().translate(position.x(), position.y(), 0);
                        graphics.pose().scale(position.scale(), position.scale(), 1);
                        graphics.drawString(font, text, 0, 0, CHAMPION_PREVIEW_COLOR, true);
                    } finally {
                        graphics.pose().popPose();
                    }
                }
            }
            case SPELLS -> {
                int background = style == HudStyle.ORIGINAL ? 0xD03A3A3A : style == HudStyle.COMPACT ? 0x803A4663 : BACKGROUND;
                graphics.fill(x, y, x + part.logicalWidth(), y + part.logicalHeight(), background);
                boolean vertical = UiSettings.get().spellVertical;
                for (int i = 0; i < HudCompatibility.spellCount(); i++) {
                    int iconX = x + 3 + (vertical ? 0 : i * SpellLayout.SLOT_STEP);
                    int iconY = y + 3 + (vertical ? SpellLayout.VERTICAL_LABEL_SPACE + i * SpellLayout.VERTICAL_ROW_HEIGHT : 0);
                    graphics.fill(iconX, iconY, iconX + 16, iconY + 16, 0xFF7186B4);
                }
            }
            case EFFECTS -> EffectsHud.preview(graphics, x, y);
            case HOTBAR -> HotbarHud.preview(graphics, x, y);
            case STATS -> StatsHud.preview(graphics, x, y);
        }
    }
}
