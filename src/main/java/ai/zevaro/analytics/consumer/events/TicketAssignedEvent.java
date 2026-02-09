package ai.zevaro.analytics.consumer.events;

import java.time.Instant;
import java.util.UUID;

public record TicketAssignedEvent(
    UUID tenantId,
    UUID ticketId,
    UUID assignedToId,
    UUID assignedById,
    Instant timestamp
) {}
