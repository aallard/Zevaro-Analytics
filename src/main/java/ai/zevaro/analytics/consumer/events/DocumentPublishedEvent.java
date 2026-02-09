package ai.zevaro.analytics.consumer.events;

import java.time.Instant;
import java.util.UUID;

public record DocumentPublishedEvent(
    UUID tenantId,
    UUID documentId,
    UUID spaceId,
    String title,
    int version,
    UUID publishedById,
    Instant timestamp
) {}
