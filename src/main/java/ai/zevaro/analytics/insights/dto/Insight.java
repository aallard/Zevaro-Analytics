package ai.zevaro.analytics.insights.dto;

import java.time.Instant;

public record Insight(
    InsightType type,
    String title,
    String description,
    String recommendation,
    double confidence,
    Instant generatedAt
) {}
