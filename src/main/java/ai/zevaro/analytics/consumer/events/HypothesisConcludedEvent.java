package ai.zevaro.analytics.consumer.events;

import java.time.Instant;
import java.util.UUID;

public record HypothesisConcludedEvent(
    UUID tenantId,
    UUID hypothesisId,
    UUID outcomeId,
    String result,  // VALIDATED or INVALIDATED
    Instant createdAt,
    Instant concludedAt
) {}
