package ai.zevaro.analytics.consumer.events;

import java.time.Instant;
import java.util.UUID;

public record TicketCreatedEvent(
    UUID tenantId,
    UUID ticketId,
    UUID workstreamId,
    String type,
    String severity,
    UUID reportedById,
    Instant timestamp
) {}
