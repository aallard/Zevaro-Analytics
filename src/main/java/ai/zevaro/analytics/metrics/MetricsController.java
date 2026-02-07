package ai.zevaro.analytics.metrics;

import ai.zevaro.analytics.client.CoreServiceClient;
import ai.zevaro.analytics.config.AppConstants;
import ai.zevaro.analytics.metrics.dto.DecisionVelocityMetric;
import ai.zevaro.analytics.metrics.dto.HypothesisThroughputMetric;
import ai.zevaro.analytics.metrics.dto.StakeholderResponseMetric;
import ai.zevaro.analytics.repository.DecisionCycleLogRepository;
import ai.zevaro.analytics.repository.MetricSnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping(AppConstants.API_V1 + "/metrics")
@RequiredArgsConstructor
public class MetricsController {

    private final MetricSnapshotRepository snapshotRepository;
    private final DecisionCycleLogRepository cycleLogRepository;
    private final CoreServiceClient coreServiceClient;

    @GetMapping("/decision-velocity")
    @Cacheable(value = AppConstants.CACHE_METRICS, key = "'dv:' + #tenantId + ':' + #days")
    public ResponseEntity<List<DecisionVelocityMetric>> getDecisionVelocity(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam(defaultValue = "30") int days) {

        var endDate = LocalDate.now();
        var startDate = endDate.minusDays(days);

        var snapshots = snapshotRepository
            .findByTenantIdAndMetricTypeAndMetricDateBetweenOrderByMetricDateAsc(
                tenantId, AppConstants.METRIC_DECISION_VELOCITY, startDate, endDate);

        var metrics = snapshots.stream()
            .map(s -> new DecisionVelocityMetric(
                tenantId,
                s.getMetricDate(),
                s.getValue().doubleValue(),
                getIntDimension(s.getDimensions(), "decisionsCreated"),
                getIntDimension(s.getDimensions(), "decisionsResolved"),
                getIntDimension(s.getDimensions(), "escalatedCount"),
                getDoubleDimension(s.getDimensions(), "escalationRate"),
                Map.of(),  // byPriority - expandable per-priority breakdown
                Map.of()   // byType - expandable per-type breakdown
            ))
            .toList();

        return ResponseEntity.ok(metrics);
    }

    @GetMapping("/stakeholder-response")
    @Cacheable(value = AppConstants.CACHE_METRICS, key = "'sr:' + #tenantId + ':' + #days")
    public ResponseEntity<List<StakeholderResponseMetric>> getStakeholderResponse(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam(defaultValue = "30") int days) {

        var since = Instant.now().minus(java.time.Duration.ofDays(days));
        var results = cycleLogRepository.findAvgCycleTimeByStakeholder(tenantId, since);

        var metrics = results.stream()
            .map(row -> {
                var stakeholderId = (UUID) row[0];
                var avgResponseTime = ((Number) row[1]).doubleValue();

                // Enrich with stakeholder data from Core
                String name = null;
                int pending = 0;
                int completed = 0;

                var info = coreServiceClient.getStakeholder(tenantId, stakeholderId);
                if (info != null) {
                    name = info.name();
                    pending = info.decisionsPending();
                    completed = info.decisionsCompleted();
                }

                var escalationRate = completed > 0 && avgResponseTime > 48.0 ? 0.3
                    : completed > 0 && avgResponseTime > 24.0 ? 0.15
                    : 0.05;

                return new StakeholderResponseMetric(
                    stakeholderId,
                    name,
                    avgResponseTime,
                    pending,
                    completed,
                    escalationRate,
                    LocalDate.now().minusDays(days),
                    LocalDate.now()
                );
            })
            .toList();

        return ResponseEntity.ok(metrics);
    }

    @GetMapping("/outcome-velocity")
    @Cacheable(value = AppConstants.CACHE_METRICS, key = "'ov:' + #tenantId + ':' + #days")
    public ResponseEntity<Map<String, Object>> getOutcomeVelocity(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam(defaultValue = "30") int days) {

        var endDate = LocalDate.now();
        var startDate = endDate.minusDays(days);

        var snapshots = snapshotRepository
            .findByTenantIdAndMetricTypeAndMetricDateBetweenOrderByMetricDateAsc(
                tenantId, AppConstants.METRIC_OUTCOME_VELOCITY, startDate, endDate);

        var totalValidated = snapshots.stream()
            .mapToInt(s -> s.getValue().intValue())
            .sum();

        var totalInvalidated = snapshots.stream()
            .mapToInt(s -> getIntDimension(s.getDimensions(), "invalidated"))
            .sum();

        var dataPoints = snapshots.stream()
            .map(s -> Map.of(
                "date", s.getMetricDate().toString(),
                "validated", s.getValue().intValue(),
                "invalidated", getIntDimension(s.getDimensions(), "invalidated")
            ))
            .toList();

        return ResponseEntity.ok(Map.of(
            "totalValidated", totalValidated,
            "totalInvalidated", totalInvalidated,
            "periodDays", days,
            "dataPoints", dataPoints
        ));
    }

    @GetMapping("/hypothesis-throughput")
    @Cacheable(value = AppConstants.CACHE_METRICS, key = "'ht:' + #tenantId + ':' + #days")
    public ResponseEntity<List<HypothesisThroughputMetric>> getHypothesisThroughput(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam(defaultValue = "30") int days) {

        var endDate = LocalDate.now();
        var startDate = endDate.minusDays(days);

        var snapshots = snapshotRepository
            .findByTenantIdAndMetricTypeAndMetricDateBetweenOrderByMetricDateAsc(
                tenantId, AppConstants.METRIC_HYPOTHESIS_THROUGHPUT, startDate, endDate);

        var metrics = snapshots.stream()
            .map(s -> {
                int validated = getIntDimension(s.getDimensions(), "validated");
                int invalidated = getIntDimension(s.getDimensions(), "invalidated");
                int total = validated + invalidated;
                double rate = total > 0 ? (double) validated / total : 0.0;

                return new HypothesisThroughputMetric(
                    tenantId,
                    s.getMetricDate(),
                    total,
                    validated,
                    invalidated,
                    rate
                );
            })
            .toList();

        return ResponseEntity.ok(metrics);
    }

    @GetMapping("/pipeline-idle-time")
    @Cacheable(value = AppConstants.CACHE_METRICS, key = "'pit:' + #tenantId")
    public ResponseEntity<Map<String, Object>> getPipelineIdleTime(
            @RequestHeader("X-Tenant-Id") UUID tenantId) {

        var pendingDecisions = coreServiceClient.getPendingDecisionCount(tenantId);

        return ResponseEntity.ok(Map.of(
            "idleTimeMinutes", 0,  // Requires Elaro integration (ZI-009)
            "lastDecisionResolved", Instant.now(),
            "pendingDecisions", pendingDecisions,
            "status", pendingDecisions > 0 ? "WAITING_FOR_DECISIONS" : "READY"
        ));
    }

    private int getIntDimension(Map<String, Object> dimensions, String key) {
        if (dimensions == null || !dimensions.containsKey(key)) return 0;
        return ((Number) dimensions.get(key)).intValue();
    }

    private double getDoubleDimension(Map<String, Object> dimensions, String key) {
        if (dimensions == null || !dimensions.containsKey(key)) return 0.0;
        return ((Number) dimensions.get(key)).doubleValue();
    }
}
