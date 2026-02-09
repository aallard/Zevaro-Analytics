package ai.zevaro.analytics.metrics;

import ai.zevaro.analytics.config.AppConstants;
import ai.zevaro.analytics.repository.AnalyticsEventRepository;
import ai.zevaro.analytics.repository.DecisionCycleLog;
import ai.zevaro.analytics.repository.DecisionCycleLogRepository;
import ai.zevaro.analytics.repository.MetricSnapshot;
import ai.zevaro.analytics.repository.MetricSnapshotRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MetricsService Unit Tests")
class MetricsServiceTest {

    @Mock
    private MetricSnapshotRepository snapshotRepository;

    @Mock
    private DecisionCycleLogRepository cycleLogRepository;

    @Mock
    private AnalyticsEventRepository analyticsEventRepository;

    @InjectMocks
    private MetricsService metricsService;

    private static final UUID TEST_TENANT_ID = UUID.randomUUID();
    private static final UUID TEST_PROJECT_ID = UUID.randomUUID();
    private static final UUID TEST_DECISION_ID = UUID.randomUUID();
    private static final UUID TEST_OUTCOME_ID = UUID.randomUUID();
    private static final UUID TEST_HYPOTHESIS_ID = UUID.randomUUID();
    private static final UUID TEST_STAKEHOLDER_ID = UUID.randomUUID();

    @Test
    @DisplayName("recordDecisionResolved should save DecisionCycleLog with correct cycle time")
    void testRecordDecisionResolved_SavesCycleLog() {
        // Arrange
        var createdAt = Instant.now().minusSeconds(7200); // 2 hours ago
        var resolvedAt = Instant.now();
        var priority = "HIGH";
        var decisionType = "STRATEGIC";
        var wasEscalated = false;

        var expectedCycleTimeHours = Duration.between(createdAt, resolvedAt).toMinutes() / 60.0;
        var expectedBigDecimal = BigDecimal.valueOf(expectedCycleTimeHours)
            .setScale(2, RoundingMode.HALF_UP);

        when(cycleLogRepository.findByTenantIdAndResolvedAtBetween(any(UUID.class), any(Instant.class), any(Instant.class)))
            .thenReturn(List.of());

        // Act
        metricsService.recordDecisionResolved(
            TEST_TENANT_ID,
            TEST_PROJECT_ID,
            TEST_DECISION_ID,
            createdAt,
            resolvedAt,
            priority,
            decisionType,
            wasEscalated,
            TEST_STAKEHOLDER_ID
        );

        // Assert
        var captor = ArgumentCaptor.forClass(DecisionCycleLog.class);
        verify(cycleLogRepository).save(captor.capture());
        var savedLog = captor.getValue();

        assertThat(savedLog.getTenantId()).isEqualTo(TEST_TENANT_ID);
        assertThat(savedLog.getDecisionId()).isEqualTo(TEST_DECISION_ID);
        assertThat(savedLog.getCreatedAt()).isEqualTo(createdAt);
        assertThat(savedLog.getResolvedAt()).isEqualTo(resolvedAt);
        assertThat(savedLog.getCycleTimeHours()).isEqualByComparingTo(expectedBigDecimal);
        assertThat(savedLog.getPriority()).isEqualTo(priority);
        assertThat(savedLog.getDecisionType()).isEqualTo(decisionType);
        assertThat(savedLog.getWasEscalated()).isEqualTo(wasEscalated);
        assertThat(savedLog.getStakeholderId()).isEqualTo(TEST_STAKEHOLDER_ID);
    }

