package ai.zevaro.analytics.config;

public final class AppConstants {
    private AppConstants() {}

    public static final String API_V1 = "/api/v1";

    // Kafka topics
    public static final String TOPIC_DECISION_RESOLVED = "zevaro.core.decision.resolved";
    public static final String TOPIC_OUTCOME_VALIDATED = "zevaro.core.outcome.validated";
    public static final String TOPIC_OUTCOME_INVALIDATED = "zevaro.core.outcome.invalidated";
    public static final String TOPIC_HYPOTHESIS_CONCLUDED = "zevaro.core.hypothesis.concluded";

    // Program topics
    public static final String TOPIC_PROGRAM_CREATED = "zevaro.core.program.created";
    public static final String TOPIC_PROGRAM_STATUS_CHANGED = "zevaro.core.program.status-changed";

    // Workstream topics
    public static final String TOPIC_WORKSTREAM_CREATED = "zevaro.core.workstream.created";
    public static final String TOPIC_WORKSTREAM_STATUS_CHANGED = "zevaro.core.workstream.status-changed";

    // Specification topics
    public static final String TOPIC_SPECIFICATION_CREATED = "zevaro.core.specification.created";
    public static final String TOPIC_SPECIFICATION_STATUS_CHANGED = "zevaro.core.specification.status-changed";
    public static final String TOPIC_SPECIFICATION_APPROVED = "zevaro.core.specification.approved";

    // Ticket topics
    public static final String TOPIC_TICKET_CREATED = "zevaro.core.ticket.created";
    public static final String TOPIC_TICKET_RESOLVED = "zevaro.core.ticket.resolved";
    public static final String TOPIC_TICKET_ASSIGNED = "zevaro.core.ticket.assigned";

    // Document topics
    public static final String TOPIC_DOCUMENT_PUBLISHED = "zevaro.core.document.published";

    // Comment topics
    public static final String TOPIC_COMMENT_CREATED = "zevaro.core.comment.created";

    // Cache names
    public static final String CACHE_DASHBOARD = "dashboard";
    public static final String CACHE_METRICS = "metrics";
    public static final String CACHE_REPORTS = "reports";

    // Metric types
    public static final String METRIC_DECISION_VELOCITY = "DECISION_VELOCITY";
    public static final String METRIC_OUTCOME_VELOCITY = "OUTCOME_VELOCITY";
    public static final String METRIC_STAKEHOLDER_RESPONSE = "STAKEHOLDER_RESPONSE";
    public static final String METRIC_HYPOTHESIS_THROUGHPUT = "HYPOTHESIS_THROUGHPUT";
    public static final String METRIC_SPECIFICATION_VELOCITY = "SPECIFICATION_VELOCITY";
    public static final String METRIC_TICKET_VELOCITY = "TICKET_VELOCITY";
    public static final String METRIC_PROGRAM_HEALTH = "PROGRAM_HEALTH";
    public static final String METRIC_WORKSTREAM_HEALTH = "WORKSTREAM_HEALTH";

    // Analytics event types
    public static final String EVENT_PROGRAM_CREATED = "PROGRAM_CREATED";
    public static final String EVENT_PROGRAM_STATUS_CHANGED = "PROGRAM_STATUS_CHANGED";
    public static final String EVENT_WORKSTREAM_CREATED = "WORKSTREAM_CREATED";
    public static final String EVENT_WORKSTREAM_STATUS_CHANGED = "WORKSTREAM_STATUS_CHANGED";
    public static final String EVENT_SPEC_CREATED = "SPEC_CREATED";
    public static final String EVENT_SPEC_STATUS_CHANGED = "SPEC_STATUS_CHANGED";
    public static final String EVENT_SPEC_APPROVED = "SPEC_APPROVED";
    public static final String EVENT_TICKET_CREATED = "TICKET_CREATED";
    public static final String EVENT_TICKET_RESOLVED = "TICKET_RESOLVED";
    public static final String EVENT_TICKET_ASSIGNED = "TICKET_ASSIGNED";
}
