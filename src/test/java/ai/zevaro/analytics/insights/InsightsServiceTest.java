package ai.zevaro.analytics.insights;

import ai.zevaro.analytics.config.AppConstants;
import ai.zevaro.analytics.insights.dto.Insight;
import ai.zevaro.analytics.insights.dto.InsightType;
import ai.zevaro.analytics.insights.dto.Trend;
import ai.zevaro.analytics.insights.dto.TrendDirection;
import ai.zevaro.analytics.repository.DecisionCycleLog;
import ai.zevaro.analytics.repository.DecisionCycleLogRepository;
import ai.zevaro.analytics.repository.MetricSnapshot;
import ai.zevaro.analytics.repository.MetricSnapshotRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("InsightsService Unit Tests")
class InsightsServiceTest {

    @Mock
    private MetricSnapshotRepository snapshotRepository;

    @Mock
    private DecisionCycleLogRepository cycleLogRepository;

    @InjectMocks
    private InsightsService insightsService;

    private static final UUID TEST_TENANT_ID = UUID.randomUUID();

    @Test
    @DisplayName("generateInsights should return empty list when no data exists")
    void testGenerateInsights_WithNoData_ReturnsEmptyList() {
        // Arrange
        var endDate = LocalDate.now();
        var midDate = endDate.minusDays(15);
        var startDate = endDate.minusDays(30);

        when(snapshotRepository.findByTenantIdAndMetricTypeAndMetricDateBetweenOrderByMetricDateAsc(
            eq(TEST_TENANT_ID), eq(AppConstants.METRIC_DECISION_VELOCITY), eq(startDate), any(LocalDate.class)))
            .thenReturn(List.of());

        when(snapshotRepository.findByTenantIdAndMetricTypeAndMetricDateBetweenOrderByMetricDateAsc(
            eq(TEST_TENANT_ID), eq(AppConstants.METRIC_DECISION_VELOCITY), eq(midDate), eq(endDate)))
            .thenReturn(List.of());

        when(cycleLogRepository.findAvgCycleTimeByStakeholder(eq(TEST_TENANT_ID), any(Instant.class)))
            .thenReturn(List.of());

        var sevenDaysAgo = Instant.now().minus(7, ChronoUnit.DAYS);
        when(cycleLogRepository.findByTenantIdAndResolvedAtBetween(
            eq(TEST_TENANT_ID), any(Instant.class), any(Instant.class)))
            .thenReturn(List.of());

        // Act
        var insights = insightsService.generateInsights(TEST_TENANT_ID);

        // Assert
        assertThat(insights).isEmpty();
    }

