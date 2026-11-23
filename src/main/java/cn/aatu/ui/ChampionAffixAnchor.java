package cn.aatu.ui;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;

public final class ChampionAffixAnchor {
    private static final int GAP = 3;
    private static final int TEXT_PADDING = 2;
    private static final int MAX_BARS = 128;
    private static final float MIN_SCALE = 0.5F;

    private ChampionAffixAnchor() {}

    public enum Anchor {
        FREE("free"), CHAMPION("champion"), BOSS("boss");

        public final String id;
        Anchor(String id) { this.id = id; }
        public Anchor next() { return values()[(ordinal() + 1) % values().length]; }
    }

    public enum Side {
        BELOW("below"), LEFT("left"), RIGHT("right");

        public final String id;
        Side(String id) { this.id = id; }
        public Side next() { return values()[(ordinal() + 1) % values().length]; }
    }

    public static Anchor anchor() {
        String configured = UiSettings.get().championAffixAnchor;
        for (Anchor value : Anchor.values()) if (value.id.equals(configured)) return value;
        return Anchor.FREE;
    }

    public static Side side() {
        String configured = UiSettings.get().championAffixSide;
        for (Side value : Side.values()) if (value.id.equals(configured)) return value;
        return Side.BELOW;
    }

    public static boolean groupAttached() {
        return UiSettings.get().championGroupWithBoss && anchor() == Anchor.BOSS
                && HudPart.CHAMPION.placement().visible && HudPart.CHAMPION_AFFIXES.placement().visible;
    }

    @Nullable
    public static Bounds groupBounds(Font font, int width, int height, boolean preview) {
        if (!groupAttached()) return null;
        List<Rect> bars = new ArrayList<>();
        Rect boss = preview ? previewBossBars(font, width, height, bars) : bottomBossBar(font, width, height, bars);
        if (boss == null) return null;
        int groupWidth = Math.max(HudPart.CHAMPION.pixelWidth(), HudPart.CHAMPION_AFFIXES.pixelWidth());
        int groupHeight = HudPart.CHAMPION.pixelHeight() + GAP + HudPart.CHAMPION_AFFIXES.scaled(font.lineHeight + 2);
        Side preferred = side();
        Side[] attempts = {preferred, Side.BELOW, Side.RIGHT, Side.LEFT};
        for (Side attempt : attempts) {
            int x = switch (attempt) {
                case BELOW -> Mth.clamp(boss.x + (boss.width - groupWidth) / 2, 0, Math.max(0, width - groupWidth));
                case LEFT -> boss.visualLeft - GAP - groupWidth;
                case RIGHT -> boss.visualRight + GAP;
            };
            int y = attempt == Side.BELOW ? boss.y + boss.height + GAP : boss.y;
            if (x < 0 || y < 0 || x + groupWidth > width || y + groupHeight > height) continue;
            boolean occupied = false;
            for (Rect other : bars) {
                if (x < other.visualRight + GAP && x + groupWidth > other.visualLeft - GAP
                        && y < other.y + other.height + GAP && y + groupHeight > other.y - GAP) {
                    occupied = true;
                    break;
                }
            }
            if (!occupied) return new Bounds(x, y, groupWidth, groupHeight);
        }
        return null;
    }

    public static Position championPosition(Font font, int width, int height, boolean preview) {
        Bounds group = groupBounds(font, width, height, preview);
        return group == null ? new Position(HudPart.CHAMPION.x(width, height), HudPart.CHAMPION.y(width, height), HudPart.CHAMPION.scale())
                : new Position(group.x + (group.width - HudPart.CHAMPION.pixelWidth()) / 2.0F, group.y, HudPart.CHAMPION.scale());
    }

