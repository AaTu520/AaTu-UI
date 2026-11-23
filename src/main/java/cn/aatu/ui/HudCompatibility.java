package cn.aatu.ui;

import com.mojang.logging.LogUtils;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.BossEvent;
import net.minecraft.world.phys.Vec2;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;

public final class HudCompatibility {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static boolean championLookupAttempted;
    private static Field championX;
    private static Field championY;
    private static Field championRendering;
    private static Field championStartX;
    private static Field championStartY;
    private static boolean spellLookupAttempted;
    private static Field spellRect;
    private static Method spellTopLeftMethod;
    private static Method spellBottomRightMethod;
    private static int spellCount = 5;
    private static boolean cataclysmLookupAttempted;
    private static Field cataclysmBossBars;
    private static Method cataclysmRemainingLives;
    private static Method cataclysmRenderType;

    private HudCompatibility() {}

    public static Component bossTitle(BossEvent boss, Component title) {
        Object data = cataclysmBossData(boss);
        if (data == null || cataclysmRemainingLives == null) return title;
        try {
            int lives = (int) cataclysmRemainingLives.invoke(data);
            return lives > 0 ? title.copy().append(Component.literal(" ×" + lives).withStyle(ChatFormatting.WHITE)) : title;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            disableCataclysmLookup(exception);
            return title;
        }
    }

    public static int cataclysmRenderType(BossEvent boss) {
        Object data = cataclysmBossData(boss);
        if (data == null || cataclysmRenderType == null) return -1;
        try {
            return (int) cataclysmRenderType.invoke(data);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            disableCataclysmLookup(exception);
            return -1;
        }
    }

    private static Object cataclysmBossData(BossEvent boss) {
        if (!cataclysmLookupAttempted) {
            cataclysmLookupAttempted = true;
            if (ModList.get().isLoaded("cataclysm")) {
                try {
                    cataclysmBossBars = Class.forName("com.github.L_Ender.cataclysm.ClientProxy").getField("bossBarRenderTypes");
                    Class<?> dataClass = Class.forName("com.github.L_Ender.cataclysm.ClientProxy$BossBarData");
                    cataclysmRemainingLives = dataClass.getMethod("remainLife");
                    try {
                        cataclysmRenderType = dataClass.getMethod("renderType");
                    } catch (NoSuchMethodException exception) {
                        LOGGER.warn("Cataclysm boss bar type is unavailable", exception);
                    }
                } catch (ReflectiveOperationException | LinkageError exception) {
                    disableCataclysmLookup(exception);
                }
            }
        }
        if (cataclysmBossBars == null) return null;
        try {
            Object entries = cataclysmBossBars.get(null);
            return entries instanceof Map<?, ?> bars ? bars.get(boss.getId()) : null;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            disableCataclysmLookup(exception);
            return null;
        }
    }

    private static void disableCataclysmLookup(Throwable exception) {
        LOGGER.warn("Cataclysm boss bar data is unavailable", exception);
        cataclysmBossBars = null;
        cataclysmRemainingLives = null;
        cataclysmRenderType = null;
    }

    private static void findChampionFields() {
        if (championLookupAttempted) return;
        championLookupAttempted = true;
        if (!ModList.get().isLoaded("champions")) return;
        try {
            Class<?> config = Class.forName("top.theillusivec4.champions.client.config.ClientChampionsConfig");
            Class<?> overlay = Class.forName("top.theillusivec4.champions.client.ChampionsOverlay");
            championX = config.getField("hudXOffset");
            championY = config.getField("hudYOffset");
            championRendering = overlay.getField("isRendering");
            championStartX = overlay.getField("startX");
            championStartY = overlay.getField("startY");
        } catch (ReflectiveOperationException exception) {
            LOGGER.warn("Champion HUD integration is unavailable", exception);
        }
    }

    public static int championOffsetX() {
        findChampionFields();
        return readInt(championX, 0);
    }

    public static int championOffsetY() {
        findChampionFields();
        return readInt(championY, 0);
    }

    private static int readInt(Field field, int fallback) {
        try {
            return field == null ? fallback : field.getInt(null);
        } catch (IllegalAccessException exception) {
            return fallback;
        }
    }

    public static void championHidden() {
        findChampionFields();
        try {
            if (championRendering != null) championRendering.setBoolean(null, false);
        } catch (IllegalAccessException exception) {
            LOGGER.debug("Unable to reset champion HUD state", exception);
        }
    }

    public static void championMoved(int originalX, int originalY, float x, float y, float scale) {
        findChampionFields();
        try {
            if (championRendering != null && championRendering.getBoolean(null)) {
                if (championStartX != null) championStartX.setInt(null, Math.round(x + (championStartX.getInt(null) - originalX) * scale));
                if (championStartY != null) championStartY.setInt(null, Math.round(y + (championStartY.getInt(null) - originalY) * scale));
            }
        } catch (IllegalAccessException exception) {
            LOGGER.debug("Unable to update champion HUD position", exception);
        }
    }

    private static Vec2 spellTopLeft() {
        if (!spellLookupAttempted) {
            spellLookupAttempted = true;
            if (ModList.get().isLoaded("spell_engine")) {
                try {
                    Field field = Class.forName("net.spell_engine.client.gui.HudRenderHelper$SpellHotBarWidget")
                            .getField("lastRendered");
                    spellTopLeftMethod = field.getType().getMethod("topLeft");
                    spellBottomRightMethod = field.getType().getMethod("bottomRight");
                    spellRect = field;
                } catch (ReflectiveOperationException exception) {
                    LOGGER.warn("Spell Engine HUD integration is unavailable", exception);
                }
            }
        }
        try {
            if (spellRect == null) return null;
            Object rect = spellRect.get(null);
            return rect == null ? null : (Vec2) spellTopLeftMethod.invoke(rect);
        } catch (ReflectiveOperationException | ClassCastException exception) {
            return null;
        }
    }

    public static int spellX(int width) {
        Vec2 center = currentSpellCenter();
        return center == null ? width / 2 - spellWidth() / 2 : Math.round(center.x - spellWidth() / 2.0F);
    }

    public static Vec2 currentSpellCenter() {
        Vec2 topLeft = spellTopLeft();
        if (topLeft == null || spellRect == null) return null;
        try {
            Object rect = spellRect.get(null);
            if (rect == null) return null;
            Vec2 bottomRight = (Vec2) spellBottomRightMethod.invoke(rect);
            return new Vec2((topLeft.x + bottomRight.x) / 2.0F, (topLeft.y + bottomRight.y) / 2.0F);
        } catch (ReflectiveOperationException | ClassCastException exception) {
            return null;
        }
    }

    public static void recordSpellCount(int count) {
        if (count > 0) spellCount = count;
    }

    public static int spellCount() {
        return spellCount;
    }

    public static int spellWidth() {
        return UiSettings.get().spellVertical ? SpellLayout.SLOT_WIDTH
                : Math.max(SpellLayout.SLOT_WIDTH, spellCount * SpellLayout.SLOT_STEP + 2);
    }

    public static int spellHeight() {
        return UiSettings.get().spellVertical ? Math.max(SpellLayout.VERTICAL_ROW_HEIGHT, spellCount * SpellLayout.VERTICAL_ROW_HEIGHT)
                : SpellLayout.SLOT_HEIGHT;
    }

    public static int spellY(int height) {
        Vec2 center = currentSpellCenter();
        return center == null ? height - 78 : Math.round(center.y - spellHeight() / 2.0F);
    }
}
