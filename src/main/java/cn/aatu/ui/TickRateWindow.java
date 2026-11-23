package cn.aatu.ui;

/** Server-thread sampler; publishes at most one immutable snapshot per second. */
public final class TickRateWindow {
    private static final int MAX_INTERVALS = 100;
    private static final long SAMPLE_INTERVAL = 1_000_000_000L;
    private final long[] tickTimes = new long[MAX_INTERVALS + 1];
    private int count;
    private int next;
    private long lastPublication;
    private long startedAt;
    private long sequence;
    private double lastTarget;
    private volatile Snapshot snapshot = new Snapshot(-1, 20, false, 0, 0);

    public record Snapshot(double tps, double target, boolean paused, long sequence, long measuredAt) {}

    public Snapshot snapshot() {
        return snapshot;
    }

    public void clear() {
        count = 0;
        next = 0;
        lastPublication = 0;
        snapshot = new Snapshot(-1, 20, false, ++sequence, 0);
    }

    public void tick(long now, double target, boolean paused) {
        if (!Double.isFinite(target) || target <= 0) target = 20;
        if (paused) {
            count = 0;
            next = 0;
            if (!snapshot.paused || now - lastPublication >= SAMPLE_INTERVAL) {
                lastPublication = now;
                snapshot = new Snapshot(0, target, true, ++sequence, now);
            }
            return;
        }
        if (snapshot.paused || lastTarget != target) {
            count = 0;
            next = 0;
            lastPublication = 0;
            lastTarget = target;
        }
        if (count == 0) startedAt = now;
        tickTimes[next] = now;
        next = (next + 1) % tickTimes.length;
        count = Math.min(count + 1, tickTimes.length);
        if (lastPublication != 0 && now - lastPublication < SAMPLE_INTERVAL) return;
        long oldest = tickTimes[count == tickTimes.length ? next : 0];
        long elapsed = now - oldest;
        double tps = count > 1 && elapsed > 0 && now - startedAt >= SAMPLE_INTERVAL
                ? Math.min(target, (count - 1) * 1_000_000_000.0 / elapsed) : -1;
        lastPublication = now;
        snapshot = new Snapshot(tps, target, false, ++sequence, now);
    }
}
