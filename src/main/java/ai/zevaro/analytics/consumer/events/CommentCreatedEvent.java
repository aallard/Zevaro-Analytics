package ai.zevaro.analytics.consumer.events;

import java.time.Instant;
import java.util.UUID;

public record CommentCreatedEvent(
    UUID tenantId,
    UUID commentId,
    String parentType,
    UUID parentId,
    UUID authorId,
    Instant timestamp
) {}