    public static Position textPosition(Font font, String text, int width, int height, boolean preview) {
        int textWidth = font.width(text);
        Bounds group = groupBounds(font, width, height, preview);
        if (group != null) {
            float scale = Math.min(HudPart.CHAMPION_AFFIXES.scale(), (group.width - 2.0F * TEXT_PADDING) / Math.max(1, textWidth));
            return new Position(group.x + (group.width - textWidth * scale) / 2.0F,
                    group.y + HudPart.CHAMPION.pixelHeight() + GAP, scale);
        }
        Anchor anchor = groupAttached() ? Anchor.CHAMPION : anchor();
        if (anchor == Anchor.FREE) return freePosition(textWidth, width, height);

        List<Rect> bars = new ArrayList<>();
        Rect bar;
        if (anchor == Anchor.CHAMPION) {
            if (!preview && !HudPart.CHAMPION.placement().visible) return freePosition(textWidth, width, height);
            int x = HudPart.CHAMPION.x(width, height);
            int y = HudPart.CHAMPION.y(width, height);
            HudPart champion = HudPart.CHAMPION;
            bar = new Rect(x, y, x, x + champion.pixelWidth(), champion.pixelWidth(), champion.pixelHeight(),
                    y + champion.scaled(20), champion.scaled(5));
            bars.add(bar);
        } else {
            bar = preview ? previewBossBars(font, width, height, bars) : bottomBossBar(font, width, height, bars);
            if (bar == null) return freePosition(textWidth, width, height);
        }

        Side preferred = side();
        Position position = preferred == Side.BELOW
                ? below(bar, textWidth, font.lineHeight, width, height)
                : beside(bar, bars, preferred == Side.LEFT, textWidth, font.lineHeight, width, height);
        if (position != null) return position;

        // Do not clamp text across a bar when the requested side is too close to an edge.
        if (preferred != Side.BELOW) {
            position = beside(bar, bars, preferred != Side.LEFT, textWidth, font.lineHeight, width, height);
            if (position != null) return position;
            position = below(bar, textWidth, font.lineHeight, width, height);
        } else {
            position = beside(bar, bars, false, textWidth, font.lineHeight, width, height);
            if (position == null) position = beside(bar, bars, true, textWidth, font.lineHeight, width, height);
        }
        return position != null ? position : new Position(0, 0, 0);
    }

    @Nullable
    private static Position beside(Rect selected, List<Rect> bars, boolean left, int textWidth, int textHeight,
                                   int width, int height) {
        float centerY = selected.barY + selected.barHeight / 2.0F;
        float desiredScale = HudPart.CHAMPION_AFFIXES.scale();
        float top = centerY - textHeight * desiredScale / 2.0F - TEXT_PADDING;
        float bottom = centerY + textHeight * desiredScale / 2.0F + TEXT_PADDING;
        float start = left ? 0 : Math.max(0, selected.visualRight + GAP);
        float end = left ? Math.min(width, selected.visualLeft - GAP) : width;
        for (Rect other : bars) {
            if (other == selected || other.y >= bottom || other.y + other.height <= top) continue;
            if (left && other.visualLeft < end && other.visualRight > start) {
                start = Math.max(start, other.visualRight + GAP);
            } else if (!left && other.visualRight > start && other.visualLeft < end) {
                end = Math.min(end, other.visualLeft - GAP);
            }
        }
        float roomWidth = end - start - 2 * TEXT_PADDING;
        float roomHeight = 2 * Math.min(centerY, height - centerY) - 2 * TEXT_PADDING;
        float scale = Math.min(desiredScale, Math.min(roomWidth / Math.max(1, textWidth + 1), roomHeight / Math.max(1, textHeight + 1)));
        if (scale < MIN_SCALE * desiredScale) return null;
        float x = left ? end - TEXT_PADDING - textWidth * scale : start + TEXT_PADDING;
        return new Position(x, centerY - textHeight * scale / 2.0F, scale);
    }

    @Nullable
    private static Position below(Rect bar, int textWidth, int textHeight, int width, int height) {
        int y = bar.y + bar.height + GAP;
        float roomWidth = width - 2.0F * TEXT_PADDING;
        float roomHeight = height - y - TEXT_PADDING;
        float desiredScale = HudPart.CHAMPION_AFFIXES.scale();
        float scale = Math.min(desiredScale, Math.min(roomWidth / Math.max(1, textWidth + 1), roomHeight / Math.max(1, textHeight + 1)));
        if (scale < MIN_SCALE * desiredScale) return null;
        float x = Mth.clamp(bar.x + (bar.width - textWidth * scale) / 2.0F,
                (float) TEXT_PADDING, width - TEXT_PADDING - (textWidth + 1) * scale);
        return new Position(x, y, scale);
    }

    public static Bounds previewBounds(Font font, int width, int height) {
        if (anchor() == Anchor.FREE) {
            return new Bounds(HudPart.CHAMPION_AFFIXES.x(width, height), HudPart.CHAMPION_AFFIXES.y(width, height),
                    HudPart.CHAMPION_AFFIXES.pixelWidth(), HudPart.CHAMPION_AFFIXES.pixelHeight());
        }
        String text = Component.translatable("aatu_ui.affixes").getString();
        Position position = textPosition(font, text, width, height, true);
        int x = Math.max(0, Mth.floor(position.x) - TEXT_PADDING);
        int y = Math.max(0, Mth.floor(position.y));
        int textWidth = Math.max(1, Mth.ceil((font.width(text) + 1) * position.scale) + 2 * TEXT_PADDING);
        int textHeight = Math.max(1, Mth.ceil((font.lineHeight + 1) * position.scale));
        return new Bounds(x, y, Math.min(Math.max(1, width - x), textWidth), Math.min(Math.max(1, height - y), textHeight));
    }

