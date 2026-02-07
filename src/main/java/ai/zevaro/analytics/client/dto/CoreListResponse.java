package ai.zevaro.analytics.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CoreListResponse<T>(
    List<T> content,
    int totalElements
) {}