    @Test
    @DisplayName("generateInsights should include trend insight when trend is significant")
    void testGenerateInsights_WithSignificantTrend_IncludesTrendInsight() {
        // Arrange
        var endDate = LocalDate.now();
        var midDate = endDate.minusDays(15);
        var startDate = endDate.minusDays(30);

        var firstHalfSnapshot1 = MetricSnapshot.builder()
            .metricType(AppConstants.METRIC_DECISION_VELOCITY)
            .value(BigDecimal.valueOf(20.0))
            .build();
        var firstHalfSnapshot2 = MetricSnapshot.builder()
            .metricType(AppConstants.METRIC_DECISION_VELOCITY)
            .value(BigDecimal.valueOf(22.0))
            .build();

        // Second half has significant increase (>10% change)
        var secondHalfSnapshot1 = MetricSnapshot.builder()
            .metricType(AppConstants.METRIC_DECISION_VELOCITY)
            .value(BigDecimal.valueOf(35.0))
            .build();
        var secondHalfSnapshot2 = MetricSnapshot.builder()
            .metricType(AppConstants.METRIC_DECISION_VELOCITY)
            .value(BigDecimal.valueOf(38.0))
            .build();

        when(snapshotRepository.findByTenantIdAndMetricTypeAndMetricDateBetweenOrderByMetricDateAsc(
            eq(TEST_TENANT_ID), eq(AppConstants.METRIC_DECISION_VELOCITY), eq(startDate), eq(midDate)))
            .thenReturn(List.of(firstHalfSnapshot1, firstHalfSnapshot2));

        when(snapshotRepository.findByTenantIdAndMetricTypeAndMetricDateBetweenOrderByMetricDateAsc(
            eq(TEST_TENANT_ID), eq(AppConstants.METRIC_DECISION_VELOCITY), eq(midDate), eq(endDate)))
            .thenReturn(List.of(secondHalfSnapshot1, secondHalfSnapshot2));

        when(cycleLogRepository.findAvgCycleTimeByStakeholder(eq(TEST_TENANT_ID), any(Instant.class)))
            .thenReturn(List.of());

        when(cycleLogRepository.findByTenantIdAndResolvedAtBetween(
            eq(TEST_TENANT_ID), any(Instant.class), any(Instant.class)))
            .thenReturn(List.of());

        // Act
        var insights = insightsService.generateInsights(TEST_TENANT_ID);

        // Assert
        assertThat(insights).isNotEmpty();
        var trendInsight = insights.stream()
            .filter(i -> i.type() == InsightType.TREND)
            .findFirst();
        assertThat(trendInsight).isPresent();
        assertThat(trendInsight.get().title()).contains("declining");
    }

    @Test
    @DisplayName("detectTrends should return 2 trends")
    void testDetectTrends_ReturnsExactlyTwoTrends() {
        // Arrange
        var endDate = LocalDate.now();
        var midDate = endDate.minusDays(15);
        var startDate = endDate.minusDays(30);

        var decisionSnapshot = MetricSnapshot.builder()
            .metricType(AppConstants.METRIC_DECISION_VELOCITY)
            .value(BigDecimal.valueOf(25.0))
            .build();

        var outcomeSnapshot = MetricSnapshot.builder()
            .metricType(AppConstants.METRIC_OUTCOME_VELOCITY)
            .value(BigDecimal.valueOf(10))
            .build();

        when(snapshotRepository.findByTenantIdAndMetricTypeAndMetricDateBetweenOrderByMetricDateAsc(
            eq(TEST_TENANT_ID), eq(AppConstants.METRIC_DECISION_VELOCITY), eq(startDate), any(LocalDate.class)))
            .thenReturn(List.of(decisionSnapshot));

        when(snapshotRepository.findByTenantIdAndMetricTypeAndMetricDateBetweenOrderByMetricDateAsc(
            eq(TEST_TENANT_ID), eq(AppConstants.METRIC_DECISION_VELOCITY), eq(midDate), eq(endDate)))
            .thenReturn(List.of(decisionSnapshot));

        when(snapshotRepository.findByTenantIdAndMetricTypeAndMetricDateBetweenOrderByMetricDateAsc(
            eq(TEST_TENANT_ID), eq(AppConstants.METRIC_OUTCOME_VELOCITY), eq(startDate), any(LocalDate.class)))
            .thenReturn(List.of(outcomeSnapshot));

        when(snapshotRepository.findByTenantIdAndMetricTypeAndMetricDateBetweenOrderByMetricDateAsc(
            eq(TEST_TENANT_ID), eq(AppConstants.METRIC_OUTCOME_VELOCITY), eq(midDate), eq(endDate)))
            .thenReturn(List.of(outcomeSnapshot));

        // Act
        var trends = insightsService.detectTrends(TEST_TENANT_ID);

        // Assert
        assertThat(trends).hasSize(2);
        assertThat(trends).extracting(Trend::metricName)
            .containsExactlyInAnyOrder("Decision Velocity", "Outcome Velocity");
    }

