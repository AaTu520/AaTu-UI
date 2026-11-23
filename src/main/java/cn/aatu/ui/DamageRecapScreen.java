package cn.aatu.ui;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

public final class DamageRecapScreen extends CrispScreen {
    private static final int ROW_HEIGHT = 38;
    private static final int LIST_TOP = 78;
    private static final int COPY_FEEDBACK_TICKS = 40;
    private static final int NARROW_FOOTER_WIDTH = 280;
    private final Screen previous;
    private boolean frozen;
    private int page;
    private int left;
    private int panelWidth;
    private int pageSize;
    private Button previousPage;
    private Button nextPage;
    private Button modeButton;
    private Button copyButton;
    private int copyFeedbackTicks;

    public DamageRecapScreen(Screen previous, boolean frozen) {
        super(Component.translatable("aatu_ui.recap.title"));
        this.previous = previous;
        this.frozen = frozen;
    }

    public void showIncident() {
        frozen = true;
        page = 0;
        resetCopyFeedback();
    }

    @Override
    protected void init() {
        panelWidth = Math.max(180, Math.min(620, width - 16));
        left = (width - panelWidth) / 2;
        boolean narrowFooter = panelWidth < NARROW_FOOTER_WIDTH;
        int footerTop = height - (narrowFooter ? 52 : 28);
        pageSize = Math.max(1, (footerTop - LIST_TOP - 6) / ROW_HEIGHT);
        modeButton = addRenderableWidget(Button.builder(modeLabel(), button -> {
            frozen = !frozen;
            page = 0;
            resetCopyFeedback();
            button.setMessage(modeLabel());
        }).bounds(left, 28, panelWidth / 2 - 2, 20).build());
        addRenderableWidget(Button.builder(autoLabel(), button -> {
            UiSettings.get().autoDamageRecap = !UiSettings.get().autoDamageRecap;
            if (!UiSettings.get().autoDamageRecap) DamageRecapHud.clear();
            UiSettings.get().save();
            button.setMessage(autoLabel());
        }).bounds(left + panelWidth / 2 + 2, 28, panelWidth / 2 - 2, 20).build());
        addRenderableWidget(Button.builder(cornerLabel(), button -> {
            UiSettings settings = UiSettings.get();
            settings.damageRecapCorner = settings.recapCorner().next().id;
            settings.save();
            button.setMessage(cornerLabel());
        }).bounds(left, 52, panelWidth / 2 - 2, 20).build());
        addRenderableWidget(new DurationSlider(left + panelWidth / 2 + 2, 52, panelWidth / 2 - 2));
        previousPage = addRenderableWidget(Button.builder(Component.literal("<"), button -> page = Math.max(0, page - 1))
                .bounds(left, height - 28, 24, 20).build());
        nextPage = addRenderableWidget(Button.builder(Component.literal(">"), button -> page++)
                .bounds(left + 28, height - 28, 24, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), button -> onClose())
                .bounds(left + panelWidth - 80, height - 28, 80, 20).build());
        copyButton = addRenderableWidget(Button.builder(Component.translatable("aatu_ui.recap.copy"), button -> copyReport())
                .tooltip(Tooltip.create(Component.translatable("aatu_ui.recap.copy_tip")))
                .bounds(left + panelWidth - (narrowFooter ? 80 : 164), height - (narrowFooter ? 52 : 28), 80, 20).build());
        copyButton.active = DamageRecapClient.available() && !DamageRecapClient.entries(frozen).isEmpty();
        copyFeedbackTicks = 0;
        modeButton.setTooltip(Tooltip.create(Component.translatable("aatu_ui.recap.window")));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        drawBackdrop(graphics, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 10, 0xFFFFFFFF);
        List<CompoundTag> entries = displayedEntries();
        int pages = Math.max(1, (entries.size() + pageSize - 1) / pageSize);
        page = Math.min(page, pages - 1);
        previousPage.active = page > 0;
        nextPage.active = page + 1 < pages;
        copyButton.active = DamageRecapClient.available() && !entries.isEmpty();
        modeButton.setMessage(modeLabel());
        if (!DamageRecapClient.available()) {
            drawMessage(graphics, "aatu_ui.recap.unavailable");
        } else if (entries.isEmpty()) {
            drawMessage(graphics, frozen ? "aatu_ui.recap.no_incident" : "aatu_ui.recap.empty");
        } else {
            long referenceTick = frozen ? DamageRecapClient.incidentTick() : entries.getFirst().getLong("tick");
            float maximum = 0;
            for (CompoundTag entry : entries) {
                float damage = entry.getFloat("damage");
                if (!Float.isNaN(damage)) maximum = Math.max(maximum, damage);
            }
            List<Component> hovered = null;
            for (int row = 0; row < pageSize; row++) {
                int index = page * pageSize + row;
                if (index >= entries.size()) break;
                CompoundTag entry = entries.get(index);
                int y = LIST_TOP + row * ROW_HEIGHT;
                boolean hover = mouseX >= left && mouseX < left + panelWidth && mouseY >= y && mouseY < y + ROW_HEIGHT - 2;
                drawEntry(graphics, entry, referenceTick, maximum, y, hover);
                if (hover) hovered = details(entry);
            }
            drawWidgets(graphics, mouseX, mouseY, partialTick);
            graphics.drawString(font, (page + 1) + " / " + pages, left + 60, height - 22, 0xFFD1D8E4);
            if (hovered != null) graphics.renderComponentTooltip(font, hovered, mouseX, mouseY);
            return;
        }
        drawWidgets(graphics, mouseX, mouseY, partialTick);
    }

    private List<CompoundTag> displayedEntries() {
        List<CompoundTag> entries = new ArrayList<>(DamageRecapClient.entries(frozen));
        Collections.reverse(entries);
        return entries;
    }

    private void copyReport() {
        List<CompoundTag> entries = displayedEntries();
        if (!DamageRecapClient.available() || entries.isEmpty()) return;
        try {
            minecraft.keyboardHandler.setClipboard(formatReport(entries));
            copyButton.setMessage(Component.translatable("aatu_ui.recap.copied"));
        } catch (RuntimeException exception) {
            LogUtils.getLogger().warn("Unable to copy damage recap", exception);
            copyButton.setMessage(Component.translatable("aatu_ui.recap.copy_failed"));
        }
        copyFeedbackTicks = COPY_FEEDBACK_TICKS;
    }

    private String formatReport(List<CompoundTag> entries) {
        StringBuilder report = new StringBuilder();
        report.append(title.getString()).append(" · ").append(modeLabel().getString()).append('\n');
        appendReportLine(report, "aatu_ui.recap.copy_count", entries.size());
        appendReportLine(report, frozen ? "aatu_ui.recap.copy_time_incident" : "aatu_ui.recap.copy_time_recent");
        long referenceTick = frozen ? DamageRecapClient.incidentTick() : entries.getFirst().getLong("tick");
        for (int index = 0; index < entries.size(); index++) {
            CompoundTag entry = entries.get(index);
            report.append('\n').append('#').append(index + 1).append(' ')
                    .append(String.format(Locale.ROOT, "−%.2fs", Math.max(0, referenceTick - entry.getLong("tick")) / 20.0))
                    .append('\n');
            appendReportLine(report, "aatu_ui.recap.copy_attacker", attacker(entry).getString(), value(entry, "attackerType"));
            appendReportLine(report, "aatu_ui.recap.direct", value(entry, "directType"));
            appendReportLine(report, "aatu_ui.recap.source", value(entry, "source"));
            appendReportLine(report, "aatu_ui.recap.amounts", reportNumber(entry.getFloat("damage")),
                    reportNumber(entry.getFloat("loss")), reportNumber(entry.getFloat("input")));
            appendReportLine(report, "aatu_ui.recap.health", reportNumber(entry.getFloat("before")), reportNumber(entry.getFloat("after")));
            appendReportLine(report, "aatu_ui.recap.absorption", reportNumber(entry.getFloat("absorption")));
            appendReportLine(report, entry.getBoolean("bypass") ? "aatu_ui.recap.bypass" : "aatu_ui.recap.armored");
            if (entry.getBoolean("effect")) appendReportLine(report, "aatu_ui.recap.effect");
            if (entry.getBoolean("lethal")) appendReportLine(report, "aatu_ui.recap.lethal");
            if (entry.getBoolean("unmeasured")) appendReportLine(report, "aatu_ui.recap.unmeasured_detail");
            appendDiagnostics(report, entry);
        }
        return report.toString();
    }

    private static void appendDiagnostics(StringBuilder report, CompoundTag entry) {
        if (!entry.contains("diagnostics", Tag.TAG_COMPOUND)) {
            appendReportLine(report, "aatu_ui.recap.trace_missing");
            return;
        }
        CompoundTag trace = entry.getCompound("diagnostics");
        appendReportLine(report, "aatu_ui.recap.trace_attributes", diagnosticNumber(trace, "armor"), diagnosticNumber(trace, "toughness"));
        appendReportLine(report, trace.getBoolean("combatActive") ? "aatu_ui.recap.combat_active" : "aatu_ui.recap.combat_inactive");
        appendReportLine(report, "aatu_ui.recap.trace_armor_pool", diagnosticNumber(trace, "armorPoolBefore"),
                diagnosticNumber(trace, "armorPoolAfter"), diagnosticNumber(trace, "armorCapacityBefore"), diagnosticNumber(trace, "armorCapacityAfter"));
        appendReportLine(report, "aatu_ui.recap.trace_toughness_pool", diagnosticNumber(trace, "toughnessPoolBefore"),
                diagnosticNumber(trace, "toughnessPoolAfter"), diagnosticNumber(trace, "toughnessCapacityBefore"), diagnosticNumber(trace, "toughnessCapacityAfter"));
        appendReportLine(report, "aatu_ui.recap.trace_stages", diagnosticNumber(trace, "beforeArmor"), diagnosticNumber(trace, "afterArmor"),
                diagnosticNumber(trace, "afterMagic"), diagnosticNumber(trace, "afterDamageEvent"));
        if (trace.getBoolean("armorPreviewCalled")) {
            appendReportLine(report, "aatu_ui.recap.trace_formula", diagnosticNumber(trace, "armorUsed"), diagnosticNumber(trace, "toughnessUsed"),
                    diagnosticNumber(trace, "poolSpent"), diagnosticNumber(trace, "armorPreviewOutput"));
        } else {
            appendReportLine(report, "aatu_ui.recap.trace_no_formula");
        }
        appendReportLine(report, "aatu_ui.recap.trace_reductions", diagnosticNumber(trace, "armorReduction"),
                diagnosticNumber(trace, "enchantmentReduction"), diagnosticNumber(trace, "effectReduction"));
        ListTag changes = trace.getList("changes", Tag.TAG_COMPOUND);
        for (int index = 0; index < Math.min(8, changes.size()); index++) {
            CompoundTag change = changes.getCompound(index);
            appendReportLine(report, "aatu_ui.recap.trace_change", Component.translatable("aatu_ui.recap.phase." + change.getString("phase")).getString(),
                    diagnosticNumber(change, "before"), diagnosticNumber(change, "after"), value(change, "caller"));
        }
        if (trace.getInt("extraChanges") > 0) appendReportLine(report, "aatu_ui.recap.trace_more", trace.getInt("extraChanges"));
    }

    private static String diagnosticNumber(CompoundTag trace, String key) {
        if (!trace.contains(key, Tag.TAG_ANY_NUMERIC)) return "--";
        if (trace.contains(key, Tag.TAG_FLOAT)) return reportNumber(trace.getFloat(key));
        double value = trace.getDouble(key);
        return Double.isFinite(value) ? Double.toString(value) : number((float) value);
    }

    private static void appendReportLine(StringBuilder report, String key, Object... values) {
        String line = Component.translatable(key, values).getString();
        report.append(line.replace('\n', ' ').replace('\r', ' ')).append('\n');
    }

    private static String reportNumber(float value) {
        // Keep the received float precision so large hits can be analyzed without display rounding.
        return Float.isFinite(value) ? Float.toString(value) : number(value);
    }

    private void resetCopyFeedback() {
        copyFeedbackTicks = 0;
        if (copyButton != null) copyButton.setMessage(Component.translatable("aatu_ui.recap.copy"));
    }

    @Override
    public void tick() {
        super.tick();
        if (copyFeedbackTicks > 0 && --copyFeedbackTicks == 0) resetCopyFeedback();
    }

    private void drawEntry(GuiGraphics graphics, CompoundTag entry, long referenceTick, float maximum, int y, boolean hover) {
        graphics.fill(left, y, left + panelWidth, y + ROW_HEIGHT - 2, hover ? 0xE02A3548 : 0xD0131C29);
        float damage = entry.getFloat("damage");
        int accent = entry.getBoolean("lethal") ? 0xFFFF7185 : damage > 0 && damage == maximum ? 0xFFFFC46E : 0xFF83B7D9;
        graphics.fill(left, y, left + 2, y + ROW_HEIGHT - 2, accent);
        String time = String.format(Locale.ROOT, "−%.1fs", Math.max(0, referenceTick - entry.getLong("tick")) / 20.0);
        String attacker = attacker(entry).getString();
        graphics.drawString(font, font.plainSubstrByWidth(attacker, panelWidth - font.width(time) - 18), left + 6, y + 4, accent);
        graphics.drawString(font, time, left + panelWidth - font.width(time) - 5, y + 4, 0xFFADBACB);
        Component amounts = Component.translatable("aatu_ui.recap.amounts", number(damage), number(entry.getFloat("loss")), number(entry.getFloat("input")));
        graphics.drawString(font, font.plainSubstrByWidth(amounts.getString(), panelWidth - 12), left + 6, y + 15, 0xFFF1F4F8);
        String flags = Component.translatable(entry.getBoolean("bypass") ? "aatu_ui.recap.bypass" : "aatu_ui.recap.armored").getString();
        if (entry.getBoolean("effect")) flags += " · " + Component.translatable("aatu_ui.recap.effect").getString();
        if (entry.getBoolean("unmeasured")) flags += " · " + Component.translatable("aatu_ui.recap.unmeasured").getString();
        graphics.drawString(font, font.plainSubstrByWidth(flags + " · " + entry.getString("source"), panelWidth - 12),
                left + 6, y + 26, 0xFFADBACB);
    }

    private List<Component> details(CompoundTag entry) {
        List<Component> lines = new ArrayList<>();
        lines.add(attacker(entry));
        lines.add(Component.translatable("aatu_ui.recap.entity", value(entry, "attackerType")));
        lines.add(Component.translatable("aatu_ui.recap.direct", value(entry, "directType")));
        lines.add(Component.translatable("aatu_ui.recap.source", value(entry, "source")));
        lines.add(Component.translatable("aatu_ui.recap.health", number(entry.getFloat("before")), number(entry.getFloat("after"))));
        lines.add(Component.translatable("aatu_ui.recap.absorption", number(entry.getFloat("absorption"))));
        lines.add(Component.translatable("aatu_ui.recap.amounts", number(entry.getFloat("damage")),
                number(entry.getFloat("loss")), number(entry.getFloat("input"))));
        if (entry.contains("diagnostics", Tag.TAG_COMPOUND)) {
            CompoundTag trace = entry.getCompound("diagnostics");
            lines.add(Component.translatable("aatu_ui.recap.trace_attributes", number(trace.getFloat("armor")), number(trace.getFloat("toughness"))));
            lines.add(Component.translatable("aatu_ui.recap.trace_hover_stages", diagnosticNumber(trace, "afterArmor"), diagnosticNumber(trace, "afterDamageEvent")));
        }
        if (entry.getBoolean("unmeasured")) lines.add(Component.translatable("aatu_ui.recap.unmeasured_detail"));
        return lines;
    }

    static Component attacker(CompoundTag entry) {
        String custom = entry.getString("attackerName");
        if (!custom.isBlank()) return Component.literal(custom);
        String key = entry.getString("attackerKey");
        return Component.translatable(key.isBlank() ? "aatu_ui.recap.unknown" : key);
    }

    private static String value(CompoundTag entry, String key) {
        String value = entry.getString(key);
        return value.isBlank() ? "--" : value;
    }

    static String number(float value) {
        if (Float.isNaN(value)) return "--";
        if (Float.isInfinite(value)) return value > 0 ? "∞" : "−∞";
        return Math.abs(value) >= 1_000_000 ? String.format(Locale.ROOT, "%.3g", value) : String.format(Locale.ROOT, "%,.1f", value);
    }

    private Component modeLabel() {
        return Component.translatable(frozen ? (DamageRecapClient.reason() == 1 ? "aatu_ui.recap.down" : "aatu_ui.recap.incident")
                : "aatu_ui.recap.recent");
    }

    private Component autoLabel() {
        return Component.translatable(UiSettings.get().autoDamageRecap ? "aatu_ui.recap.auto_on" : "aatu_ui.recap.auto_off");
    }

    private Component cornerLabel() {
        return Component.translatable("aatu_ui.recap.position",
                Component.translatable("aatu_ui.recap.corner." + UiSettings.get().recapCorner().id));
    }

    private static final class DurationSlider extends AbstractSliderButton {
        DurationSlider(int x, int y, int width) {
            super(x, y, width, 20, Component.empty(),
                    (double) (UiSettings.validRecapSeconds(UiSettings.get().damageRecapSeconds) - UiSettings.MIN_RECAP_SECONDS)
                            / (UiSettings.MAX_RECAP_SECONDS - UiSettings.MIN_RECAP_SECONDS));
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.translatable("aatu_ui.recap.duration", UiSettings.get().damageRecapSeconds));
        }

        @Override
        protected void applyValue() {
            UiSettings.get().damageRecapSeconds = UiSettings.MIN_RECAP_SECONDS
                    + (int) Math.round(value * (UiSettings.MAX_RECAP_SECONDS - UiSettings.MIN_RECAP_SECONDS));
        }

        @Override
        public void onRelease(double mouseX, double mouseY) {
            super.onRelease(mouseX, mouseY);
            UiSettings.get().save();
        }
    }

    private void drawMessage(GuiGraphics graphics, String key) {
        int y = LIST_TOP + 8;
        for (var line : font.split(Component.translatable(key), panelWidth - 12)) {
            graphics.drawString(font, line, left + 6, y, 0xFFD1D8E4);
            y += font.lineHeight + 3;
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (vertical > 0 && previousPage.active) page--;
        else if (vertical < 0 && nextPage.active) page++;
        return true;
    }

    @Override
    public void onClose() {
        Screen destination = previous;
        if (destination instanceof DeathScreen && minecraft.player != null && !minecraft.player.isDeadOrDying()) destination = null;
        minecraft.setScreen(destination);
    }

    @Override
    public void removed() {
        // Keyboard slider changes are saved on exit without writing the config on every drag frame.
        UiSettings.get().save();
        super.removed();
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
