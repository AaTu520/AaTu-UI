package cn.aatu.ui;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;

public final class BossLayout {
    private static final int MAX_BARS = 128;
    private static final int STOP_INCREMENT = 1_000_000_000;
    private static int index;
    private static int deltaX;
    private static int deltaY;
    private static int sourceX;
    private static int sourceY;
    private static Position currentPosition;
    private static boolean frameActive;
    private static boolean insideEvent;
    private static boolean currentVisible;
    private static boolean currentAuxiliary;
    private static int currentSlot;
    private static int displayedCount;
    private static BossEvent currentBoss;
    private static final Map<UUID, LerpingBossEvent> AUXILIARY_BARS = new HashMap<>();

    private BossLayout() {}

    public static void beginFrame() {
        frameActive = true;
        insideEvent = false;
        index = 0;
        currentPosition = null;
        displayedCount = 0;
        AUXILIARY_BARS.clear();
        if (Minecraft.getInstance().gui.getBossOverlay() instanceof BossLayoutHook hook) {
            UUID owner = null;
            int expectedType = -1;
            int configuredLimit = Mth.clamp(UiSettings.get().bossLimit, 0, MAX_BARS);
            int limit = configuredLimit == 0 ? MAX_BARS : configuredLimit;
            for (LerpingBossEvent boss : hook.aatu$bossEvents().values()) {
                int type = HudCompatibility.cataclysmRenderType(boss);
                if (!CataclysmBossMeters.isRage(boss) && displayedCount < limit) displayedCount++;
                if (owner != null && type == expectedType) AUXILIARY_BARS.put(owner, boss);
                expectedType = CataclysmBossMeters.auxiliaryType(type);
                owner = expectedType >= 0 ? boss.getId() : null;
                if (displayedCount >= limit && owner == null) break;
            }
        }
    }

    public static void endFrame() {
        frameActive = false;
        insideEvent = false;
        currentBoss = null;
        currentPosition = null;
        currentAuxiliary = false;
        displayedCount = 0;
        AUXILIARY_BARS.clear();
    }

    public static boolean prepare(GuiGraphics graphics, BossEvent boss, int originalX, int originalY) {
        if (!frameActive) return false;
        currentBoss = boss;
        currentAuxiliary = CataclysmBossMeters.isRage(boss);
        int slot = index;
        if (!currentAuxiliary) index++;
        currentSlot = slot;
        int limit = Mth.clamp(UiSettings.get().bossLimit, 0, MAX_BARS);
        currentVisible = !currentAuxiliary && slot < (limit == 0 ? MAX_BARS : limit);
        int baseX = HudPart.BOSS.x(graphics.guiWidth(), graphics.guiHeight());
        int baseY = HudPart.BOSS.y(graphics.guiWidth(), graphics.guiHeight());
        Position position = position(slot, baseX, baseY, graphics.guiWidth(), graphics.guiHeight());
        sourceX = originalX;
        sourceY = originalY - 9;
        currentPosition = position;
        deltaX = position.x() - originalX;
        deltaY = position.y() + 9 - originalY;
        insideEvent = true;
        return true;
    }

    public static void leaveEvent() {
        insideEvent = false;
    }

    public static boolean insideEvent() {
        return insideEvent;
    }

    public static boolean frameActive() {
        return frameActive;
    }

    public static boolean currentVisible() {
        return currentVisible;
    }

    public static boolean currentAuxiliary() {
        return frameActive && currentAuxiliary;
    }

    public static int currentSlot() {
        return currentSlot;
    }

    public static int displayedCount() {
        return displayedCount;
    }

    public static LerpingBossEvent currentRage() {
        return frameActive && currentBoss != null ? AUXILIARY_BARS.get(currentBoss.getId()) : null;
    }

    public static BossEvent currentBoss() {
        return frameActive ? currentBoss : null;
    }

    public static int deltaX() {
        return frameActive ? deltaX : 0;
    }

    public static int deltaY() {
        return frameActive ? deltaY : 0;
    }

    public static void applyTransform(GuiGraphics graphics) {
        if (frameActive && currentPosition != null) {
            HudTransform.apply(graphics, HudPart.BOSS, currentPosition.x(), currentPosition.y(), sourceX, sourceY);
        }
    }

    public static void stopAfterCurrent(CustomizeGuiOverlayEvent.BossEventProgress event) {
        event.setCanceled(true);
        event.setIncrement(STOP_INCREMENT);
    }

    public static void hideAuxiliary(CustomizeGuiOverlayEvent.BossEventProgress event) {
        event.setCanceled(true);
        event.setIncrement(0);
    }

    public static Position position(int slot, int baseX, int baseY, int width, int height) {
        int gap = scaledGap();
        int columnWidth = columnStep();
        int rowHeight = rowStep();
        if (UiSettings.get().bossHorizontal) {
            int columns = Math.max(1, (width - baseX + gap) / columnWidth);
            return new Position(baseX + slot % columns * columnWidth, baseY + slot / columns * rowHeight);
        }
        int rows = Math.max(1, (height - baseY + gap) / rowHeight);
        return new Position(baseX + slot / rows * columnWidth, baseY + slot % rows * rowHeight);
    }

    public static int scaledGap() {
        return HudPart.BOSS.scaled(Mth.clamp(UiSettings.get().bossGap, 0, 40));
    }

    public static int columnStep() {
        return HudPart.BOSS.pixelWidth() + scaledGap();
    }

    public static int rowStep() {
        return HudPart.BOSS.pixelHeight() + scaledGap();
    }

    public static ChampionAffixAnchor.Bounds previewBounds(int baseX, int baseY, int width, int height) {
        int right = baseX;
        int bottom = baseY;
        int count = Math.min(3, UiSettings.get().bossLimit == 0 ? 3 : Math.max(1, UiSettings.get().bossLimit));
        for (int slot = 0; slot < count; slot++) {
            Position position = position(slot, baseX, baseY, width, height);
            right = Math.max(right, position.x() + HudPart.BOSS.pixelWidth());
            bottom = Math.max(bottom, position.y() + HudPart.BOSS.pixelHeight());
        }
        return new ChampionAffixAnchor.Bounds(baseX, baseY, right - baseX, bottom - baseY);
    }

    public static boolean previewContains(double mouseX, double mouseY, int baseX, int baseY, int width, int height) {
        int count = Math.min(3, UiSettings.get().bossLimit == 0 ? 3 : Math.max(1, UiSettings.get().bossLimit));
        for (int slot = 0; slot < count; slot++) {
            Position position = position(slot, baseX, baseY, width, height);
            if (mouseX >= position.x && mouseX < position.x + HudPart.BOSS.pixelWidth()
                    && mouseY >= position.y && mouseY < position.y + HudPart.BOSS.pixelHeight()) return true;
        }
        return false;
    }

    public record Position(int x, int y) {}
}
