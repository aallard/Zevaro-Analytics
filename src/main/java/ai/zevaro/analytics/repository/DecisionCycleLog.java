package ai.zevaro.analytics.repository;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "decision_cycle_log", schema = "analytics",
    indexes = {
        @Index(name = "idx_cycle_tenant_resolved", columnList = "tenant_id, resolved_at"),
        @Index(name = "idx_cycle_stakeholder", columnList = "stakeholder_id")
    })
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class DecisionCycleLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "decision_id", nullable = false)
    private UUID decisionId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "resolved_at", nullable = false)
    private Instant resolvedAt;

    @Column(name = "cycle_time_hours", nullable = false, precision = 10, scale = 2)
    private BigDecimal cycleTimeHours;

    @Column(name = "priority", length = 20)
    private String priority;

    @Column(name = "decision_type", length = 50)
    private String decisionType;

    @Column(name = "was_escalated")
    @Builder.Default
    private Boolean wasEscalated = false;

    @Column(name = "stakeholder_id")
    private UUID stakeholderId;
}
