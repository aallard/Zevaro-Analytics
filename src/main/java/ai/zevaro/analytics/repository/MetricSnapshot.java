package ai.zevaro.analytics.repository;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "metric_snapshots", schema = "analytics",
    uniqueConstraints = @UniqueConstraint(
        columnNames = {"tenant_id", "metric_type", "metric_date"}
    ),
    indexes = {
        @Index(name = "idx_metric_tenant_type_date",
               columnList = "tenant_id, metric_type, metric_date")
    })
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class MetricSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "project_id")
    private UUID projectId;

    @Column(name = "metric_type", nullable = false, length = 50)
    private String metricType;

    @Column(name = "metric_date", nullable = false)
    private LocalDate metricDate;

    @Column(name = "value", nullable = false, precision = 15, scale = 4)
    private BigDecimal value;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "dimensions", columnDefinition = "jsonb")
    private Map<String, Object> dimensions;

    @Column(name = "created_at")
    @Builder.Default
    private Instant createdAt = Instant.now();
}
