package ai.zevaro.analytics.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CoreOutcomeInfo(
    UUID id,
    String title,
    String status,
    String successCriteria,
    Instant createdAt,
    Instant validatedAt,
    UUID ownerId
) {}
