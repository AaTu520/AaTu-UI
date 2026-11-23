package cn.aatu.ui;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.ScreenEvent;

public final class DamageRecapClient {
    private static final int MAX_HITS = 64;
    private static final List<CompoundTag> RECENT = new ArrayList<>();
    private static List<CompoundTag> incident = List.of();
    private static boolean available;
    private static int reason;
    private static long incidentVersion;
    private static long incidentTick;
    private static long notifiedIncident;
    private static long lastId;

    private DamageRecapClient() {}

    static void receive(Connection connection, CompoundTag packet) {
        var listener = Minecraft.getInstance().getConnection();
        if (listener == null || listener.getConnection() != connection || packet.getInt("protocol") != 1) return;
        available = true;
        reason = packet.getInt("reason");
        incidentVersion = packet.getLong("incidentVersion");
        incidentTick = packet.getLong("incidentTick");
        ListTag entries = packet.getList("entries", Tag.TAG_COMPOUND);
        for (int i = 0; i < Math.min(entries.size(), MAX_HITS); i++) {
            CompoundTag entry = entries.getCompound(i);
            if (entry.getLong("id") <= lastId) continue;
            RECENT.add(entry.copy());
            lastId = entry.getLong("id");
        }
        while (RECENT.size() > MAX_HITS) RECENT.removeFirst();
        if (packet.contains("incident", Tag.TAG_LIST)) {
            ListTag frozen = packet.getList("incident", Tag.TAG_COMPOUND);
            List<CompoundTag> copy = new ArrayList<>();
            for (int i = 0; i < Math.min(frozen.size(), MAX_HITS); i++) copy.add(frozen.getCompound(i).copy());
            incident = List.copyOf(copy);
        }
    }

    static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) return;
        if (!UiSettings.get().autoDamageRecap) {
            notifiedIncident = incidentVersion;
            DamageRecapHud.clear();
            return;
        }
        if (incidentVersion <= notifiedIncident) return;
        notifiedIncident = incidentVersion;
        if (minecraft.screen instanceof DamageRecapScreen recap) {
            recap.showIncident();
            DamageRecapHud.clear();
        } else {
            // A notification must never replace a screen or capture input while the player is crawling.
            DamageRecapHud.show(incident, reason);
        }
    }

    static void screenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof DeathScreen death)) return;
        event.addListener(Button.builder(Component.translatable("aatu_ui.recap.title"), button -> {
            DamageRecapHud.clear();
            Minecraft.getInstance().setScreen(new DamageRecapScreen(death, true));
        }).bounds(6, 6, 88, 20).build());
    }

    public static boolean available() { return available; }
    public static boolean hasIncident() { return incidentVersion > 0; }
    public static int reason() { return reason; }
    public static long incidentTick() { return incidentTick; }
    public static List<CompoundTag> entries(boolean frozen) { return frozen ? incident : List.copyOf(RECENT); }

    static void clear() {
        RECENT.clear();
        incident = List.of();
        available = false;
        reason = 0;
        incidentVersion = incidentTick = notifiedIncident = lastId = 0;
        DamageRecapHud.clear();
    }
}
