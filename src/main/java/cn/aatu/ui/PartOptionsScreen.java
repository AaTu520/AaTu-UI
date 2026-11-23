package cn.aatu.ui;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModList;

public final class PartOptionsScreen extends CrispScreen {
    private final Screen previous;
    private final HudPart part;
    private int firstY;
    private Button woundPreviewButton;

    public PartOptionsScreen(Screen previous, HudPart part) {
        super(Component.translatable(part.label));
        this.previous = previous;
        this.part = part;
    }

    @Override
    protected void init() {
        UiSettings settings = UiSettings.get();
        int left = width / 2 - 100;
        firstY = Math.max(28, height / 2 - 45);
        if (part == HudPart.HEALTH || part == HudPart.HUNGER) {
            boolean health = part == HudPart.HEALTH;
            addToggle(left, firstY, "aatu_ui.status_colors", () -> health ? settings.healthStatusColors : settings.hungerStatusColors,
                    value -> { if (health) settings.healthStatusColors = value; else settings.hungerStatusColors = value; });
            addRenderableWidget(Button.builder(Component.translatable("aatu_ui.effect_colors"), button ->
                    minecraft.setScreen(new EffectColorsScreen(this, health))).bounds(left, firstY + 22, 200, 20).build());
        } else if (part == HudPart.EXPERIENCE) {
            addDirection(left, firstY, () -> settings.experienceVertical, value -> settings.experienceVertical = value);
            addToggle(left, firstY + 22, "aatu_ui.experience_bar", () -> settings.showExperienceBar, value -> settings.showExperienceBar = value);
            addToggle(left, firstY + 44, "aatu_ui.experience_level", () -> settings.showExperienceLevel, value -> settings.showExperienceLevel = value);
        } else if (part == HudPart.BOSS) {
            addRenderableWidget(Button.builder(bossDirection(settings), button -> {
                settings.bossHorizontal = !settings.bossHorizontal;
                UiSettings.get().save();
                button.setMessage(bossDirection(settings));
            }).bounds(left, firstY, 200, 20).build());
            addNumberControls(left, firstY + 22, () -> settings.bossGap, value -> settings.bossGap = value, 40);
            addNumberControls(left, firstY + 44, () -> settings.bossLimit, value -> settings.bossLimit = value, 128);
            if (ModList.get().isLoaded("cataclysm")) {
                addRenderableWidget(Button.builder(remnantRageSide(settings), button -> {
                    settings.remnantRageRight = !settings.remnantRageRight;
                    settings.save();
                    button.setMessage(remnantRageSide(settings));
                }).bounds(left, firstY + 66, 200, 20).build());
            }
        } else if (part == HudPart.CHAMPION) {
            addToggle(left, firstY, "aatu_ui.bar", () -> HudPart.CHAMPION.placement().visible,
                    value -> HudPart.CHAMPION.placement().visible = value);
            addToggle(left, firstY + 22, "aatu_ui.affixes", () -> HudPart.CHAMPION_AFFIXES.placement().visible,
                    value -> HudPart.CHAMPION_AFFIXES.placement().visible = value);
            addGroupAnchor(left, firstY + 44);
        } else if (part == HudPart.CHAMPION_AFFIXES) {
            addRenderableWidget(Button.builder(affixAnchorText(), button -> {
                settings.championAffixAnchor = ChampionAffixAnchor.anchor().next().id;
                settings.save();
                button.setMessage(affixAnchorText());
            }).bounds(left, firstY, 200, 20).build());
            addRenderableWidget(Button.builder(affixSideText(), button -> {
                settings.championAffixSide = ChampionAffixAnchor.side().next().id;
                settings.save();
                button.setMessage(affixSideText());
            }).bounds(left, firstY + 22, 200, 20).build());
            addGroupAnchor(left, firstY + 44);
        } else if (part == HudPart.EFFECTS) {
            if (ModList.get().isLoaded("champions")) firstY = Math.max(28, Math.min(firstY, height - 145));
            addDirection(left, firstY, () -> settings.effectVertical, value -> settings.effectVertical = value);
            addToggle(left, firstY + 22, "aatu_ui.effect_level", () -> settings.effectLevels, value -> settings.effectLevels = value);
            addToggle(left, firstY + 44, "aatu_ui.effect_duration", () -> settings.effectDuration, value -> settings.effectDuration = value);
            if (ModList.get().isLoaded("champions")) {
                addToggle(left, firstY + 66, "aatu_ui.wound_edge_blur", () -> settings.woundEdgeBlur, value -> {
                    settings.woundEdgeBlur = value;
                    WoundScreenEffect.clear();
                });
                woundPreviewButton = addRenderableWidget(Button.builder(Component.translatable("aatu_ui.wound_preview"),
                        button -> minecraft.setScreen(new WoundPreviewScreen(this)))
                        .bounds(left, firstY + 88, 200, 20).build());
            }
        } else if (part == HudPart.HOTBAR) {
            addDirection(left, firstY, () -> settings.hotbarVertical, value -> settings.hotbarVertical = value);
        } else if (part == HudPart.SPELLS) {
            addDirection(left, firstY, () -> settings.spellVertical, value -> settings.spellVertical = value);
        } else if (part == HudPart.STATS) {
            addStatsOptions(settings);
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(left, height - 25, 200, 20).build());
    }