    @Test
    @DisplayName("detectTrends should identify UP trend with positive change")
    void testDetectTrends_IdentifiesUpTrend() {
        // Arrange
        var endDate = LocalDate.now();
        var midDate = endDate.minusDays(15);
        var startDate = endDate.minusDays(30);

        var firstHalf = MetricSnapshot.builder()
            .value(BigDecimal.valueOf(10))
            .build();

        var secondHalf = MetricSnapshot.builder()
            .value(BigDecimal.valueOf(15))
            .build();

        when(snapshotRepository.findByTenantIdAndMetricTypeAndMetricDateBetweenOrderByMetricDateAsc(
            eq(TEST_TENANT_ID), eq(AppConstants.METRIC_DECISION_VELOCITY), eq(startDate), eq(midDate)))
            .thenReturn(List.of(firstHalf));

        when(snapshotRepository.findByTenantIdAndMetricTypeAndMetricDateBetweenOrderByMetricDateAsc(
            eq(TEST_TENANT_ID), eq(AppConstants.METRIC_DECISION_VELOCITY), eq(midDate), eq(endDate)))
            .thenReturn(List.of(secondHalf));

        when(snapshotRepository.findByTenantIdAndMetricTypeAndMetricDateBetweenOrderByMetricDateAsc(
            eq(TEST_TENANT_ID), eq(AppConstants.METRIC_OUTCOME_VELOCITY), any(LocalDate.class), any(LocalDate.class)))
            .thenReturn(List.of());

        // Act
        var trends = insightsService.detectTrends(TEST_TENANT_ID);

        // Assert
        var decisionTrend = trends.stream()
            .filter(t -> "Decision Velocity".equals(t.metricName()))
            .findFirst()
            .orElse(null);

        assertThat(decisionTrend).isNotNull();
        assertThat(decisionTrend.direction()).isEqualTo(TrendDirection.UP);
        assertThat(decisionTrend.percentChange()).isPositive();
    }

    @Test
    @DisplayName("detectTrends should identify DOWN trend with negative change")
    void testDetectTrends_IdentifiesDownTrend() {
        // Arrange
        var endDate = LocalDate.now();
        var midDate = endDate.minusDays(15);
        var startDate = endDate.minusDays(30);

        var firstHalf = MetricSnapshot.builder()
            .value(BigDecimal.valueOf(25))
            .build();

        var secondHalf = MetricSnapshot.builder()
            .value(BigDecimal.valueOf(15))
            .build();

        when(snapshotRepository.findByTenantIdAndMetricTypeAndMetricDateBetweenOrderByMetricDateAsc(
            eq(TEST_TENANT_ID), eq(AppConstants.METRIC_OUTCOME_VELOCITY), eq(startDate), eq(midDate)))
            .thenReturn(List.of(firstHalf));

        when(snapshotRepository.findByTenantIdAndMetricTypeAndMetricDateBetweenOrderByMetricDateAsc(
            eq(TEST_TENANT_ID), eq(AppConstants.METRIC_OUTCOME_VELOCITY), eq(midDate), eq(endDate)))
            .thenReturn(List.of(secondHalf));

        when(snapshotRepository.findByTenantIdAndMetricTypeAndMetricDateBetweenOrderByMetricDateAsc(
            eq(TEST_TENANT_ID), eq(AppConstants.METRIC_DECISION_VELOCITY), any(LocalDate.class), any(LocalDate.class)))
            .thenReturn(List.of());

        // Act
        var trends = insightsService.detectTrends(TEST_TENANT_ID);

        // Assert
        var outcomeTrend = trends.stream()
            .filter(t -> "Outcome Velocity".equals(t.metricName()))
            .findFirst()
            .orElse(null);

        assertThat(outcomeTrend).isNotNull();
        assertThat(outcomeTrend.direction()).isEqualTo(TrendDirection.DOWN);
        assertThat(outcomeTrend.percentChange()).isNegative();
    }

