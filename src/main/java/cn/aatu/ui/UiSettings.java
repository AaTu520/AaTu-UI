package cn.aatu.ui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;

public final class UiSettings {
    public static final float MIN_FONT_SIZE = 1.0F;
    public static final float MAX_FONT_SIZE = 10.0F;
    public static final float DEFAULT_FONT_SIZE = 5.0F;
    public static final float MIN_HUD_SCALE = 0.25F;
    public static final float MAX_HUD_SCALE = 4.0F;
    public static final float DEFAULT_HUD_SCALE = 1.0F;
    public static final float HUD_SCALE_STEP = 0.25F;
    public static final int MIN_RECAP_SECONDS = 1;
    public static final int MAX_RECAP_SECONDS = 30;
    public static final int DEFAULT_RECAP_SECONDS = 8;
    public static final int MAX_STATS_COLUMNS = 5;
    public static final int MIN_STAT_BAR_WIDTH = 80;
    public static final int MAX_STAT_BAR_WIDTH = 200;
    public static final int DEFAULT_STAT_BAR_WIDTH = 112;
    public static final int STAT_BAR_WIDTH_STEP = 8;
    public static final int MIN_STAT_BAR_HEIGHT = 12;
    public static final int MAX_STAT_BAR_HEIGHT = 32;
    public static final int DEFAULT_STAT_BAR_HEIGHT = 14;
    public static final int STAT_BAR_HEIGHT_STEP = 2;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "aatu-ui.json";

    private static UiSettings instance;

    public volatile String selectedFont = "";
    public volatile int selectedFontFace = 0;
    public volatile boolean systemFontReference = false;
    public volatile String systemFontName = "";
    public volatile String systemFontFile = "";
    public volatile boolean copySystemFonts = true;
    public volatile float fontSize = DEFAULT_FONT_SIZE;
    public boolean smoothFonts = true;
    public boolean autoDamageRecap = true;
    public String damageRecapCorner = "top_right";
    public int damageRecapSeconds = DEFAULT_RECAP_SECONDS;
    public boolean championAffixes = true;
    public boolean championGroupWithBoss = true;
    public String championAffixAnchor = "free";
    public String championAffixSide = "below";
    public boolean effectLevels = true;
    public boolean effectDuration = true;
    public boolean effectVertical = false;
    public boolean woundEdgeBlur = true;
    public boolean hotbarVertical = false;
    public boolean spellVertical = false;
    public boolean showExperienceBar = true;
    public boolean showExperienceLevel = true;
    public boolean experienceVertical = false;
    public boolean showStatHealth = true;
    public boolean showStatHunger = true;
    public boolean showStatExperience = true;
    public boolean showStatArmor = true;
    public boolean showStatToughness = true;
    public boolean showStatAttack = true;
    public boolean showStatDefense = true;
    public boolean showStatPool = true;
    public boolean showStatBypassProtection = true;
    public boolean showStatTps = true;
    public int statsColumns = 1;
    public int statBarWidth = DEFAULT_STAT_BAR_WIDTH;
    public int statBarHeight = DEFAULT_STAT_BAR_HEIGHT;
    public boolean healthStatusColors = true;
    public boolean hungerStatusColors = true;
    public Map<String, String> healthEffectColors = HudColors.defaults(true);
    public Map<String, String> hungerEffectColors = HudColors.defaults(false);
    public boolean showPlayerIdentity = true;
    public boolean bossHorizontal = false;
    public boolean remnantRageRight = false;
    public int bossGap = 2;
    public int bossLimit = 0;
    private Map<String, Placement> placements = new LinkedHashMap<>();
    private transient boolean invalidFile;

    public static synchronized UiSettings get() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    private static UiSettings load() {
        Path file = FMLPaths.CONFIGDIR.get().resolve(FILE_NAME);
        if (Files.isRegularFile(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                UiSettings settings = GSON.fromJson(reader, UiSettings.class);
                if (settings != null) {
                    if (settings.placements == null) settings.placements = new LinkedHashMap<>();
                    if (settings.selectedFont == null) settings.selectedFont = "";
                    if (settings.systemFontName == null) settings.systemFontName = "";
                    if (settings.systemFontFile == null) settings.systemFontFile = "";
                    settings.selectedFontFace = Math.max(0, settings.selectedFontFace);
                    settings.fontSize = validFontSize(settings.fontSize);
                    settings.damageRecapCorner = settings.recapCorner().id;
                    settings.damageRecapSeconds = validRecapSeconds(settings.damageRecapSeconds);
                    settings.statsColumns = validStatsColumns(settings.statsColumns);
                    settings.statBarWidth = validStatBarWidth(settings.statBarWidth);
                    settings.statBarHeight = validStatBarHeight(settings.statBarHeight);
                    if (settings.healthEffectColors == null) settings.healthEffectColors = HudColors.defaults(true);
                    if (settings.hungerEffectColors == null) settings.hungerEffectColors = HudColors.defaults(false);
                    return settings;
                }
            } catch (IOException | RuntimeException exception) {
                LOGGER.warn("Unable to read AaTu UI settings: {}", file, exception);
            }
            UiSettings fallback = new UiSettings();
            fallback.invalidFile = true;
            return fallback;
        }
        return new UiSettings();
    }