    @Test
    @DisplayName("recordDecisionResolved should update daily snapshot with correct metrics")
    void testRecordDecisionResolved_UpdatesDailySnapshot() {
        // Arrange
        var createdAt = Instant.now().minusSeconds(3600);
        var resolvedAt = Instant.now();
        var today = resolvedAt.atZone(ZoneOffset.UTC).toLocalDate();

        var existingLog1 = DecisionCycleLog.builder()
            .cycleTimeHours(BigDecimal.valueOf(2.50))
            .wasEscalated(false)
            .build();
        var existingLog2 = DecisionCycleLog.builder()
            .cycleTimeHours(BigDecimal.valueOf(3.50))
            .wasEscalated(true)
            .build();

        when(cycleLogRepository.findByTenantIdAndResolvedAtBetween(any(UUID.class), any(Instant.class), any(Instant.class)))
            .thenReturn(List.of(existingLog1, existingLog2));
        when(snapshotRepository.findByTenantIdAndMetricTypeAndMetricDate(any(UUID.class), anyString(), any(LocalDate.class)))
            .thenReturn(Optional.empty());

        // Act
        metricsService.recordDecisionResolved(
            TEST_TENANT_ID,
            TEST_PROJECT_ID,
            TEST_DECISION_ID,
            createdAt,
            resolvedAt,
            "MEDIUM",
            "OPERATIONAL",
            true,
            TEST_STAKEHOLDER_ID
        );

        // Assert
        var snapshotCaptor = ArgumentCaptor.forClass(MetricSnapshot.class);
        verify(snapshotRepository, times(1)).save(snapshotCaptor.capture());

        var allSnapshots = snapshotCaptor.getAllValues();
        var dailySnapshot = allSnapshots.stream()
            .filter(s -> AppConstants.METRIC_DECISION_VELOCITY.equals(s.getMetricType()))
            .findFirst()
            .orElse(null);

        assertThat(dailySnapshot).isNotNull();
        assertThat(dailySnapshot.getTenantId()).isEqualTo(TEST_TENANT_ID);
        assertThat(dailySnapshot.getMetricType()).isEqualTo(AppConstants.METRIC_DECISION_VELOCITY);
        assertThat(dailySnapshot.getMetricDate()).isEqualTo(today);
        assertThat(dailySnapshot.getValue()).isEqualByComparingTo(BigDecimal.valueOf(3.00));
        assertThat(dailySnapshot.getDimensions()).containsEntry("escalatedCount", 1L);
        assertThat(dailySnapshot.getDimensions()).containsEntry("decisionsResolved", 2);
    }

