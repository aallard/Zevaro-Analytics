package ai.zevaro.analytics.config;

public final class AppConstants {
    private AppConstants() {}

    public static final String API_V1 = "/api/v1";

    // Kafka topics
    public static final String TOPIC_DECISION_RESOLVED = "zevaro.core.decision.resolved";
    public static final String TOPIC_OUTCOME_VALIDATED = "zevaro.core.outcome.validated";
    public static final String TOPIC_OUTCOME_INVALIDATED = "zevaro.core.outcome.invalidated";
    public static final String TOPIC_HYPOTHESIS_CONCLUDED = "zevaro.core.hypothesis.concluded";

    // Cache names
    public static final String CACHE_DASHBOARD = "dashboard";
    public static final String CACHE_METRICS = "metrics";
    public static final String CACHE_REPORTS = "reports";

    // Metric types
    public static final String METRIC_DECISION_VELOCITY = "DECISION_VELOCITY";
    public static final String METRIC_OUTCOME_VELOCITY = "OUTCOME_VELOCITY";
    public static final String METRIC_STAKEHOLDER_RESPONSE = "STAKEHOLDER_RESPONSE";
    public static final String METRIC_HYPOTHESIS_THROUGHPUT = "HYPOTHESIS_THROUGHPUT";
}
