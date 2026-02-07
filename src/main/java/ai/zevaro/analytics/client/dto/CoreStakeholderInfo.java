package ai.zevaro.analytics.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CoreStakeholderInfo(
    UUID id,
    String name,
    String email,
    String role,
    int decisionsPending,
    int decisionsCompleted
) {}
