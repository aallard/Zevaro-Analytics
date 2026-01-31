package ai.zevaro.analytics.reports.dto;

import java.time.Instant;

public record TimelineEvent(
    Instant timestamp,
    String eventType,
    String description
) {}