    public Placement placement(String id) {
        return placements.computeIfAbsent(id, key -> {
            Placement placement = new Placement();
            if ("hotbar".equals(key) || "armor".equals(key) || "experience".equals(key)) placement.style = HudStyle.ORIGINAL.id;
            if ("champion_affixes".equals(key)) placement.visible = championAffixes;
            return placement;
        });
    }

    public void prepareChampionAffixes(int width, int height) {
        if (placements.containsKey("champion_affixes")) return;
        Placement champion = placements.get("champion");
        if (champion == null || !champion.hasPosition()) return;
        Placement affixes = placement("champion_affixes");
        affixes.setPosition(HudPart.CHAMPION.x(width, height),
                HudPart.CHAMPION.y(width, height) + HudPart.CHAMPION.pixelHeight(), width, height);
    }

    public static float validFontSize(float size) {
        return Float.isFinite(size) ? Math.max(MIN_FONT_SIZE, Math.min(MAX_FONT_SIZE, size)) : DEFAULT_FONT_SIZE;
    }

    public DamageRecapCorner recapCorner() {
        return DamageRecapCorner.from(damageRecapCorner);
    }

    public static int validRecapSeconds(int seconds) {
        return Math.clamp(seconds, MIN_RECAP_SECONDS, MAX_RECAP_SECONDS);
    }

    public static int validStatsColumns(int columns) {
        return Math.clamp(columns, 1, MAX_STATS_COLUMNS);
    }

    public static int validStatBarWidth(int width) {
        int bounded = Math.clamp(width, MIN_STAT_BAR_WIDTH, MAX_STAT_BAR_WIDTH);
        return MIN_STAT_BAR_WIDTH + Math.round((bounded - MIN_STAT_BAR_WIDTH) / (float) STAT_BAR_WIDTH_STEP) * STAT_BAR_WIDTH_STEP;
    }

    public static int validStatBarHeight(int height) {
        int bounded = Math.clamp(height, MIN_STAT_BAR_HEIGHT, MAX_STAT_BAR_HEIGHT);
        return MIN_STAT_BAR_HEIGHT + Math.round((bounded - MIN_STAT_BAR_HEIGHT) / (float) STAT_BAR_HEIGHT_STEP) * STAT_BAR_HEIGHT_STEP;
    }

    public synchronized boolean save() {
        Placement affixes = placements.get("champion_affixes");
        if (affixes != null) championAffixes = affixes.visible;
        Path file = FMLPaths.CONFIGDIR.get().resolve(FILE_NAME);
        Path temporary = null;
        try {
            Files.createDirectories(file.getParent());
            if (invalidFile && Files.exists(file)) {
                Path backup = file.resolveSibling(FILE_NAME + ".invalid");
                for (int suffix = 1; Files.exists(backup); suffix++) {
                    backup = file.resolveSibling(FILE_NAME + ".invalid-" + suffix);
                }
                Files.copy(file, backup);
                invalidFile = false;
            }
            temporary = Files.createTempFile(file.getParent(), "aatu-ui-", ".tmp");
            Files.writeString(temporary, GSON.toJson(this), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException exception) {
            LOGGER.warn("Unable to save AaTu UI settings: {}", file, exception);
            return false;
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException exception) {
                    LOGGER.debug("Unable to remove temporary settings file: {}", temporary, exception);
                }
            }
        }
    }

    public static final class Placement {
        public boolean visible = true;
        public String style = "modern";
        public double x = -1;
        public double y = -1;
        public float scale = DEFAULT_HUD_SCALE;

        public float scale() {
            return Float.isFinite(scale) && scale > 0
                    ? Math.clamp(scale, MIN_HUD_SCALE, MAX_HUD_SCALE) : DEFAULT_HUD_SCALE;
        }

        public void changeScale(float amount) {
            scale = Math.clamp(scale() + amount, MIN_HUD_SCALE, MAX_HUD_SCALE);
        }

        public boolean hasPosition() {
            return Double.isFinite(x) && Double.isFinite(y) && x >= 0 && x <= 1 && y >= 0 && y <= 1;
        }

        public void setPosition(int x, int y, int width, int height) {
            this.x = Math.max(0, Math.min(1, (double) x / Math.max(1, width)));
            this.y = Math.max(0, Math.min(1, (double) y / Math.max(1, height)));
        }

        public void reset() {
            x = -1;
            y = -1;
        }

        public HudStyle style() {
            return HudStyle.from(style);
        }

        public void nextStyle() {
            style = style().next().id;
        }
    }
}