    @Test
    @DisplayName("getRecommendations should return default recommendations when no data")
    void testGetRecommendations_WithNoData_ReturnsDefaultRecommendations() {
        // Arrange
        var thirtyDaysAgo = Instant.now().minus(30, ChronoUnit.DAYS);

        when(cycleLogRepository.findAvgCycleTimeSince(TEST_TENANT_ID, thirtyDaysAgo))
            .thenReturn(null);

        when(cycleLogRepository.countEscalatedSince(TEST_TENANT_ID, thirtyDaysAgo))
            .thenReturn(0L);

        when(cycleLogRepository.findByTenantIdAndResolvedAtBetween(
            eq(TEST_TENANT_ID), any(Instant.class), any(Instant.class)))
            .thenReturn(List.of());

        // Act
        var recommendations = insightsService.getRecommendations(TEST_TENANT_ID);

        // Assert
        assertThat(recommendations).isNotEmpty();
        assertThat(recommendations).contains(
            "Continue monitoring decision velocity trends",
            "Consider setting up weekly digest reports for stakeholders"
        );
    }

    @Test
    @DisplayName("getRecommendations should recommend optimizing complex decisions when cycle time > 48h")
    void testGetRecommendations_HighCycleTime_RecommendOptimization() {
        // Arrange
        var thirtyDaysAgo = Instant.now().minus(30, ChronoUnit.DAYS);

        when(cycleLogRepository.findAvgCycleTimeSince(TEST_TENANT_ID, thirtyDaysAgo))
            .thenReturn(60.0); // 60 hours

        when(cycleLogRepository.countEscalatedSince(TEST_TENANT_ID, thirtyDaysAgo))
            .thenReturn(1L);

        when(cycleLogRepository.findByTenantIdAndResolvedAtBetween(
            eq(TEST_TENANT_ID), any(Instant.class), any(Instant.class)))
            .thenReturn(List.of(
                DecisionCycleLog.builder()
                    .cycleTimeHours(BigDecimal.valueOf(60.0))
                    .wasEscalated(false)
                    .build()
            ));

        // Act
        var recommendations = insightsService.getRecommendations(TEST_TENANT_ID);

        // Assert
        assertThat(recommendations).contains(
            "Consider breaking down complex decisions into smaller, time-boxed choices"
        );
    }

    @Test
    @DisplayName("getRecommendations should flag high escalation rate")
    void testGetRecommendations_HighEscalationRate_FlagsIssue() {
        // Arrange
        var thirtyDaysAgo = Instant.now().minus(30, ChronoUnit.DAYS);

        var logs = List.of(
            DecisionCycleLog.builder()
                .cycleTimeHours(BigDecimal.valueOf(10.0))
                .wasEscalated(true)
                .build(),
            DecisionCycleLog.builder()
                .cycleTimeHours(BigDecimal.valueOf(12.0))
                .wasEscalated(true)
                .build(),
            DecisionCycleLog.builder()
                .cycleTimeHours(BigDecimal.valueOf(8.0))
                .wasEscalated(true)
                .build(),
            DecisionCycleLog.builder()
                .cycleTimeHours(BigDecimal.valueOf(9.0))
                .wasEscalated(false)
                .build(),
            DecisionCycleLog.builder()
                .cycleTimeHours(BigDecimal.valueOf(11.0))
                .wasEscalated(false)
                .build()
        );

        when(cycleLogRepository.findAvgCycleTimeSince(TEST_TENANT_ID, thirtyDaysAgo))
            .thenReturn(20.0);

        when(cycleLogRepository.countEscalatedSince(TEST_TENANT_ID, thirtyDaysAgo))
            .thenReturn(3L); // 3 out of 5 = 60%

        when(cycleLogRepository.findByTenantIdAndResolvedAtBetween(
            eq(TEST_TENANT_ID), any(Instant.class), any(Instant.class)))
            .thenReturn(logs);

        // Act
        var recommendations = insightsService.getRecommendations(TEST_TENANT_ID);

        // Assert
        assertThat(recommendations).contains(
            "High escalation rate detected. Review stakeholder availability and SLA settings"
        );
    }

