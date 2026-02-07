package ai.zevaro.analytics.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CorePageResponse(
    int totalElements,
    int totalPages,
    int size,
    int number
) {}
