package cn.aatu.ui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffect;

public final class EffectColorsScreen extends CrispScreen {
    private final Screen previous;
    private final boolean health;
    private final List<Button> effectButtons = new ArrayList<>();
    private List<MobEffect> effects = List.of();
    private EditBox search;
    private EditBox color;
    private MobEffect selected;
    private Button save;
    private Button remove;
    private Button previousPage;
    private Button nextPage;
    private int page;
    private int rows;
    private int formY;
    private boolean saveFailed;

    public EffectColorsScreen(Screen previous, boolean health) {
        super(Component.translatable(health ? "aatu_ui.health_colors" : "aatu_ui.hunger_colors"));
        this.previous = previous;
        this.health = health;
    }

    @Override
    protected void init() {
        rows = Math.max(1, Math.min(8, (height - 174) / 22));
        formY = 62 + rows * 22 + 28;
        int contentWidth = Math.min(360, width - 24);
        int left = (width - contentWidth) / 2;
        search = addRenderableWidget(new EditBox(font, left, 32, contentWidth, 20, Component.translatable("aatu_ui.effect_search")));
        search.setHint(Component.translatable("aatu_ui.effect_search"));
        search.setMaxLength(128);
        color = addRenderableWidget(new EditBox(font, left + 22, formY, 90, 20, Component.literal("#RRGGBB")));
        color.setMaxLength(7);
        color.setValue("#FFFFFF");
        addRenderableWidget(Button.builder(Component.translatable("aatu_ui.effect_color"), button -> {
            if (selected != null) color.setValue(effectColor(selected));
        }).bounds(left + 118, formY, contentWidth - 118, 20).build());
        save = addRenderableWidget(Button.builder(Component.translatable("aatu_ui.color_save"), button -> saveRule(false))
                .bounds(left, formY + 24, contentWidth / 2 - 2, 20).build());
        remove = addRenderableWidget(Button.builder(Component.translatable("aatu_ui.color_remove"), button -> saveRule(true))
                .bounds(left + contentWidth / 2 + 2, formY + 24, contentWidth / 2 - 2, 20).build());
        previousPage = addRenderableWidget(Button.builder(Component.literal("<"), button -> {
            page--;
            showPage();
        }).bounds(left, formY - 26, 35, 20).build());
        nextPage = addRenderableWidget(Button.builder(Component.literal(">"), button -> {
            page++;
            showPage();
        }).bounds(left + contentWidth - 35, formY - 26, 35, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(width / 2 - 80, height - 25, 160, 20).build());
        color.setResponder(value -> updateActions());
        search.setResponder(value -> filter());
        filter();
    }

    private void filter() {
        String query = search.getValue().toLowerCase(Locale.ROOT).trim();
        effects = BuiltInRegistries.MOB_EFFECT.stream()
                .filter(effect -> id(effect).contains(query) || name(effect).toLowerCase(Locale.ROOT).contains(query))
                .sorted(Comparator.comparing(EffectColorsScreen::id)).toList();
        page = 0;
        showPage();
    }

    private void showPage() {
        effectButtons.forEach(this::removeWidget);
        effectButtons.clear();
        int contentWidth = Math.min(360, width - 24);
        int left = (width - contentWidth) / 2;
        page = Math.max(0, Math.min(page, Math.max(0, (effects.size() - 1) / rows)));
        for (int row = 0; row < rows && page * rows + row < effects.size(); row++) {
            MobEffect effect = effects.get(page * rows + row);
            String label = (rules().containsKey(id(effect)) ? "● " : "") + name(effect) + "  " + id(effect);
            Button button = Button.builder(Component.literal(font.plainSubstrByWidth(label, contentWidth - 12)), clicked -> {
                selected = effect;
                color.setValue(rules().getOrDefault(id(effect), effectColor(effect)));
                updateActions();
            }).bounds(left, 60 + row * 22, contentWidth, 20).build();
            effectButtons.add(addRenderableWidget(button));
        }
        previousPage.active = page > 0;
        nextPage.active = (page + 1) * rows < effects.size();
        updateActions();
    }

    private Map<String, String> rules() {
        return health ? UiSettings.get().healthEffectColors : UiSettings.get().hungerEffectColors;
    }

    private void updateActions() {
        save.active = selected != null && HudColors.parse(color.getValue()) != null;
        remove.active = selected != null && rules().containsKey(id(selected));
    }

    private void saveRule(boolean delete) {
        if (selected == null) return;
        Integer parsed = HudColors.parse(color.getValue());
        if (!delete && parsed == null) return;
        Map<String, String> before = new LinkedHashMap<>(rules());
        Map<String, String> changed = new LinkedHashMap<>();
        if (!delete) changed.put(id(selected), String.format(Locale.ROOT, "#%06X", parsed & 0xFFFFFF));
        before.forEach((key, value) -> { if (!key.equals(id(selected))) changed.put(key, value); });
        if (changed.size() > 128) { saveFailed = true; return; }
        rules().clear();
        rules().putAll(changed);
        saveFailed = !UiSettings.get().save();
        if (saveFailed) {
            rules().clear();
            rules().putAll(before);
        }
        HudColors.invalidate();
        showPage();
    }

    private static String id(MobEffect effect) {
        return BuiltInRegistries.MOB_EFFECT.getKey(effect).toString();
    }

    private static String name(MobEffect effect) {
        return Component.translatable(effect.getDescriptionId()).getString();
    }

    private static String effectColor(MobEffect effect) {
        return String.format(Locale.ROOT, "#%06X", effect.getColor() & 0xFFFFFF);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        drawBackdrop(graphics, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 12, 0xFFFFFFFF);
        if (selected != null) {
            graphics.drawCenteredString(font, font.plainSubstrByWidth(name(selected), Math.min(270, width - 110)),
                    width / 2, formY - 20, 0xFFFFFFFF);
        }
        Integer swatch = HudColors.parse(color.getValue());
        int left = (width - Math.min(360, width - 24)) / 2;
        graphics.fill(left, formY + 2, left + 16, formY + 18, swatch == null ? 0xFF555555 : swatch);
        if (saveFailed) graphics.drawCenteredString(font, Component.translatable("aatu_ui.save_error"), width / 2, height - 38, 0xFFFF7777);
        drawWidgets(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(previous);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