    @Test
    @DisplayName("recordOutcomeValidated should create new MetricSnapshot for OUTCOME_VELOCITY")
    void testRecordOutcomeValidated_CreatesNewSnapshot() {
        // Arrange
        var createdAt = Instant.now().minusSeconds(86400);
        var validatedAt = Instant.now();
        var today = validatedAt.atZone(ZoneOffset.UTC).toLocalDate();

        when(snapshotRepository.findByTenantIdAndMetricTypeAndMetricDate(
            TEST_TENANT_ID, AppConstants.METRIC_OUTCOME_VELOCITY, today))
            .thenReturn(Optional.empty());

        // Act
        metricsService.recordOutcomeValidated(TEST_TENANT_ID, TEST_PROJECT_ID, TEST_OUTCOME_ID, createdAt, validatedAt);

        // Assert
        var captor = ArgumentCaptor.forClass(MetricSnapshot.class);
        verify(snapshotRepository).save(captor.capture());
        var savedSnapshot = captor.getValue();

        assertThat(savedSnapshot.getTenantId()).isEqualTo(TEST_TENANT_ID);
        assertThat(savedSnapshot.getMetricType()).isEqualTo(AppConstants.METRIC_OUTCOME_VELOCITY);
        assertThat(savedSnapshot.getMetricDate()).isEqualTo(today);
        assertThat(savedSnapshot.getValue()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(savedSnapshot.getDimensions()).isEmpty();
    }

    @Test
    @DisplayName("recordOutcomeValidated should increment existing OUTCOME_VELOCITY snapshot")
    void testRecordOutcomeValidated_IncrementsExistingSnapshot() {
        // Arrange
        var createdAt = Instant.now().minusSeconds(86400);
        var validatedAt = Instant.now();
        var today = validatedAt.atZone(ZoneOffset.UTC).toLocalDate();

        var existingSnapshot = MetricSnapshot.builder()
            .id(UUID.randomUUID())
            .tenantId(TEST_TENANT_ID)
            .metricType(AppConstants.METRIC_OUTCOME_VELOCITY)
            .metricDate(today)
            .value(BigDecimal.valueOf(5))
            .dimensions(Map.of())
            .build();

        when(snapshotRepository.findByTenantIdAndMetricTypeAndMetricDate(
            TEST_TENANT_ID, AppConstants.METRIC_OUTCOME_VELOCITY, today))
            .thenReturn(Optional.of(existingSnapshot));

        // Act
        metricsService.recordOutcomeValidated(TEST_TENANT_ID, TEST_PROJECT_ID, TEST_OUTCOME_ID, createdAt, validatedAt);

        // Assert
        var captor = ArgumentCaptor.forClass(MetricSnapshot.class);
        verify(snapshotRepository).save(captor.capture());
        var savedSnapshot = captor.getValue();

        assertThat(savedSnapshot.getValue()).isEqualByComparingTo(BigDecimal.valueOf(6));
        assertThat(savedSnapshot.getTenantId()).isEqualTo(TEST_TENANT_ID);
    }

    @Test
    @DisplayName("recordOutcomeInvalidated should track invalidation in dimensions")
    void testRecordOutcomeInvalidated_TracksInvalidationInDimensions() {
        // Arrange
        var createdAt = Instant.now().minusSeconds(86400);
        var invalidatedAt = Instant.now();
        var today = invalidatedAt.atZone(ZoneOffset.UTC).toLocalDate();

        var existingSnapshot = MetricSnapshot.builder()
            .id(UUID.randomUUID())
            .tenantId(TEST_TENANT_ID)
            .metricType(AppConstants.METRIC_OUTCOME_VELOCITY)
            .metricDate(today)
            .value(BigDecimal.valueOf(3))
            .dimensions(Map.of("invalidated", 1, "validated", 2))
            .build();

        when(snapshotRepository.findByTenantIdAndMetricTypeAndMetricDate(
            TEST_TENANT_ID, AppConstants.METRIC_OUTCOME_VELOCITY, today))
            .thenReturn(Optional.of(existingSnapshot));

        // Act
        metricsService.recordOutcomeInvalidated(TEST_TENANT_ID, TEST_PROJECT_ID, TEST_OUTCOME_ID, createdAt, invalidatedAt);

        // Assert
        var captor = ArgumentCaptor.forClass(MetricSnapshot.class);
        verify(snapshotRepository).save(captor.capture());
        var savedSnapshot = captor.getValue();

        assertThat(savedSnapshot.getDimensions()).containsEntry("invalidated", 2);
        assertThat(savedSnapshot.getDimensions()).containsEntry("validated", 2);
        assertThat(savedSnapshot.getTenantId()).isEqualTo(TEST_TENANT_ID);
    }

    @Test
    @DisplayName("recordOutcomeInvalidated should create new snapshot when none exists")
    void testRecordOutcomeInvalidated_CreatesNewSnapshot() {
        // Arrange
        var createdAt = Instant.now().minusSeconds(86400);
        var invalidatedAt = Instant.now();
        var today = invalidatedAt.atZone(ZoneOffset.UTC).toLocalDate();

        when(snapshotRepository.findByTenantIdAndMetricTypeAndMetricDate(
            TEST_TENANT_ID, AppConstants.METRIC_OUTCOME_VELOCITY, today))
            .thenReturn(Optional.empty());

        // Act
        metricsService.recordOutcomeInvalidated(TEST_TENANT_ID, TEST_PROJECT_ID, TEST_OUTCOME_ID, createdAt, invalidatedAt);

        // Assert
        var captor = ArgumentCaptor.forClass(MetricSnapshot.class);
        verify(snapshotRepository).save(captor.capture());
        var savedSnapshot = captor.getValue();

        assertThat(savedSnapshot.getTenantId()).isEqualTo(TEST_TENANT_ID);
        assertThat(savedSnapshot.getMetricType()).isEqualTo(AppConstants.METRIC_OUTCOME_VELOCITY);
        assertThat(savedSnapshot.getValue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(savedSnapshot.getDimensions()).containsEntry("invalidated", 1);
        assertThat(savedSnapshot.getDimensions()).containsEntry("validated", 0);
    }

    @Test
    @DisplayName("recordHypothesisConcluded should create new HYPOTHESIS_THROUGHPUT snapshot")
    void testRecordHypothesisConcluded_CreatesNewSnapshot() {
        // Arrange
        var createdAt = Instant.now().minusSeconds(172800);
        var concludedAt = Instant.now();
        var today = concludedAt.atZone(ZoneOffset.UTC).toLocalDate();
        var result = "VALIDATED";

        when(snapshotRepository.findByTenantIdAndMetricTypeAndMetricDate(
            TEST_TENANT_ID, AppConstants.METRIC_HYPOTHESIS_THROUGHPUT, today))
            .thenReturn(Optional.empty());

        // Act
        metricsService.recordHypothesisConcluded(
            TEST_TENANT_ID,
            TEST_PROJECT_ID,
            TEST_HYPOTHESIS_ID,
            TEST_OUTCOME_ID,
            result,
            createdAt,
            concludedAt
        );

        // Assert
        var captor = ArgumentCaptor.forClass(MetricSnapshot.class);
        verify(snapshotRepository).save(captor.capture());
        var savedSnapshot = captor.getValue();

        assertThat(savedSnapshot.getTenantId()).isEqualTo(TEST_TENANT_ID);
        assertThat(savedSnapshot.getMetricType()).isEqualTo(AppConstants.METRIC_HYPOTHESIS_THROUGHPUT);
        assertThat(savedSnapshot.getMetricDate()).isEqualTo(today);
        assertThat(savedSnapshot.getValue()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(savedSnapshot.getDimensions()).containsEntry("validated", 1);
        assertThat(savedSnapshot.getDimensions()).containsEntry("invalidated", 0);
    }

    @Test
    @DisplayName("recordHypothesisConcluded should increment and update dimensions for VALIDATED")
    void testRecordHypothesisConcluded_IncrementsAndUpdatesDimensionsValidated() {
        // Arrange
        var createdAt = Instant.now().minusSeconds(172800);
        var concludedAt = Instant.now();
        var today = concludedAt.atZone(ZoneOffset.UTC).toLocalDate();
        var result = "VALIDATED";

        var existingSnapshot = MetricSnapshot.builder()
            .id(UUID.randomUUID())
            .tenantId(TEST_TENANT_ID)
            .metricType(AppConstants.METRIC_HYPOTHESIS_THROUGHPUT)
            .metricDate(today)
            .value(BigDecimal.valueOf(3))
            .dimensions(new HashMap<>(Map.of("validated", 2, "invalidated", 1)))
            .build();

        when(snapshotRepository.findByTenantIdAndMetricTypeAndMetricDate(
            TEST_TENANT_ID, AppConstants.METRIC_HYPOTHESIS_THROUGHPUT, today))
            .thenReturn(Optional.of(existingSnapshot));

        // Act
        metricsService.recordHypothesisConcluded(
            TEST_TENANT_ID,
            TEST_PROJECT_ID,
            TEST_HYPOTHESIS_ID,
            TEST_OUTCOME_ID,
            result,
            createdAt,
            concludedAt
        );

        // Assert
        var captor = ArgumentCaptor.forClass(MetricSnapshot.class);
        verify(snapshotRepository).save(captor.capture());
        var savedSnapshot = captor.getValue();

        assertThat(savedSnapshot.getValue()).isEqualByComparingTo(BigDecimal.valueOf(4));
        assertThat(savedSnapshot.getDimensions()).containsEntry("validated", 3);
        assertThat(savedSnapshot.getDimensions()).containsEntry("invalidated", 1);
    }

    @Test
    @DisplayName("recordHypothesisConcluded should increment and update dimensions for INVALIDATED")
    void testRecordHypothesisConcluded_IncrementsAndUpdatesDimensionsInvalidated() {
        // Arrange
        var createdAt = Instant.now().minusSeconds(172800);
        var concludedAt = Instant.now();
        var today = concludedAt.atZone(ZoneOffset.UTC).toLocalDate();
        var result = "INVALIDATED";

        var existingSnapshot = MetricSnapshot.builder()
            .id(UUID.randomUUID())
            .tenantId(TEST_TENANT_ID)
            .metricType(AppConstants.METRIC_HYPOTHESIS_THROUGHPUT)
            .metricDate(today)
            .value(BigDecimal.valueOf(2))
            .dimensions(new HashMap<>(Map.of("validated", 1, "invalidated", 1)))
            .build();

        when(snapshotRepository.findByTenantIdAndMetricTypeAndMetricDate(
            TEST_TENANT_ID, AppConstants.METRIC_HYPOTHESIS_THROUGHPUT, today))
            .thenReturn(Optional.of(existingSnapshot));

        // Act
        metricsService.recordHypothesisConcluded(
            TEST_TENANT_ID,
            TEST_PROJECT_ID,
            TEST_HYPOTHESIS_ID,
            TEST_OUTCOME_ID,
            result,
            createdAt,
            concludedAt
        );

        // Assert
        var captor = ArgumentCaptor.forClass(MetricSnapshot.class);
        verify(snapshotRepository).save(captor.capture());
        var savedSnapshot = captor.getValue();

        assertThat(savedSnapshot.getValue()).isEqualByComparingTo(BigDecimal.valueOf(3));
        assertThat(savedSnapshot.getDimensions()).containsEntry("validated", 1);
        assertThat(savedSnapshot.getDimensions()).containsEntry("invalidated", 2);
    }

    @Test
    @DisplayName("recordDecisionResolved should handle escalated decisions")
    void testRecordDecisionResolved_HandlesEscalatedDecisions() {
        // Arrange
        var createdAt = Instant.now().minusSeconds(7200);
        var resolvedAt = Instant.now();
        var wasEscalated = true;

        var existingLog = DecisionCycleLog.builder()
            .cycleTimeHours(BigDecimal.valueOf(1.50))
            .wasEscalated(true)
            .build();

        when(cycleLogRepository.findByTenantIdAndResolvedAtBetween(any(UUID.class), any(Instant.class), any(Instant.class)))
            .thenReturn(List.of(existingLog));
        when(snapshotRepository.findByTenantIdAndMetricTypeAndMetricDate(any(UUID.class), anyString(), any(LocalDate.class)))
            .thenReturn(Optional.empty());

        // Act
        metricsService.recordDecisionResolved(
            TEST_TENANT_ID,
            TEST_PROJECT_ID,
            TEST_DECISION_ID,
            createdAt,
            resolvedAt,
            "CRITICAL",
            "ESCALATED",
            wasEscalated,
            TEST_STAKEHOLDER_ID
        );

        // Assert
        var cycleLogCaptor = ArgumentCaptor.forClass(DecisionCycleLog.class);
        verify(cycleLogRepository).save(cycleLogCaptor.capture());
        var savedLog = cycleLogCaptor.getValue();

        assertThat(savedLog.getWasEscalated()).isTrue();

        var snapshotCaptor = ArgumentCaptor.forClass(MetricSnapshot.class);
        verify(snapshotRepository, times(1)).save(snapshotCaptor.capture());

        var dailySnapshot = snapshotCaptor.getAllValues().stream()
            .filter(s -> AppConstants.METRIC_DECISION_VELOCITY.equals(s.getMetricType()))
            .findFirst()
            .orElse(null);

        assertThat(dailySnapshot).isNotNull();
        assertThat(dailySnapshot.getDimensions()).containsEntry("escalatedCount", 1L);
    }
}
