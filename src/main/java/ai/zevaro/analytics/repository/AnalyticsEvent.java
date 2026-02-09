package ai.zevaro.analytics.repository;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "analytics_events", schema = "analytics",
    indexes = {
        @Index(name = "idx_ae_tenant_type_time",
               columnList = "tenant_id, event_type, event_timestamp"),
        @Index(name = "idx_ae_entity_type",
               columnList = "entity_id, event_type")
    })
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class AnalyticsEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    @Column(name = "parent_id")
    private UUID parentId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @Column(name = "event_timestamp", nullable = false)
    private Instant eventTimestamp;

    @Column(name = "recorded_at")
    @Builder.Default
    private Instant recordedAt = Instant.now();
}
