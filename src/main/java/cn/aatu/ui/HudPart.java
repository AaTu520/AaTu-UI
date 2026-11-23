package cn.aatu.ui;

import net.minecraft.util.Mth;
import net.neoforged.fml.ModList;

public enum HudPart {
    HEALTH("health", "aatu_ui.health", 91, 10),
    HUNGER("hunger", "aatu_ui.hunger", 91, 10),
    ARMOR("armor", "aatu_ui.vanilla_armor", 81, 9),
    HOTBAR("hotbar", "aatu_ui.hotbar", 182, 22),
    EXPERIENCE("experience", "aatu_ui.experience", 182, 12),
    BOSS("boss", "aatu_ui.boss", 182, 17),
    CHAMPION("champion", "aatu_ui.champion", 182, 26),
    CHAMPION_AFFIXES("champion_affixes", "aatu_ui.affixes", 182, 10),
    SPELLS("spells", "aatu_ui.spells", 100, 22),
    EFFECTS("effects", "aatu_ui.effects", 125, 55),
    STATS("stats", "aatu_ui.stats", 140, 44);

    public final String id;
    public final String label;
    public final int width;
    public final int height;

    HudPart(String id, String label, int width, int height) {
        this.id = id;
        this.label = label;
        this.width = width;
        this.height = height;
    }

    public UiSettings.Placement placement() {
        return UiSettings.get().placement(id);
    }

    public boolean available() {
        return (this != CHAMPION && this != CHAMPION_AFFIXES) || ModList.get().isLoaded("champions");
    }

    public int pixelWidth() {
        return scaled(logicalWidth());
    }

    public int logicalWidth() {
        return switch (this) {
            case SPELLS -> HudCompatibility.spellWidth();
            case EFFECTS -> EffectsHud.pixelWidth();
            case HOTBAR -> HotbarHud.pixelWidth();
            case EXPERIENCE -> ExperienceHud.logicalWidth();
            case STATS -> StatsHud.pixelWidth();
            default -> width;
        };
    }

    public int pixelHeight() {
        return scaled(logicalHeight());
    }

    public int logicalHeight() {
        return switch (this) {
            case EFFECTS -> EffectsHud.pixelHeight();
            case STATS -> StatsHud.pixelHeight();
            case HOTBAR -> HotbarHud.pixelHeight();
            case EXPERIENCE -> ExperienceHud.logicalHeight();
            case SPELLS -> HudCompatibility.spellHeight();
            default -> height;
        };
    }

    public float scale() {
        return placement().scale();
    }

    public int scaled(int pixels) {
        return Mth.ceil(pixels * scale());
    }

    public int x(int screenWidth, int screenHeight) {
        UiSettings.Placement placement = placement();
        int coordinate = placement.hasPosition() ? (int) Math.round(placement.x * screenWidth) : defaultX(screenWidth, screenHeight);
        return Mth.clamp(coordinate, 0, Math.max(0, screenWidth - pixelWidth()));
    }

    public int y(int screenWidth, int screenHeight) {
        UiSettings.Placement placement = placement();
        int coordinate = placement.hasPosition() ? (int) Math.round(placement.y * screenHeight) : defaultY(screenWidth, screenHeight);
        return Mth.clamp(coordinate, 0, Math.max(0, screenHeight - pixelHeight()));
    }

    private int defaultX(int screenWidth, int screenHeight) {
        return switch (this) {
            case HEALTH, ARMOR -> screenWidth / 2 - scaled(91);
            case HUNGER -> screenWidth / 2 + (placement().style() == HudStyle.ORIGINAL ? 10 : 0);
            case HOTBAR -> screenWidth / 2 - scaled(UiSettings.get().hotbarVertical ? 11 : 91);
            case BOSS, EXPERIENCE -> screenWidth / 2 - pixelWidth() / 2;
            case CHAMPION, CHAMPION_AFFIXES -> screenWidth / 2 - pixelWidth() / 2 + HudCompatibility.championOffsetX();
            case SPELLS -> HudCompatibility.spellX(screenWidth) + (logicalWidth() - pixelWidth()) / 2;
            case EFFECTS -> screenWidth - pixelWidth();
            case STATS -> 6;
        };
    }

    private int defaultY(int screenWidth, int screenHeight) {
        return switch (this) {
            case HEALTH, HUNGER -> screenHeight - 29 - pixelHeight();
            case ARMOR -> OriginalHudLayers.armorY(screenHeight);
            case HOTBAR -> screenHeight - pixelHeight();
            case EXPERIENCE -> screenHeight - 24 - pixelHeight();
            case BOSS -> 3;
            case CHAMPION -> 1 + HudCompatibility.championOffsetY();
            case CHAMPION_AFFIXES -> 1 + HudPart.CHAMPION.pixelHeight() + HudCompatibility.championOffsetY();
            case SPELLS -> HudCompatibility.spellY(screenHeight) + (logicalHeight() - pixelHeight()) / 2;
            case EFFECTS -> 1;
            case STATS -> 40;
        };
    }
}