    private static Position freePosition(int textWidth, int width, int height) {
        HudPart part = HudPart.CHAMPION_AFFIXES;
        return new Position(part.x(width, height) + (part.pixelWidth() - textWidth * part.scale()) / 2.0F,
                part.y(width, height), part.scale());
    }

    @Nullable
    private static Rect previewBossBars(Font font, int width, int height, List<Rect> bars) {
        int count = Math.min(3, UiSettings.get().bossLimit == 0 ? 3 : Math.max(1, UiSettings.get().bossLimit));
        int titleWidth = font.width(Component.translatable(HudPart.BOSS.label));
        for (int slot = 0; slot < count; slot++) bars.add(bossRect(slot, titleWidth, width, height));
        Rect bottom = bottomVisible(bars, width, height);
        if (bottom != null && ModList.get().isLoaded("cataclysm")) {
            int slot = bars.indexOf(bottom);
            bars.set(slot, reserveRageSpace(font, slot, bars.size(), bottom, width, height));
        }
        return bottomVisible(bars, width, height);
    }

    @Nullable
    private static Rect bottomBossBar(Font font, int width, int height, List<Rect> bars) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!HudPart.BOSS.placement().visible || !(minecraft.gui.getBossOverlay() instanceof BossLayoutHook hook)) return null;
        int configuredLimit = Mth.clamp(UiSettings.get().bossLimit, 0, MAX_BARS);
        int limit = configuredLimit == 0 ? MAX_BARS : configuredLimit;
        int slot = 0;
        List<Integer> remnantSlots = new ArrayList<>();
        for (LerpingBossEvent boss : hook.aatu$bossEvents().values()) {
            if (CataclysmBossMeters.isRage(boss)) continue;
            if (slot >= limit) break;
            int titleWidth = font.width(HudCompatibility.bossTitle(boss, boss.getName()));
            bars.add(bossRect(slot, titleWidth, width, height));
            if (CataclysmBossMeters.hasMeter(boss)) remnantSlots.add(slot);
            slot++;
        }
        for (int remnantSlot : remnantSlots) {
            Rect bar = bars.get(remnantSlot);
            bars.set(remnantSlot, reserveRageSpace(font, remnantSlot, bars.size(), bar, width, height));
        }
        return bottomVisible(bars, width, height);
    }

    private static Rect reserveRageSpace(Font font, int slot, int count, Rect bar, int width, int height) {
        CataclysmBossMeters.Side rageSide = CataclysmBossMeters.side(font, slot, count, bar.x, width, height);
        int outside = HudPart.BOSS.scaled(CataclysmBossMeters.outsideWidth(font));
        int left = rageSide == CataclysmBossMeters.Side.LEFT ? Math.min(bar.visualLeft, bar.x - outside) : bar.visualLeft;
        int right = rageSide == CataclysmBossMeters.Side.RIGHT ? Math.max(bar.visualRight, bar.x + bar.width + outside) : bar.visualRight;
        return new Rect(bar.x, bar.y, left, right, bar.width, bar.height, bar.barY, bar.barHeight);
    }

    @Nullable
    private static Rect bottomVisible(List<Rect> bars, int width, int height) {
        Rect bottom = null;
        for (Rect bar : bars) {
            if (bar.x + bar.width <= 0 || bar.x >= width || bar.barY + bar.barHeight <= 0 || bar.barY >= height) continue;
            if (bottom == null || bar.barY >= bottom.barY) bottom = bar;
        }
        return bottom;
    }

    private static Rect bossRect(int slot, int titleWidth, int width, int height) {
        BossLayout.Position position = BossLayout.position(slot, HudPart.BOSS.x(width, height), HudPart.BOSS.y(width, height), width, height);
        int x = position.x();
        int y = position.y();
        HudPart part = HudPart.BOSS;
        titleWidth = part.scaled(titleWidth);
        int titleX = x + (part.pixelWidth() - titleWidth) / 2;
        boolean original = HudPart.BOSS.placement().style() == HudStyle.ORIGINAL;
        return new Rect(x, y, Math.min(x, titleX), Math.max(x + part.pixelWidth(), titleX + titleWidth),
                part.pixelWidth(), part.pixelHeight(), y + part.scaled(original ? 9 : 11), part.scaled(original ? 5 : 6));
    }

    public record Position(float x, float y, float scale) {}
    public record Bounds(int x, int y, int width, int height) {}
    private record Rect(int x, int y, int visualLeft, int visualRight, int width, int height, int barY, int barHeight) {}
}
