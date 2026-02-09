package ai.zevaro.analytics.consumer.events;

import java.time.Instant;
import java.util.UUID;

public record TicketResolvedEvent(
    UUID tenantId,
    UUID ticketId,
    String resolution,
    UUID resolvedById,
    Instant timestamp
) {}
