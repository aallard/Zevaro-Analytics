package ai.zevaro.analytics.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CoreDecisionSummary(
    UUID id,
    String title,
    String priority,
    String status,
    UUID ownerId,
    String ownerName,
    Instant createdAt,
    long blockedItemsCount
) {}
