package cn.aatu.ui;

import cn.aatu.ui.font.FontQuality;
import cn.aatu.ui.font.SystemFontCatalog;
import cn.aatu.ui.font.SystemFontCatalog.Face;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class FontScreen extends CrispScreen {
    private static final int MAX_ROWS = 8;
    private static final float FONT_SIZE_STEP = 0.5F;
    private static final int FONT_SIZE_Y = 64;
    private static final int FONT_LIST_Y = 86;
    private final Screen previous;
    private final List<Button> fontButtons = new ArrayList<>();
    private List<Face> fonts = List.of();
    private List<Face> filtered = List.of();
    private boolean systemTab;
    private boolean applyingFont;
    private boolean importingFont;
    private boolean lastBusy;
    private int page;
    private int pageSize;
    private long catalogRequest;
    private long importRequest;
    private int left;
    private int panelWidth;
    private EditBox search;
    private Button previousPage;
    private Button nextPage;
    private Button sourceMode;
    private Button smoothing;
    private Button smallerFont;
    private Button largerFont;
    private Button applyFontSize;
    private Button defaultFont;
    private Button done;
    private float pendingFontSize;
    private Component fontSizeLabel;
    private Component status = Component.translatable("aatu_ui.font_drop");

    public FontScreen(Screen previous) {
        super(Component.translatable("aatu_ui.fonts"));
        this.previous = previous;
        this.pendingFontSize = UiSettings.get().fontSize;
    }

    @Override
    protected void init() {
        fontButtons.clear();
        panelWidth = Math.min(280, width - 12);
        left = (width - panelWidth) / 2;
        int half = (panelWidth - 4) / 2;
        pageSize = Math.max(1, Math.min(MAX_ROWS, (height - 162) / 19));

        addRenderableWidget(Button.builder(Component.translatable("aatu_ui.font_local"), button -> openTab(false))
                .bounds(left, 22, half, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("aatu_ui.font_system"), button -> openTab(true))
                .bounds(left + half + 4, 22, half, 18).build());
        search = addRenderableWidget(new EditBox(font, left, 43, panelWidth, 18, Component.translatable("aatu_ui.font_search")));
        search.setHint(Component.translatable("aatu_ui.font_search"));
        search.setResponder(value -> { page = 0; filterAndDraw(); });

        smallerFont = addRenderableWidget(Button.builder(Component.literal("−"), button -> changeFontSize(-1))
                .bounds(left, FONT_SIZE_Y, 24, 18).build());
        largerFont = addRenderableWidget(Button.builder(Component.literal("+"), button -> changeFontSize(1))
                .bounds(left + 28, FONT_SIZE_Y, 24, 18).build());
        applyFontSize = addRenderableWidget(Button.builder(Component.translatable("aatu_ui.font_size_apply"), button -> applyFontSize())
                .bounds(left + panelWidth - 54, FONT_SIZE_Y, 54, 18).build());

        int navigationY = height - 60;
        previousPage = addRenderableWidget(Button.builder(Component.literal("<"), button -> { page--; updateRows(); })
                .bounds(left, navigationY, 24, 18).build());
        nextPage = addRenderableWidget(Button.builder(Component.literal(">"), button -> { page++; updateRows(); })
                .bounds(left + 28, navigationY, 24, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("aatu_ui.refresh"), button -> loadFonts(true))
                .bounds(left + panelWidth - 55, navigationY, 55, 18).build());

        sourceMode = addRenderableWidget(Button.builder(Component.empty(), button -> changeSourceMode())
                .bounds(left, height - 40, half, 18).build());
        smoothing = addRenderableWidget(Button.builder(Component.empty(), button -> toggleSmoothing())
                .bounds(left + half + 4, height - 40, half, 18).build());
        defaultFont = addRenderableWidget(Button.builder(Component.translatable("aatu_ui.font_default"), button -> selectDefault())
                .bounds(left, height - 20, half, 18).build());
        done = addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> finish())
                .bounds(left + half + 4, height - 20, half, 18).build());
        updateControls();
        loadFonts(false);
    }

    private void openTab(boolean system) {
        if (systemTab == system) return;
        systemTab = system;
        page = 0;
        fonts = List.of();
        search.setValue("");
        filterAndDraw();
        updateControls();
        loadFonts(false);
    }

    private void updateControls() {
        UiSettings settings = UiSettings.get();
        sourceMode.setMessage(Component.translatable(systemTab
                ? (settings.copySystemFonts ? "aatu_ui.font_copy_mode" : "aatu_ui.font_reference_mode")
                : "aatu_ui.open_folder"));
        smoothing.setMessage(Component.translatable(settings.smoothFonts ? "aatu_ui.smoothing_on" : "aatu_ui.smoothing_off"));
        updateFontSizeControls();
    }

    private void updateFontSizeControls() {
        float size = pendingFontSize;
        boolean pending = Float.compare(size, UiSettings.get().fontSize) != 0;
        boolean busy = isBusy();
        fontSizeLabel = Component.translatable("aatu_ui.font_size", Float.toString(size));
        smallerFont.active = !busy && size > UiSettings.MIN_FONT_SIZE;
        largerFont.active = !busy && size < UiSettings.MAX_FONT_SIZE;
        applyFontSize.active = !busy && pending;
        smoothing.active = !busy;
        defaultFont.active = !busy;
        done.active = !busy || !pending;
    }

    private boolean isBusy() {
        return applyingFont || importingFont || FontQuality.isRefreshing();
    }

    private void changeFontSize(int direction) {
        if (isBusy()) return;
        pendingFontSize = UiSettings.validFontSize(pendingFontSize + direction * FONT_SIZE_STEP);
        updateFontSizeControls();
    }

    private boolean applyFontSize() {
        if (isBusy()) return false;
        UiSettings settings = UiSettings.get();
        float old = settings.fontSize;
        if (Float.compare(pendingFontSize, old) == 0) return true;
        settings.fontSize = pendingFontSize;
        if (!settings.save()) {
            settings.fontSize = old;
            status = Component.translatable("aatu_ui.save_error");
            updateFontSizeControls();
            return false;
        }
        updateFontSizeControls();
        boolean hasSelectedFont = settings.systemFontReference
                ? !settings.systemFontFile.isBlank() : !settings.selectedFont.isBlank();
        if (hasSelectedFont) refreshFont();
        return true;
    }

    private void finish() {
        if (Float.compare(pendingFontSize, UiSettings.get().fontSize) == 0 || applyFontSize()) onClose();
    }

    private void changeSourceMode() {
        if (!systemTab) {
            try {
                Files.createDirectories(FontFiles.directory());
                Util.getPlatform().openPath(FontFiles.directory());
            } catch (IOException exception) {
                status = Component.translatable("aatu_ui.font_error");
            }
            return;
        }
        UiSettings settings = UiSettings.get();
        boolean old = settings.copySystemFonts;
        settings.copySystemFonts = !old;
        if (!settings.save()) {
            settings.copySystemFonts = old;
            status = Component.translatable("aatu_ui.save_error");
        }
        updateControls();
    }

    private void toggleSmoothing() {
        if (isBusy()) return;
        UiSettings settings = UiSettings.get();
        boolean old = settings.smoothFonts;
        settings.smoothFonts = !old;
        if (!settings.save()) {
            settings.smoothFonts = old;
            status = Component.translatable("aatu_ui.save_error");
            return;
        }
        updateControls();
        refreshFont();
    }

    private void loadFonts(boolean refresh) {
        long request = ++catalogRequest;
        boolean requestedSystemTab = systemTab;
        status = Component.translatable("aatu_ui.font_scanning");
        Minecraft client = minecraft;
        CompletableFuture.supplyAsync(() -> {
            try {
                return requestedSystemTab ? SystemFontCatalog.installed(refresh) : SystemFontCatalog.bundled();
            } catch (IOException exception) {
                throw new CompletionException(exception);
            }
        }).whenComplete((found, failure) -> client.execute(() -> {
            if (request != catalogRequest || client.screen != this) return;
            if (failure != null) {
                fonts = List.of();
                status = Component.translatable("aatu_ui.font_error");
            } else {
                fonts = found;
                status = Component.translatable(requestedSystemTab ? "aatu_ui.font_choose_system" : "aatu_ui.font_drop");
            }
            filterAndDraw();
        }));
    }

    private void filterAndDraw() {
        String query = search == null ? "" : search.getValue().strip().toLowerCase(Locale.ROOT);
        filtered = fonts.stream().filter(face -> query.isEmpty()
                || face.displayName().toLowerCase(Locale.ROOT).contains(query)
                || face.postScriptName().toLowerCase(Locale.ROOT).contains(query)
                || face.path().getFileName().toString().toLowerCase(Locale.ROOT).contains(query)).toList();
        page = Math.min(page, Math.max(0, (filtered.size() - 1) / pageSize));
        updateRows();
    }

    private void updateRows() {
        for (Button button : fontButtons) removeWidget(button);
        fontButtons.clear();
        for (int index = page * pageSize; index < Math.min(filtered.size(), (page + 1) * pageSize); index++) {
            Face face = filtered.get(index);
            String prefix = isSelected(face) ? "> " : "";
            String label = prefix + font.plainSubstrByWidth(face.displayName(), panelWidth - 20);
            int y = FONT_LIST_Y + (index - page * pageSize) * 19;
            Button button = addRenderableWidget(Button.builder(Component.literal(label), clicked -> choose(face))
                    .bounds(left, y, panelWidth, 18).build());
            button.active = !isBusy();
            fontButtons.add(button);
        }
        previousPage.active = page > 0;
        nextPage.active = (page + 1) * pageSize < filtered.size();
    }

    private boolean isSelected(Face face) {
        UiSettings settings = UiSettings.get();
        if (settings.selectedFontFace != face.index()) return false;
        if (systemTab) return settings.systemFontReference && settings.systemFontName.equals(face.postScriptName())
                && settings.systemFontFile.equalsIgnoreCase(face.path().getFileName().toString());
        return !settings.systemFontReference && settings.selectedFont.equals(face.path().getFileName().toString());
    }

    private void choose(Face face) {
        if (isBusy()) return;
        if (systemTab && UiSettings.get().copySystemFonts) {
            importFont(face.path(), face.index());
        } else {
            applySelection(face.path(), face.index(), systemTab ? face.postScriptName() : "", systemTab);
        }
    }

    private void selectDefault() {
        if (isBusy()) return;
        applySelection(null, 0, "", false);
    }

    private void applySelection(Path path, int face, String systemName, boolean reference) {
        UiSettings settings = UiSettings.get();
        Selection old = new Selection(settings.selectedFont, settings.selectedFontFace, settings.systemFontReference,
                settings.systemFontName, settings.systemFontFile);
        settings.systemFontReference = reference;
        settings.selectedFont = path == null || reference ? "" : path.getFileName().toString();
        settings.selectedFontFace = Math.max(0, face);
        settings.systemFontName = reference ? systemName : "";
        settings.systemFontFile = reference && path != null ? path.getFileName().toString() : "";
        if (path != null && FontFiles.selected() == null) {
            old.restore(settings);
            status = Component.translatable("aatu_ui.font_error");
            return;
        }
        if (!settings.save()) {
            old.restore(settings);
            status = Component.translatable("aatu_ui.save_error");
            return;
        }
        updateRows();
        refreshFont();
    }

    private void refreshFont() {
        applyingFont = true;
        updateRows();
        updateFontSizeControls();
        status = Component.translatable("aatu_ui.font_reloading");
        Minecraft client = minecraft;
        FontQuality.refresh(client).whenComplete((unused, failure) -> client.execute(() -> {
            applyingFont = false;
            if (client.screen == this) {
                updateRows();
                updateFontSizeControls();
                status = Component.translatable(failure == null
                        ? (systemTab ? "aatu_ui.font_choose_system" : "aatu_ui.font_drop") : "aatu_ui.font_error");
            }
        }));
    }

    private void importFont(Path path, int face) {
        if (isBusy()) return;
        importingFont = true;
        updateRows();
        updateFontSizeControls();
        long request = ++importRequest;
        Minecraft client = minecraft;
        status = Component.translatable("aatu_ui.font_importing");
        CompletableFuture.supplyAsync(() -> {
            try {
                return FontFiles.importFile(path);
            } catch (IOException exception) {
                throw new CompletionException(exception);
            }
        }).whenComplete((name, failure) -> client.execute(() -> finishImport(client, request, name, face, failure)));
    }

    private void finishImport(Minecraft client, long request, String name, int face, Throwable failure) {
        if (request != importRequest || client.screen != this) {
            importingFont = false;
            return;
        }
        if (failure != null) {
            importingFont = false;
            status = Component.translatable("aatu_ui.font_error");
            updateFontSizeControls();
            updateRows();
            return;
        }
        CompletableFuture<Void> current = FontQuality.currentRefresh();
        if (current != null && !current.isDone()) {
            current.whenComplete((unused, refreshFailure) -> client.execute(() -> finishImport(client, request, name, face, null)));
            return;
        }
        importingFont = false;
        systemTab = false;
        fonts = List.of();
        search.setValue("");
        filterAndDraw();
        updateControls();
        applySelection(FontFiles.directory().resolve(name), face, "", false);
        loadFonts(false);
    }

    @Override
    public void tick() {
        super.tick();
        boolean busy = isBusy();
        if (busy != lastBusy) {
            lastBusy = busy;
            updateRows();
            updateFontSizeControls();
        }
    }

    @Override
    public void onFilesDrop(List<Path> paths) {
        if (!paths.isEmpty()) importFont(paths.get(0), 0);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        drawBackdrop(graphics, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 8, 0xFFFFFFFF);
        graphics.drawCenteredString(font, fontSizeLabel, width / 2, FONT_SIZE_Y + 5, 0xFFFFFFFF);
        graphics.drawCenteredString(font, status, width / 2, height - 72, 0xFFB8C0CB);
        int totalPages = Math.max(1, (filtered.size() + pageSize - 1) / pageSize);
        graphics.drawCenteredString(font, (page + 1) + " / " + totalPages, left + 85, height - 55, 0xFFB8C0CB);
        drawWidgets(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        catalogRequest++;
        importRequest++;
        minecraft.setScreen(previous);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record Selection(String font, int face, boolean reference, String name, String file) {
        void restore(UiSettings settings) {
            settings.selectedFont = font;
            settings.selectedFontFace = face;
            settings.systemFontReference = reference;
            settings.systemFontName = name;
            settings.systemFontFile = file;
        }
    }
}
