package ai.zevaro.analytics.insights.dto;

public record Trend(
    String metricName,
    TrendDirection direction,
    double percentChange,
    int periodDays,
    boolean isSignificant
) {}
