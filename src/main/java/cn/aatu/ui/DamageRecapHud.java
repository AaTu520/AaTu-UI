package cn.aatu.ui;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.ScreenEvent;

public final class DamageRecapHud {
    private static final int MAX_VISIBLE_HITS = 3;
    private static final int MAX_WIDTH = 252;
    private static final int MARGIN = 8;
    private static final int PADDING = 5;
    private static final int ROW_GAP = 4;
    private static final int TEXT_COLOR = 0xFFF1F4F8;
    private static final int MUTED_COLOR = 0xFFADBACB;
    private static final int LETHAL_COLOR = 0xFFFF7185;
    private static final int HIT_COLOR = 0xFF83B7D9;
    private static final int BACKGROUND_COLOR = 0xCC131C29;
    private static List<Hit> hits = List.of();
    private static Component title = Component.empty();
    private static long shownAt;
    private static boolean showing;

    private record Hit(Component attacker, Component amounts, Component source, int color) {}

    private DamageRecapHud() {}

    static void show(List<CompoundTag> incident, int reason) {
        List<Hit> visible = new ArrayList<>(MAX_VISIBLE_HITS);
        for (int i = incident.size() - 1; i >= 0 && visible.size() < MAX_VISIBLE_HITS; i--) {
            CompoundTag entry = incident.get(i);
            Component amounts = entry.getBoolean("unmeasured")
                    ? Component.translatable("aatu_ui.recap.unmeasured")
                    : Component.translatable("aatu_ui.recap.hud_amounts",
                            DamageRecapScreen.number(entry.getFloat("damage")),
                            DamageRecapScreen.number(entry.getFloat("loss")));
            String source = entry.getString("source");
            visible.add(new Hit(DamageRecapScreen.attacker(entry), amounts,
                    Component.literal(source.isBlank() ? "--" : source),
                    entry.getBoolean("lethal") ? LETHAL_COLOR : HIT_COLOR));
        }
        hits = List.copyOf(visible);
        title = Component.translatable(reason == 1 ? "aatu_ui.recap.hud_down" : "aatu_ui.recap.hud_death");
        shownAt = Util.getMillis();
        showing = true;
    }

    static boolean active() {
        if (!showing) return false;
        UiSettings settings = UiSettings.get();
        long duration = UiSettings.validRecapSeconds(settings.damageRecapSeconds) * 1_000L;
        if (!settings.autoDamageRecap || Util.getMillis() - shownAt >= duration) clear();
        return showing;
    }

    public static void render(GuiGraphics graphics, DeltaTracker delta) {
        if (Minecraft.getInstance().screen == null) draw(graphics, false);
    }

    static void screenRender(ScreenEvent.Render.Post event) {
        // Render after the death-screen backdrop, without replacing its respawn controls.
        if (event.getScreen() instanceof DeathScreen) draw(event.getGuiGraphics(), true);
    }

    private static void draw(GuiGraphics graphics, boolean deathScreen) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!active() || minecraft.player == null || minecraft.level == null || minecraft.options.hideGui) return;
        Font font = minecraft.font;
        int screenWidth = graphics.guiWidth();
        int screenHeight = graphics.guiHeight();
        int width = Math.min(MAX_WIDTH, (screenWidth - MARGIN * 3) / 2);
        if (width <= PADDING * 2) return;
        int lineHeight = font.lineHeight + 2;
        int rowHeight = lineHeight * 3 + ROW_GAP;
        DamageRecapCorner corner = UiSettings.get().recapCorner();
        int topMargin = deathScreen && corner == DamageRecapCorner.TOP_LEFT ? 32 : MARGIN;
        int fixedHeight = PADDING * 2 + lineHeight * 2 + ROW_GAP;
        int availableRows = Math.max(0, (screenHeight - topMargin - MARGIN - fixedHeight) / rowHeight);
        int rows = Math.min(hits.size(), availableRows);
        int height = fixedHeight + (hits.isEmpty() ? lineHeight : rows * rowHeight);
        if (height > screenHeight - topMargin - MARGIN) return;
        int x = corner.right ? screenWidth - width - MARGIN : MARGIN;
        int y = corner.bottom ? screenHeight - height - MARGIN : topMargin;

        graphics.fill(x, y, x + width, y + height, BACKGROUND_COLOR);
        int textX = x + PADDING;
        int textWidth = width - PADDING * 2;
        int textY = y + PADDING;
        drawLine(graphics, font, title, textX, textY, textWidth, TEXT_COLOR);
        textY += lineHeight + ROW_GAP;
        if (hits.isEmpty()) {
            drawLine(graphics, font, Component.translatable("aatu_ui.recap.no_incident"),
                    textX, textY, textWidth, MUTED_COLOR);
        } else {
            for (int i = 0; i < rows; i++) {
                Hit hit = hits.get(i);
                drawLine(graphics, font, hit.attacker, textX, textY, textWidth, hit.color);
                drawLine(graphics, font, hit.amounts, textX, textY + lineHeight, textWidth, TEXT_COLOR);
                drawLine(graphics, font, hit.source, textX, textY + lineHeight * 2, textWidth, MUTED_COLOR);
                textY += rowHeight;
            }
        }
        Component details = deathScreen ? Component.translatable("aatu_ui.recap.hud_death_details")
                : Component.translatable("aatu_ui.recap.hud_details", AaTuUi.recapKeyName());
        drawLine(graphics, font, details,
                textX, y + height - PADDING - lineHeight, textWidth, MUTED_COLOR);
    }

    private static void drawLine(GuiGraphics graphics, Font font, Component text, int x, int y, int width, int color) {
        graphics.drawString(font, font.plainSubstrByWidth(text.getString(), width), x, y, color);
    }

    static void clear() {
        hits = List.of();
        title = Component.empty();
        showing = false;
        shownAt = 0;
    }
}
