package cn.aatu.ui;

import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class ServerPerformance {
    private static final long STALE_NANOS = 10_000_000_000L;
    private static final TickRateWindow LOCAL = new TickRateWindow();
    private static final AtomicBoolean LOCAL_PAUSED = new AtomicBoolean();
    private static volatile MinecraftServer localServer;
    private static Connection connection;
    private static long remoteSequence = Long.MIN_VALUE;
    private static long remoteReceivedAt;

    public enum State { READY, WAITING, UNAVAILABLE, PAUSED }
    public record Reading(State state, double tps, double target) {}

    private ServerPerformance() {}

    public static void serverTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.isDedicatedServer()) return;
        if (localServer != server || LOCAL_PAUSED.getAndSet(false)) LOCAL.clear();
        localServer = server;
        LOCAL.tick(System.nanoTime(), server.tickRateManager().tickrate(), server.tickRateManager().isFrozen());
    }

    public static void serverStopped(ServerStoppedEvent event) {
        LOCAL.clear();
        localServer = null;
        LOCAL_PAUSED.set(false);
    }

    public static void clientTick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.hasSingleplayerServer() && minecraft.isPaused()) LOCAL_PAUSED.set(true);
    }

    public static void clear() {
        connection = null;
        remoteSequence = Long.MIN_VALUE;
        remoteReceivedAt = 0;
    }

    public static Reading current() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.hasSingleplayerServer()) {
            if (minecraft.isPaused()) return new Reading(State.PAUSED, 0, 20);
            if (localServer != minecraft.getSingleplayerServer()) return new Reading(State.WAITING, 0, 20);
            TickRateWindow.Snapshot sample = LOCAL.snapshot();
            if (sample.measuredAt() != 0 && System.nanoTime() - sample.measuredAt() > STALE_NANOS) {
                return new Reading(State.UNAVAILABLE, 0, sample.target());
            }
            return new Reading(sample.paused() ? State.PAUSED : sample.tps() < 0 ? State.WAITING : State.READY,
                    sample.tps(), sample.target());
        }
        var listener = minecraft.getConnection();
        if (listener == null || !CombatUiLink.hasChannel()) return new Reading(State.UNAVAILABLE, 0, 20);
        if (connection != listener.getConnection()) {
            clear();
            connection = listener.getConnection();
        }
        CompoundTag data = CombatUiLink.state();
        if (data == null) return new Reading(State.WAITING, 0, 20);
        if (!data.contains("tpsSequence", Tag.TAG_LONG) || !data.contains("tps", Tag.TAG_DOUBLE)
                || !data.contains("tickTarget", Tag.TAG_DOUBLE)) return new Reading(State.UNAVAILABLE, 0, 20);
        if (!data.contains("_uiReceivedAt", Tag.TAG_LONG)
                || System.nanoTime() - data.getLong("_uiReceivedAt") > STALE_NANOS) {
            return new Reading(State.UNAVAILABLE, 0, 20);
        }
        long sequence = data.getLong("tpsSequence");
        if (sequence != remoteSequence) {
            remoteSequence = sequence;
            remoteReceivedAt = System.nanoTime();
        }
        double tps = data.getDouble("tps");
        double target = data.getDouble("tickTarget");
        if (!Double.isFinite(tps) || !Double.isFinite(target) || target <= 0 || tps < -1 || tps > target
                || System.nanoTime() - remoteReceivedAt > STALE_NANOS) return new Reading(State.UNAVAILABLE, 0, 20);
        return new Reading(data.getBoolean("ticksPaused") ? State.PAUSED : tps < 0 ? State.WAITING : State.READY, tps, target);
    }
}