    @Test
    @DisplayName("getRecommendations should include multiple recommendations when conditions match")
    void testGetRecommendations_MultipleConditions_ReturnsMultipleRecommendations() {
        // Arrange
        var thirtyDaysAgo = Instant.now().minus(30, ChronoUnit.DAYS);

        var logs = List.of(
            DecisionCycleLog.builder()
                .cycleTimeHours(BigDecimal.valueOf(50.0))
                .wasEscalated(true)
                .build(),
            DecisionCycleLog.builder()
                .cycleTimeHours(BigDecimal.valueOf(55.0))
                .wasEscalated(true)
                .build(),
            DecisionCycleLog.builder()
                .cycleTimeHours(BigDecimal.valueOf(48.0))
                .wasEscalated(true)
                .build(),
            DecisionCycleLog.builder()
                .cycleTimeHours(BigDecimal.valueOf(45.0))
                .wasEscalated(false)
                .build(),
            DecisionCycleLog.builder()
                .cycleTimeHours(BigDecimal.valueOf(52.0))
                .wasEscalated(false)
                .build()
        );

        when(cycleLogRepository.findAvgCycleTimeSince(TEST_TENANT_ID, thirtyDaysAgo))
            .thenReturn(50.0); // > 48h

        when(cycleLogRepository.countEscalatedSince(TEST_TENANT_ID, thirtyDaysAgo))
            .thenReturn(3L); // 60% escalation rate

        when(cycleLogRepository.findByTenantIdAndResolvedAtBetween(
            eq(TEST_TENANT_ID), any(Instant.class), any(Instant.class)))
            .thenReturn(logs);

        // Act
        var recommendations = insightsService.getRecommendations(TEST_TENANT_ID);

        // Assert
        assertThat(recommendations)
            .contains("Consider breaking down complex decisions into smaller, time-boxed choices")
            .contains("High escalation rate detected. Review stakeholder availability and SLA settings");
    }

    @Test
    @DisplayName("detectTrends should identify STABLE trend with small change")
    void testDetectTrends_SmallChange_IdentifiesStableTrend() {
        // Arrange
        var endDate = LocalDate.now();
        var midDate = endDate.minusDays(15);
        var startDate = endDate.minusDays(30);

        var firstHalf = MetricSnapshot.builder()
            .value(BigDecimal.valueOf(20.0))
            .build();

        var secondHalf = MetricSnapshot.builder()
            .value(BigDecimal.valueOf(21.0))
            .build();

        when(snapshotRepository.findByTenantIdAndMetricTypeAndMetricDateBetweenOrderByMetricDateAsc(
            eq(TEST_TENANT_ID), eq(AppConstants.METRIC_DECISION_VELOCITY), eq(startDate), eq(midDate)))
            .thenReturn(List.of(firstHalf));

        when(snapshotRepository.findByTenantIdAndMetricTypeAndMetricDateBetweenOrderByMetricDateAsc(
            eq(TEST_TENANT_ID), eq(AppConstants.METRIC_DECISION_VELOCITY), eq(midDate), eq(endDate)))
            .thenReturn(List.of(secondHalf));

        when(snapshotRepository.findByTenantIdAndMetricTypeAndMetricDateBetweenOrderByMetricDateAsc(
            eq(TEST_TENANT_ID), eq(AppConstants.METRIC_OUTCOME_VELOCITY), any(LocalDate.class), any(LocalDate.class)))
            .thenReturn(List.of());

        // Act
        var trends = insightsService.detectTrends(TEST_TENANT_ID);

        // Assert
        var decisionTrend = trends.stream()
            .filter(t -> "Decision Velocity".equals(t.metricName()))
            .findFirst()
            .orElse(null);

        assertThat(decisionTrend).isNotNull();
        assertThat(decisionTrend.direction()).isEqualTo(TrendDirection.STABLE);
    }
}
