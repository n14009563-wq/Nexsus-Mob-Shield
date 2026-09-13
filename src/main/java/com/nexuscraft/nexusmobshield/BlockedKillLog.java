package com.nexuscraft.nexusmobshield;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * What got blocked, kept in memory only (deliberately -- see README: whatever is doing this
 * can fire fast enough that writing one log line to disk per blocked kill would just trade one
 * performance problem for another). A capped recent-entries ring buffer for /nexusmobshield log,
 * plus running counters that the plugin's periodic summary task drains for one aggregated
 * report instead of one message per kill.
 */
public final class BlockedKillLog {

    private static final int MAX_RECENT = 200;
    private static final DateTimeFormatter DISPLAY =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    public static final class Entry {
        public final long atMillis;
        public final String entityType;
        public final String targetName;
        public final String world;
        public final int x;
        public final int y;
        public final int z;

        Entry(String entityType, String targetName, String world, int x, int y, int z) {
            this.atMillis = System.currentTimeMillis();
            this.entityType = entityType;
            this.targetName = targetName;
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public String format() {
            return "[" + DISPLAY.format(Instant.ofEpochMilli(atMillis)) + "] " + entityType
                    + (targetName != null ? " (" + targetName + ")" : "")
                    + " at " + world + " " + x + "," + y + "," + z;
        }
    }

    private final Deque<Entry> recent = new ArrayDeque<>();
    private final AtomicLong totalBlocked = new AtomicLong();
    private final AtomicLong sinceLastSummary = new AtomicLong();
    private final AtomicLong totalRecovered = new AtomicLong();
    private final AtomicLong recoveredSinceLastSummary = new AtomicLong();

    public synchronized void record(String entityType, String targetName, String world, int x, int y, int z) {
        recent.addLast(new Entry(entityType, targetName, world, x, y, z));
        while (recent.size() > MAX_RECENT) {
            recent.removeFirst();
        }
        totalBlocked.incrementAndGet();
        sinceLastSummary.incrementAndGet();
    }

    public synchronized List<Entry> recent(int n) {
        List<Entry> all = new ArrayList<>(recent);
        int from = Math.max(0, all.size() - n);
        return all.subList(from, all.size());
    }

    public long totalBlocked() {
        return totalBlocked.get();
    }

    /** Reads and resets the since-last-summary counter -- call this once per summary interval. */
    public long drainSinceLastSummary() {
        return sinceLastSummary.getAndSet(0);
    }

    /**
     * Records that a protected, non-exempt mob died anyway (its last damage cause was the KILL
     * we tried to cancel) and got a same-type replacement spawned back in as a safety net. This
     * only ever fires when the primary cancellation didn't hold, so a non-zero count here is a
     * useful diagnostic on its own.
     */
    public void recordRecovery() {
        totalRecovered.incrementAndGet();
        recoveredSinceLastSummary.incrementAndGet();
    }

    public long totalRecovered() {
        return totalRecovered.get();
    }

    /** Reads and resets the since-last-summary recovery counter. */
    public long drainRecoveredSinceLastSummary() {
        return recoveredSinceLastSummary.getAndSet(0);
    }
}
