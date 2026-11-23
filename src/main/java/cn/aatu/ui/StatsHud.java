package cn.aatu.ui;

import com.mojang.blaze3d.systems.RenderSystem;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

public final class StatsHud {
    private static final int IDENTITY_HEIGHT = 20;
    private static final int FACE_SIZE = 16;
    private static final int HORIZONTAL_PADDING = 4;
    private static final int VERTICAL_PADDING = 2;
    private static final int COLUMN_GAP = 8;
    private static final int ROW_GAP = 2;
    private static final DecimalFormat NUMBER = new DecimalFormat("0.#", DecimalFormatSymbols.getInstance(Locale.ROOT));
    private static Player cachedPlayer;
    private static int cachedTick = Integer.MIN_VALUE;
    private static double cachedHealth = Double.NaN;
    private static double cachedMaxHealth = Double.NaN;
    private static double cachedAbsorption = Double.NaN;
    private static double cachedArmor = Double.NaN;
    private static double cachedToughness = Double.NaN;
    private static double cachedAttack = Double.NaN;
    private static DefenseEstimate.Result cachedDefense;
    private static int cachedHunger = Integer.MIN_VALUE;
    private static int cachedExperienceLevel = Integer.MIN_VALUE;
    private static double cachedExperienceProgress = Double.NaN;
    private static Component healthLine;
    private static Component hungerLine;
    private static Component experienceLine;
    private static Component armorLine;
    private static Component toughnessLine;
    private static Component attackLine;
    private static Component defenseLine;
    private static Component poolLine;
    private static Component bypassLine;
    private static Component tpsLine;
    private static ServerPerformance.Reading cachedPerformance;
    private static long nextPerformanceUpdate;
    private static Grid cachedGrid;

    private record Grid(int columns, int barWidth, int barHeight, int itemCount, boolean showIdentity) {
        int width() {
            return columns * barWidth + (columns - 1) * COLUMN_GAP + HORIZONTAL_PADDING * 2;
        }

        int height() {
            int rows = (itemCount + columns - 1) / columns;
            return Math.max(12, rows * rowHeight() + (showIdentity ? IDENTITY_HEIGHT : 0) + VERTICAL_PADDING * 2);
        }

        int rowHeight() {
            return barHeight + ROW_GAP;
        }

        int cellX(int x, int index) {
            return x + HORIZONTAL_PADDING + index % columns * (barWidth + COLUMN_GAP);
        }

        int cellY(int y, int index) {
            return y + VERTICAL_PADDING + (showIdentity ? IDENTITY_HEIGHT : 0) + index / columns * rowHeight();
        }
    }

    private StatsHud() {}

    public static void clear() {
        cachedPlayer = null;
        cachedTick = Integer.MIN_VALUE;
        cachedDefense = null;
        cachedPerformance = null;
        nextPerformanceUpdate = 0;
        cachedGrid = null;
    }

    private static Grid grid() {
        UiSettings settings = UiSettings.get();
        int items = (settings.showStatHealth ? 1 : 0) + (settings.showStatHunger ? 1 : 0)
                + (settings.showStatExperience ? 1 : 0)
                + (settings.showStatArmor ? 1 : 0) + (settings.showStatToughness ? 1 : 0)
                + (settings.showStatAttack ? 1 : 0) + (settings.showStatDefense ? 1 : 0)
                + (settings.showStatPool ? 1 : 0) + (settings.showStatBypassProtection ? 1 : 0)
                + (settings.showStatTps ? 1 : 0);
        int columns = Math.min(UiSettings.validStatsColumns(settings.statsColumns), Math.max(1, items));
        int barWidth = UiSettings.validStatBarWidth(settings.statBarWidth);
        int barHeight = UiSettings.validStatBarHeight(settings.statBarHeight);
        if (cachedGrid == null || cachedGrid.columns != columns || cachedGrid.barWidth != barWidth
                || cachedGrid.barHeight != barHeight || cachedGrid.itemCount != items
                || cachedGrid.showIdentity != settings.showPlayerIdentity) {
            // Drawing and editor bounds share geometry; changing the visual style must not resize the panel.
            cachedGrid = new Grid(columns, barWidth, barHeight, items, settings.showPlayerIdentity);
        }
        return cachedGrid;
    }

