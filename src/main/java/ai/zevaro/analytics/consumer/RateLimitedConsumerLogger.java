package ai.zevaro.analytics.consumer;

import org.slf4j.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Shared rate-limited logging utility for Kafka consumers.
 *
 * PROBLEM: When the database goes down or a producer replays thousands of duplicate
 * events, each consumer logs per-message errors/warnings. With 4 consumers × 3 retries
 * × thousands of queued messages, this generates hundreds of thousands of log lines
 * with full stack traces — enough to fill 200GB of disk.
 *
 * SOLUTION: Each consumer creates one instance per error category (db errors, duplicates).
 * The first occurrence is logged immediately. Subsequent occurrences within a 5-minute
 * window are silently counted. At the end of each window, a single summary line reports
 * how many events were suppressed.
 *
 * Thread-safe via atomics (no locks).
 */
public class RateLimitedConsumerLogger {

    private final AtomicReference<Instant> lastLogTime = new AtomicReference<>(Instant.EPOCH);
    private final AtomicInteger suppressedCount = new AtomicInteger(0);

    private static final Duration LOG_INTERVAL = Duration.ofMinutes(5);

    /**
     * Log a warning if enough time has passed since the last log.
     * Otherwise, silently increment the suppressed counter.
     *
     * @param logger  the SLF4J logger to write to
     * @param message message template — must contain two {} placeholders:
     *                first for latestId, second for suppressed count
     * @param latestId the most recent event/entity ID that triggered this error
     */
    public void warnRateLimited(Logger logger, String message, Object latestId) {
        Instant now = Instant.now();
        Instant lastLog = lastLogTime.get();

        if (Duration.between(lastLog, now).compareTo(LOG_INTERVAL) > 0) {
            // Window expired — emit a summary and reset
            int suppressed = suppressedCount.getAndSet(0);
            logger.warn(message, latestId, suppressed);
            lastLogTime.set(now);
        } else {
            suppressedCount.incrementAndGet();
        }
    }

    /**
     * Log an error if enough time has passed since the last log.
     * Otherwise, silently increment the suppressed counter.
     */
    public void errorRateLimited(Logger logger, String message, Object latestId) {
        Instant now = Instant.now();
        Instant lastLog = lastLogTime.get();

        if (Duration.between(lastLog, now).compareTo(LOG_INTERVAL) > 0) {
            int suppressed = suppressedCount.getAndSet(0);
            logger.error(message, latestId, suppressed);
            lastLogTime.set(now);
        } else {
            suppressedCount.incrementAndGet();
        }
    }
}