    private void addStatsOptions(UiSettings settings) {
        int columnWidth = Math.min(150, Math.max(80, (width - 16) / 2));
        int firstColumn = width / 2 - columnWidth - 2;
        int secondColumn = width / 2 + 2;
        firstY = Math.max(28, (height - 198) / 2);
        addRenderableWidget(new StatSizeSlider(firstColumn, firstY, columnWidth, "aatu_ui.stat_bar_width",
                UiSettings.MIN_STAT_BAR_WIDTH, UiSettings.MAX_STAT_BAR_WIDTH,
                () -> UiSettings.validStatBarWidth(settings.statBarWidth),
                value -> settings.statBarWidth = UiSettings.validStatBarWidth(value)));
        addRenderableWidget(new StatSizeSlider(secondColumn, firstY, columnWidth, "aatu_ui.stat_bar_height",
                UiSettings.MIN_STAT_BAR_HEIGHT, UiSettings.MAX_STAT_BAR_HEIGHT,
                () -> UiSettings.validStatBarHeight(settings.statBarHeight),
                value -> settings.statBarHeight = UiSettings.validStatBarHeight(value)));
        int y = firstY + 22;
        addToggle(firstColumn, y, columnWidth, "aatu_ui.health", () -> settings.showStatHealth, value -> settings.showStatHealth = value);
        addToggle(secondColumn, y, columnWidth, "aatu_ui.hunger", () -> settings.showStatHunger, value -> settings.showStatHunger = value);
        addToggle(firstColumn, y + 22, columnWidth, "aatu_ui.armor", () -> settings.showStatArmor, value -> settings.showStatArmor = value);
        addToggle(secondColumn, y + 22, columnWidth, "aatu_ui.toughness", () -> settings.showStatToughness, value -> settings.showStatToughness = value);
        addToggle(firstColumn, y + 44, columnWidth, "aatu_ui.attack", () -> settings.showStatAttack, value -> settings.showStatAttack = value);
        addToggle(secondColumn, y + 44, columnWidth, "aatu_ui.defense", () -> settings.showStatDefense, value -> settings.showStatDefense = value);
        addToggle(firstColumn, y + 66, columnWidth, "aatu_ui.pool", () -> settings.showStatPool, value -> settings.showStatPool = value);
        addToggle(secondColumn, y + 66, columnWidth, "aatu_ui.tps", () -> settings.showStatTps, value -> settings.showStatTps = value);
        addToggle(firstColumn, y + 88, columnWidth, "aatu_ui.player_identity", () -> settings.showPlayerIdentity, value -> settings.showPlayerIdentity = value);
        addToggle(secondColumn, y + 88, columnWidth, "aatu_ui.bypass_protection", () -> settings.showStatBypassProtection, value -> settings.showStatBypassProtection = value);
        addToggle(firstColumn, y + 110, columnWidth, "aatu_ui.experience", () -> settings.showStatExperience, value -> settings.showStatExperience = value);
        addRenderableWidget(Button.builder(statsColumnsLabel(), button -> {
            settings.statsColumns = UiSettings.validStatsColumns(settings.statsColumns) % UiSettings.MAX_STATS_COLUMNS + 1;
            settings.save();
            button.setMessage(statsColumnsLabel());
        }).bounds(secondColumn, y + 110, columnWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("aatu_ui.health_colors"), button ->
                minecraft.setScreen(new EffectColorsScreen(this, true))).bounds(firstColumn, y + 132, columnWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("aatu_ui.hunger_colors"), button ->
                minecraft.setScreen(new EffectColorsScreen(this, false))).bounds(secondColumn, y + 132, columnWidth, 20).build());
    }

    private static Component statsColumnsLabel() {
        return Component.translatable("aatu_ui.stats_columns", UiSettings.get().statsColumns);
    }

    private static final class StatSizeSlider extends AbstractSliderButton {
        private final String translationKey;
        private final int minimum;
        private final int maximum;
        private final IntSupplier getSize;
        private final IntConsumer setSize;

        StatSizeSlider(int x, int y, int width, String translationKey, int minimum, int maximum,
                       IntSupplier getSize, IntConsumer setSize) {
            super(x, y, width, 20, Component.empty(),
                    (double) (getSize.getAsInt() - minimum) / (maximum - minimum));
            this.translationKey = translationKey;
            this.minimum = minimum;
            this.maximum = maximum;
            this.getSize = getSize;
            this.setSize = setSize;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.translatable(translationKey, getSize.getAsInt()));
        }

