package ai.zevaro.analytics.internal;

import ai.zevaro.analytics.config.AppConstants;
import ai.zevaro.analytics.metrics.MetricsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Internal endpoints for Core service to push metrics directly (alternative to Kafka).
 */
@RestController
@RequestMapping(AppConstants.API_V1 + "/internal/metrics")
@RequiredArgsConstructor
public class InternalMetricsController {

    private final MetricsService metricsService;

    @PostMapping("/decision-resolved")
    public ResponseEntity<Map<String, String>> recordDecisionResolved(
            @RequestBody DecisionResolvedRequest request) {

        metricsService.recordDecisionResolved(
            request.tenantId(),
            request.decisionId(),
            request.createdAt(),
            request.resolvedAt(),
            request.priority(),
            request.decisionType(),
            request.wasEscalated(),
            request.stakeholderId()
        );

        return ResponseEntity.ok(Map.of("status", "recorded"));
    }

    @PostMapping("/outcome-validated")
    public ResponseEntity<Map<String, String>> recordOutcomeValidated(
            @RequestBody OutcomeValidatedRequest request) {

        metricsService.recordOutcomeValidated(
            request.tenantId(),
            request.outcomeId(),
            request.createdAt(),
            request.validatedAt()
        );

        return ResponseEntity.ok(Map.of("status", "recorded"));
    }

    @PostMapping("/hypothesis-concluded")
    public ResponseEntity<Map<String, String>> recordHypothesisConcluded(
            @RequestBody HypothesisConcludedRequest request) {

        metricsService.recordHypothesisConcluded(
            request.tenantId(),
            request.hypothesisId(),
            request.outcomeId(),
            request.result(),
            request.createdAt(),
            request.concludedAt()
        );

        return ResponseEntity.ok(Map.of("status", "recorded"));
    }

    // Request DTOs
    public record DecisionResolvedRequest(
        UUID tenantId,
        UUID decisionId,
        Instant createdAt,
        Instant resolvedAt,
        String priority,
        String decisionType,
        boolean wasEscalated,
        UUID stakeholderId
    ) {}

    public record OutcomeValidatedRequest(
        UUID tenantId,
        UUID outcomeId,
        Instant createdAt,
        Instant validatedAt
    ) {}

    public record HypothesisConcludedRequest(
        UUID tenantId,
        UUID hypothesisId,
        UUID outcomeId,
        String result,
        Instant createdAt,
        Instant concludedAt
    ) {}
}
