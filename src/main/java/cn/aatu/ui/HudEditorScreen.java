package cn.aatu.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class HudEditorScreen extends CrispScreen {
    private final Screen previous;
    private HudPart selected = HudPart.HEALTH;
    private HudPart dragging;
    private int dragX;
    private int dragY;
    private boolean draggingGroup;
    private int draggedGroupWidth;
    private int draggedGroupHeight;
    private Button visibilityButton;
    private Button styleButton;
    private Button optionsButton;
    private Button selectedButton;
    private Button smallerButton;
    private Button scaleButton;
    private Button largerButton;

    public HudEditorScreen(Screen previous) {
        super(Component.translatable("aatu_ui.editor"));
        this.previous = previous;
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.translatable("aatu_ui.fonts"), button -> minecraft.setScreen(new FontScreen(this)))
                .bounds(6, 6, 78, 20).build());
        visibilityButton = addRenderableWidget(Button.builder(Component.empty(), button -> toggleVisibility())
                .bounds(6, 30, 78, 20).build());
        styleButton = addRenderableWidget(Button.builder(Component.empty(), button -> changeStyle())
                .bounds(6, 54, 78, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("aatu_ui.reset"), button -> resetSelected())
                .bounds(6, 78, 78, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(6, 102, 78, 20).build());
        optionsButton = addRenderableWidget(Button.builder(Component.translatable("aatu_ui.details"),
                button -> minecraft.setScreen(new PartOptionsScreen(this, selected)))
                .bounds(6, 126, 78, 20).build());
        selectedButton = addRenderableWidget(Button.builder(Component.empty(), button -> {
            HudPart[] parts = HudPart.values();
            int index = selected.ordinal();
            do {
                index = (index + 1) % parts.length;
            } while (!parts[index].available());
            selected = parts[index];
            updateButtons();
        }).bounds(6, 150, 78, 20).build());
        smallerButton = addRenderableWidget(Button.builder(Component.literal("−"), button -> changeScale(-UiSettings.HUD_SCALE_STEP))
                .tooltip(Tooltip.create(Component.translatable("aatu_ui.scale_down"))).bounds(6, 174, 18, 20).build());
        scaleButton = addRenderableWidget(Button.builder(Component.empty(), button -> {
            selected.placement().scale = UiSettings.DEFAULT_HUD_SCALE;
            UiSettings.get().save();
            updateButtons();
        }).tooltip(Tooltip.create(Component.translatable("aatu_ui.scale_reset"))).bounds(26, 174, 38, 20).build());
        largerButton = addRenderableWidget(Button.builder(Component.literal("+"), button -> changeScale(UiSettings.HUD_SCALE_STEP))
                .tooltip(Tooltip.create(Component.translatable("aatu_ui.scale_up"))).bounds(66, 174, 18, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("aatu_ui.recap.title"), button ->
                minecraft.setScreen(new DamageRecapScreen(this, DamageRecapClient.hasIncident())))
                .bounds(6, 198, 78, 20).build());
        updateButtons();
    }

    private void changeScale(float amount) {
        selected.placement().changeScale(amount);
        UiSettings.get().save();
        updateButtons();
    }

    private void toggleVisibility() {
        selected.placement().visible = !selected.placement().visible;
        UiSettings.get().save();
        updateButtons();
    }

    private void changeStyle() {
        if (selected == HudPart.STATS) {
            selected.placement().style = StatBarStyle.from(selected.placement().style).next().id;
        } else {
            selected.placement().nextStyle();
        }
        UiSettings.get().save();
        updateButtons();
    }

    private void resetSelected() {
        if (selected == HudPart.CHAMPION_AFFIXES) UiSettings.get().championAffixAnchor = "free";
        selected.placement().reset();
        UiSettings.get().save();
        updateButtons();
    }

    private void updateButtons() {
        String visibilityKey = selected == HudPart.CHAMPION
                ? (selected.placement().visible ? "aatu_ui.hide_bar" : "aatu_ui.show_bar")
                : (selected.placement().visible ? "aatu_ui.hide" : "aatu_ui.show");
        visibilityButton.setMessage(Component.translatable(visibilityKey));
        String style = selected == HudPart.STATS
                ? StatBarStyle.from(selected.placement().style).id : selected.placement().style().id;
        styleButton.setMessage(Component.translatable("aatu_ui.style", Component.translatable("aatu_ui.style." + style)));
        styleButton.visible = selected != HudPart.CHAMPION_AFFIXES && selected != HudPart.ARMOR;
        optionsButton.visible = selected == HudPart.BOSS || selected == HudPart.CHAMPION || selected == HudPart.CHAMPION_AFFIXES || selected == HudPart.EFFECTS
                || selected == HudPart.STATS || selected == HudPart.HOTBAR || selected == HudPart.SPELLS
                || selected == HudPart.HEALTH || selected == HudPart.HUNGER || selected == HudPart.EXPERIENCE;
        selectedButton.setMessage(Component.translatable("aatu_ui.select_part", Component.translatable(selected.label)));
        scaleButton.setMessage(Component.literal(Math.round(selected.scale() * 100) + "%"));
        smallerButton.active = selected.scale() > UiSettings.MIN_HUD_SCALE;
        largerButton.active = selected.scale() < UiSettings.MAX_HUD_SCALE;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        drawBackdrop(graphics, partialTick);
        if (HudPart.CHAMPION_AFFIXES.available()) UiSettings.get().prepareChampionAffixes(width, height);
        for (HudPart part : HudPart.values()) {
            if (!part.available()) continue;
            ChampionAffixAnchor.Bounds bounds = bounds(part);
            int x = bounds.x();
            int y = bounds.y();
            HudRenderer.preview(graphics, part);
            if (!part.placement().visible) {
                graphics.fill(x, y, x + bounds.width(), y + bounds.height(), 0xAA16191D);
            }
            if (part == selected) {
                graphics.renderOutline(x - 1, y - 1, bounds.width() + 2, bounds.height() + 2, 0xFFE4EAF5);
                graphics.drawString(font, Component.translatable(part.label), x, Math.max(0, y - 10), 0xFFFFFFFF, true);
            }
        }
        drawWidgets(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button != 0) return false;
        if (contains(selected, mouseX, mouseY)) return startDragging(selected, mouseX, mouseY);
        HudPart[] parts = HudPart.values();
        for (int i = parts.length - 1; i >= 0; i--) {
            HudPart part = parts[i];
            if (contains(part, mouseX, mouseY)) return startDragging(part, mouseX, mouseY);
        }
        return false;
    }

    private boolean contains(HudPart part, double mouseX, double mouseY) {
        if (!part.available()) return false;
        ChampionAffixAnchor.Bounds bounds = bounds(part);
        int x = bounds.x();
        int y = bounds.y();
        return part == HudPart.BOSS
                ? BossLayout.previewContains(mouseX, mouseY, x, y, width, height)
                : mouseX >= x && mouseX < x + bounds.width() && mouseY >= y && mouseY < y + bounds.height();
    }

    private ChampionAffixAnchor.Bounds bounds(HudPart part) {
        if (part == HudPart.BOSS) return BossLayout.previewBounds(part.x(width, height), part.y(width, height), width, height);
        if (part == HudPart.CHAMPION) {
            ChampionAffixAnchor.Position position = ChampionAffixAnchor.championPosition(font, width, height, true);
            return new ChampionAffixAnchor.Bounds(Math.round(position.x()), Math.round(position.y()), part.pixelWidth(), part.pixelHeight());
        }
        return part == HudPart.CHAMPION_AFFIXES ? ChampionAffixAnchor.previewBounds(font, width, height)
                : new ChampionAffixAnchor.Bounds(part.x(width, height), part.y(width, height), part.pixelWidth(), part.pixelHeight());
    }

    private boolean startDragging(HudPart part, double mouseX, double mouseY) {
        selected = part;
        dragging = part;
        ChampionAffixAnchor.Bounds group = part == HudPart.CHAMPION || part == HudPart.CHAMPION_AFFIXES
                ? ChampionAffixAnchor.groupBounds(font, width, height, true) : null;
        draggingGroup = group != null;
        ChampionAffixAnchor.Bounds bounds = group == null ? bounds(part) : group;
        draggedGroupWidth = bounds.width();
        draggedGroupHeight = bounds.height();
        dragX = (int) mouseX - bounds.x();
        dragY = (int) mouseY - bounds.y();
        updateButtons();
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (button == 0 && dragging != null) {
            if (draggingGroup) {
                int groupWidth = draggedGroupWidth;
                int groupHeight = draggedGroupHeight;
                int x = Math.max(0, Math.min(width - groupWidth, (int) mouseX - dragX));
                int y = Math.max(0, Math.min(height - groupHeight, (int) mouseY - dragY));
                UiSettings.get().championAffixAnchor = "champion";
                UiSettings.get().championAffixSide = "below";
                HudPart.CHAMPION.placement().setPosition(x + (groupWidth - HudPart.CHAMPION.pixelWidth()) / 2, y, width, height);
                HudPart.CHAMPION_AFFIXES.placement().setPosition(x + (groupWidth - HudPart.CHAMPION_AFFIXES.pixelWidth()) / 2,
                        y + HudPart.CHAMPION.pixelHeight() + 3, width, height);
                return true;
            }
            if (dragging == HudPart.CHAMPION_AFFIXES && ChampionAffixAnchor.anchor() != ChampionAffixAnchor.Anchor.FREE) {
                ChampionAffixAnchor.Bounds attached = bounds(dragging);
                int x = Math.max(0, Math.min(width - dragging.pixelWidth(), (int) mouseX - dragX + attached.width() / 2 - dragging.pixelWidth() / 2));
                int y = Math.max(0, Math.min(height - dragging.pixelHeight(), (int) mouseY - dragY));
                UiSettings.get().championAffixAnchor = "free";
                dragging.placement().setPosition(x, y, width, height);
                dragX = (int) mouseX - x;
                dragY = (int) mouseY - y;
                return true;
            }
            int x = Math.max(0, Math.min(width - dragging.pixelWidth(), (int) mouseX - dragX));
            int y = Math.max(0, Math.min(height - dragging.pixelHeight(), (int) mouseY - dragY));
            dragging.placement().setPosition(x, y, width, height);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && dragging != null) {
            dragging = null;
            draggingGroup = false;
            UiSettings.get().save();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void onClose() {
        UiSettings.get().save();
        minecraft.setScreen(previous);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