        @Override
        protected void applyValue() {
            setSize.accept(minimum + (int) Math.round(value * (maximum - minimum)));
        }

        @Override
        public void onRelease(double mouseX, double mouseY) {
            super.onRelease(mouseX, mouseY);
            UiSettings.get().save();
        }
    }

    private void addGroupAnchor(int x, int y) {
        addRenderableWidget(Button.builder(groupAnchorText(), button -> {
            UiSettings settings = UiSettings.get();
            boolean attached = settings.championGroupWithBoss && ChampionAffixAnchor.anchor() == ChampionAffixAnchor.Anchor.BOSS;
            settings.championGroupWithBoss = !attached;
            if (!attached) settings.championAffixAnchor = "boss";
            settings.save();
            rebuildWidgets();
        }).bounds(x, y, 200, 20).build());
    }

    private static Component groupAnchorText() {
        boolean enabled = UiSettings.get().championGroupWithBoss && ChampionAffixAnchor.anchor() == ChampionAffixAnchor.Anchor.BOSS;
        return Component.translatable(enabled ? "aatu_ui.group_anchor_on" : "aatu_ui.group_anchor_off");
    }

    private static Component bossDirection(UiSettings settings) {
        return Component.translatable(settings.bossHorizontal ? "aatu_ui.boss_horizontal" : "aatu_ui.boss_vertical");
    }

    private static Component remnantRageSide(UiSettings settings) {
        return Component.translatable("aatu_ui.remnant_rage_side",
                Component.translatable(settings.remnantRageRight ? "aatu_ui.affix_side.right" : "aatu_ui.affix_side.left"));
    }

    private static Component affixAnchorText() {
        return Component.translatable("aatu_ui.affix_anchor", Component.translatable("aatu_ui.affix_anchor." + ChampionAffixAnchor.anchor().id));
    }

    private static Component affixSideText() {
        return Component.translatable("aatu_ui.affix_side", Component.translatable("aatu_ui.affix_side." + ChampionAffixAnchor.side().id));
    }

    private void addDirection(int x, int y, BooleanSupplier vertical, Consumer<Boolean> setter) {
        addRenderableWidget(Button.builder(directionText(vertical.getAsBoolean()), button -> {
            boolean next = !vertical.getAsBoolean();
            setter.accept(next);
            UiSettings.get().save();
            button.setMessage(directionText(next));
        }).bounds(x, y, 200, 20).build());
    }

    private static Component directionText(boolean vertical) {
        return Component.translatable(vertical ? "aatu_ui.boss_vertical" : "aatu_ui.boss_horizontal");
    }

    private void addNumberControls(int x, int y, IntSupplier value, IntConsumer setter, int maximum) {
        addRenderableWidget(Button.builder(Component.literal("−"), button -> {
            setter.accept(Math.max(0, value.getAsInt() - 1));
            UiSettings.get().save();
        }).bounds(x, y, 24, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+"), button -> {
            setter.accept(Math.min(maximum, value.getAsInt() + 1));
            UiSettings.get().save();
        }).bounds(x + 176, y, 24, 20).build());
    }

    private void addToggle(int x, int y, String key, BooleanSupplier value, Consumer<Boolean> setter) {
        addToggle(x, y, 200, key, value, setter);
    }

    private void addToggle(int x, int y, int buttonWidth, String key, BooleanSupplier value, Consumer<Boolean> setter) {
        addRenderableWidget(Button.builder(toggleText(key, value.getAsBoolean()), button -> {
            boolean next = !value.getAsBoolean();
            setter.accept(next);
            UiSettings.get().save();
            button.setMessage(toggleText(key, next));
        }).bounds(x, y, buttonWidth, 20).build());
    }

    private static Component toggleText(String key, boolean enabled) {
        return Component.translatable("aatu_ui.toggle", Component.translatable(key),
                Component.translatable(enabled ? "aatu_ui.enabled" : "aatu_ui.disabled"));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (woundPreviewButton != null) {
            woundPreviewButton.active = minecraft.level != null && UiSettings.get().woundEdgeBlur;
        }
        drawBackdrop(graphics, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 10, 0xFFFFFFFF);
        if (part == HudPart.BOSS) {
            UiSettings settings = UiSettings.get();
            graphics.drawCenteredString(font, Component.translatable("aatu_ui.boss_gap", settings.bossGap), width / 2, firstY + 28, 0xFFFFFFFF);
            Object limit = settings.bossLimit == 0 ? Component.translatable("aatu_ui.boss_all") : settings.bossLimit;
            graphics.drawCenteredString(font, Component.translatable("aatu_ui.boss_limit", limit), width / 2, firstY + 50, 0xFFFFFFFF);
        }
        drawWidgets(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(previous);
    }

    @Override
    public void removed() {
        if (part == HudPart.STATS) UiSettings.get().save();
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