    public static int pixelWidth() {
        return grid().width();
    }

    public static int pixelHeight() {
        return grid().height();
    }

    public static void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.hideGui || minecraft.screen instanceof HudEditorScreen
                || minecraft.player == null || !HudPart.STATS.placement().visible) return;
        int x = HudPart.STATS.x(graphics.guiWidth(), graphics.guiHeight());
        int y = HudPart.STATS.y(graphics.guiWidth(), graphics.guiHeight());
        graphics.pose().pushPose();
        try {
            HudTransform.apply(graphics, HudPart.STATS, x, y);
            draw(graphics, x, y, minecraft.player);
        } finally {
            graphics.pose().popPose();
        }
    }

    public static void preview(GuiGraphics graphics, int x, int y) {
        draw(graphics, x, y, Minecraft.getInstance().player);
    }

    private static void draw(GuiGraphics graphics, int x, int y, Player player) {
        UiSettings settings = UiSettings.get();
        updateValues(player);
        Grid grid = grid();
        int index = 0;
        if (settings.showPlayerIdentity) {
            drawPlayerIdentity(graphics, x, y + VERTICAL_PADDING, player, grid.width());
        }
        if (settings.showStatHealth) {
            double capacity = Math.max(1, cachedMaxHealth + cachedAbsorption);
            StatBarRenderer.drawSegments(graphics, grid.cellX(x, index), grid.cellY(y, index), grid.barWidth, grid.barHeight, healthLine,
                    cachedHealth / capacity, HudColors.healthWithoutAbsorption(player),
                    cachedAbsorption / capacity, HudColors.ABSORPTION);
            index++;
        }
        if (settings.showStatHunger) {
            drawStat(graphics, grid, x, y, index++, hungerLine, cachedHunger / 20.0, HudColors.hunger(player));
        }
        if (settings.showStatExperience) {
            drawStat(graphics, grid, x, y, index++, experienceLine, cachedExperienceProgress, HudColors.EXPERIENCE);
        }
        if (settings.showStatArmor) {
            drawStat(graphics, grid, x, y, index++, armorLine, -1, HudColors.ARMOR);
        }
        if (settings.showStatToughness) {
            drawStat(graphics, grid, x, y, index++, toughnessLine, -1, HudColors.TOUGHNESS);
        }
        if (settings.showStatAttack) {
            drawStat(graphics, grid, x, y, index++, attackLine, -1, HudColors.ATTACK);
        }
        if (settings.showStatDefense) {
            drawStat(graphics, grid, x, y, index++, defenseLine, -1, HudColors.DEFENSE);
        }
        if (settings.showStatPool) {
            double progress = cachedDefense.mode() == DefenseEstimate.Mode.COMBAT
                    ? (cachedDefense.capacity() > 0 ? cachedDefense.pool() / cachedDefense.capacity() : 0) : 0;
            drawStat(graphics, grid, x, y, index++, poolLine, progress, HudColors.POOL);
        }
        if (settings.showStatBypassProtection) {
            double current = cachedDefense.bypassAbsorption();
            double maximum = cachedDefense.bypassAbsorptionMax();
            double progress = Double.isFinite(current) && Double.isFinite(maximum) && maximum > 0
                    ? current / maximum : 0;
            drawStat(graphics, grid, x, y, index++, bypassLine, progress, HudColors.TOUGHNESS);
        }
        if (settings.showStatTps) {
            updatePerformance();
            boolean ready = cachedPerformance.state() == ServerPerformance.State.READY;
            double fraction = ready ? cachedPerformance.tps() / cachedPerformance.target() : 0;
            int color = !ready ? 0xFF778196 : fraction >= 0.9 ? HudColors.POOL : fraction >= 0.7 ? HudColors.HUNGER : HudColors.HEALTH;
            drawStat(graphics, grid, x, y, index, tpsLine, fraction, color);
        }
    }

    private static void drawStat(GuiGraphics graphics, Grid grid, int x, int y, int index,
                                 Component text, double progress, int color) {
        StatBarRenderer.draw(graphics, grid.cellX(x, index), grid.cellY(y, index), grid.barWidth, grid.barHeight, text, progress, color);
    }

    private static void updateValues(Player player) {
        int tick = player == null ? 0 : player.tickCount;
        boolean needDefense = UiSettings.get().showStatDefense || UiSettings.get().showStatPool
                || UiSettings.get().showStatBypassProtection;
        if (player == cachedPlayer && tick == cachedTick && (!needDefense || cachedDefense != null)) return;
        cachedPlayer = player;
        cachedTick = tick;
        double health = player == null ? 18 : player.getHealth();
        double max = player == null ? 20 : player.getMaxHealth();
        double absorption = player == null ? 4 : player.getAbsorptionAmount();
        absorption = Double.isFinite(absorption) ? Math.max(0, absorption) : 0;
        int hunger = player == null ? 18 : player.getFoodData().getFoodLevel();
        int experienceLevel = player == null ? 12 : Math.max(0, player.experienceLevel);
        double experienceProgress = player == null ? 0.6 : player.experienceProgress;
        experienceProgress = Double.isFinite(experienceProgress) ? Math.clamp(experienceProgress, 0, 1) : 0;
        double armor = player == null ? 20 : player.getAttributeValue(Attributes.ARMOR);
        double toughness = player == null ? 8 : player.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
        double attack = player == null ? 7 : player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        if (healthLine == null || Double.compare(health, cachedHealth) != 0 || Double.compare(max, cachedMaxHealth) != 0
                || Double.compare(absorption, cachedAbsorption) != 0) {
            cachedHealth = health;
            cachedMaxHealth = max;
            cachedAbsorption = absorption;
            var label = Component.translatable("aatu_ui.stat.health", number(health), number(max));
            if (absorption > 0) {
                label.append(Component.translatable("aatu_ui.stat.absorption", absorptionNumber(absorption))
                        .withStyle(style -> style.withColor(HudColors.ABSORPTION & 0xFFFFFF)));
            }
            healthLine = label;
        }
        if (hunger != cachedHunger) {
            cachedHunger = hunger;
            hungerLine = Component.translatable("aatu_ui.stat.hunger", Integer.toString(hunger));
        }
        if (experienceLine == null || experienceLevel != cachedExperienceLevel
                || Double.compare(experienceProgress, cachedExperienceProgress) != 0) {
            cachedExperienceLevel = experienceLevel;
            cachedExperienceProgress = experienceProgress;
            experienceLine = Component.translatable("aatu_ui.stat.experience",
                    Integer.toString(experienceLevel), number(experienceProgress * 100));
        }
        if (armorLine == null || Double.compare(armor, cachedArmor) != 0) {
            cachedArmor = armor;
            armorLine = Component.translatable("aatu_ui.stat.armor", number(armor));
        }
        if (toughnessLine == null || Double.compare(toughness, cachedToughness) != 0) {
            cachedToughness = toughness;
            toughnessLine = Component.translatable("aatu_ui.stat.toughness", number(toughness));
        }
        if (attackLine == null || Double.compare(attack, cachedAttack) != 0) {
            cachedAttack = attack;
            attackLine = Component.translatable("aatu_ui.stat.attack", number(attack));
        }
        if (needDefense) {
            DefenseEstimate.Result defense = DefenseEstimate.current(player);
            if (!defense.equals(cachedDefense) || defenseLine == null) {
                cachedDefense = defense;
                defenseLine = switch (defense.mode()) {
                    case COMBAT -> Component.translatable("aatu_ui.stat.defense_pool",
                            defenseNumber(defense.incoming()), defenseNumber(defense.fullPoolIncoming()));
                    case VANILLA -> Component.translatable("aatu_ui.stat.vanilla_defense", defenseNumber(defense.incoming()));
                    case WAITING -> Component.translatable("aatu_ui.stat.defense_waiting");
                    case OUTDATED -> Component.translatable("aatu_ui.stat.defense_outdated");
                    case UNAVAILABLE -> Component.translatable("aatu_ui.stat.defense_unavailable");
                };
                poolLine = switch (defense.mode()) {
                    case COMBAT -> Component.translatable("aatu_ui.stat.pool", number(defense.pool()), number(defense.capacity()));
                    case WAITING -> Component.translatable("aatu_ui.stat.pool_waiting");
                    case OUTDATED -> Component.translatable("aatu_ui.stat.pool_outdated");
                    default -> Component.translatable("aatu_ui.stat.pool_unavailable");
                };
                boolean bypassAvailable = defense.mode() == DefenseEstimate.Mode.COMBAT
                        && Double.isFinite(defense.bypassAbsorption()) && Double.isFinite(defense.bypassAbsorptionMax());
                bypassLine = bypassAvailable ? Component.translatable("aatu_ui.stat.bypass_protection",
                        absorptionNumber(defense.bypassAbsorption()), absorptionNumber(defense.bypassAbsorptionMax()))
                        : Component.translatable("aatu_ui.stat.bypass_unavailable");
            }
        }
    }

    private static void updatePerformance() {
        long now = System.nanoTime();
        if (cachedPerformance != null && now < nextPerformanceUpdate) return;
        nextPerformanceUpdate = now + 200_000_000L;
        ServerPerformance.Reading reading = ServerPerformance.current();
        if (reading.equals(cachedPerformance)) return;
        cachedPerformance = reading;
        tpsLine = switch (reading.state()) {
            case READY -> Component.translatable("aatu_ui.stat.tps", number(reading.tps()));
            case WAITING -> Component.translatable("aatu_ui.stat.tps_waiting");
            case PAUSED -> Component.translatable("aatu_ui.stat.tps_paused");
            case UNAVAILABLE -> Component.translatable("aatu_ui.stat.tps_unavailable");
        };
    }

    private static void drawPlayerIdentity(GuiGraphics graphics, int x, int y, Player player, int panelWidth) {
        Minecraft minecraft = Minecraft.getInstance();
        ResourceLocation skin = player instanceof AbstractClientPlayer clientPlayer
                ? clientPlayer.getSkin().texture() : DefaultPlayerSkin.getDefaultTexture();
        String name = player == null ? minecraft.getUser().getName() : player.getGameProfile().getName();
        drawPlayerFace(graphics, skin, x + 4, y);
        graphics.drawString(minecraft.font, minecraft.font.plainSubstrByWidth(name, panelWidth - 28),
                x + 24, y + 4, 0xFFFFFFFF, true);
    }

    private static void drawPlayerFace(GuiGraphics graphics, ResourceLocation skin, int x, int y) {
        graphics.flush();
        float[] previousColor = RenderSystem.getShaderColor().clone();
        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        try {
            PlayerFaceRenderer.draw(graphics, skin, x, y, FACE_SIZE, false, false);
            graphics.pose().pushPose();
            try {
                // The outer skin must be in front of the face even when depth testing is enabled.
                graphics.pose().translate(0, 0, 1);
                graphics.blit(skin, x, y, FACE_SIZE, FACE_SIZE,
                        (float) PlayerFaceRenderer.SKIN_HAT_U, (float) PlayerFaceRenderer.SKIN_HAT_V,
                        PlayerFaceRenderer.SKIN_HAT_WIDTH, PlayerFaceRenderer.SKIN_HAT_HEIGHT,
                        PlayerFaceRenderer.SKIN_TEX_WIDTH, PlayerFaceRenderer.SKIN_TEX_HEIGHT);
            } finally {
                graphics.pose().popPose();
            }
        } finally {
            RenderSystem.disableBlend();
            RenderSystem.setShaderColor(previousColor[0], previousColor[1], previousColor[2], previousColor[3]);
        }
    }

    private static String number(double value) {
        return Double.isFinite(value) ? NUMBER.format(value) : "0";
    }

    private static String defenseNumber(double value) {
        return Double.isInfinite(value) ? "∞" : number(value);
    }

    private static String absorptionNumber(double value) {
        return value > 0 && value < 0.1 ? "<0.1" : number(value);
    }
}
