package ai.zevaro.analytics.metrics;

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
                Map.of(),  // byPriority - could be expanded
                Map.of()   // byType - could be expanded
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
            .map(row -> new StakeholderResponseMetric(
                (UUID) row[0],
                null,  // Name would need to be fetched from Core
                ((Number) row[1]).doubleValue(),
                0,  // pending - would need live data from Core
                0,  // completed - could count from logs
                0.0,
                LocalDate.now().minusDays(days),
                LocalDate.now()
            ))
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

        var dataPoints = snapshots.stream()
            .map(s -> Map.of(
                "date", s.getMetricDate().toString(),
                "count", s.getValue().intValue()
            ))
            .toList();

        return ResponseEntity.ok(Map.of(
            "totalValidated", totalValidated,
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

        // This would integrate with deployment tracking
        // For now, return structure
        return ResponseEntity.ok(Map.of(
            "idleTimeMinutes", 0,
            "lastDecisionResolved", Instant.now(),
            "pendingDecisions", 0,
            "status", "UNKNOWN"
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
