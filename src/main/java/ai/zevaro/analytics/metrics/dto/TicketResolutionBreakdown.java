package ai.zevaro.analytics.metrics.dto;

import java.util.Map;

public record TicketResolutionBreakdown(
    Map<String, Integer> byResolution,
    Map<String, Integer> byType,
    Map<String, Integer> bySeverity
) {}
