package ai.zevaro.analytics.dashboard;

import ai.zevaro.analytics.client.CoreServiceClient;
import ai.zevaro.analytics.client.dto.CoreDecisionSummary;
import ai.zevaro.analytics.client.dto.CoreStakeholderInfo;
import ai.zevaro.analytics.config.AppConstants;
import ai.zevaro.analytics.dashboard.dto.DashboardData;
import ai.zevaro.analytics.repository.AnalyticsEventRepository;
import ai.zevaro.analytics.repository.DecisionCycleLog;
import ai.zevaro.analytics.repository.DecisionCycleLogRepository;
import ai.zevaro.analytics.repository.MetricSnapshot;
import ai.zevaro.analytics.repository.MetricSnapshotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DashboardService Unit Tests")
class DashboardServiceTest {

    @Mock
    private MetricSnapshotRepository snapshotRepository;

    @Mock
    private DecisionCycleLogRepository cycleLogRepository;

    @Mock
    private CoreServiceClient coreServiceClient;

    @Mock
    private AnalyticsEventRepository analyticsEventRepository;

    @InjectMocks
    private DashboardService dashboardService;

    private static final UUID TEST_TENANT_ID = UUID.randomUUID();
    private static final UUID TEST_STAKEHOLDER_ID_1 = UUID.randomUUID();
    private static final UUID TEST_STAKEHOLDER_ID_2 = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        // Default stub for analytics event queries — returns empty lists
        lenient().when(analyticsEventRepository.findByTenantIdAndEventTypeAndEventTimestampAfter(
            any(UUID.class), anyString(), any(Instant.class)))
            .thenReturn(List.of());
    }

    @Test
    @DisplayName("getDashboard should aggregate data from repositories and CoreServiceClient")
    void testGetDashboard_AggregatesDataCorrectly() {
        // Arrange
        var decisionVelocitySnapshot = MetricSnapshot.builder()
            .metricType(AppConstants.METRIC_DECISION_VELOCITY)
            .metricDate(LocalDate.now())
            .value(BigDecimal.valueOf(12.50))
            .build();

        var outcomeVelocitySnapshot = MetricSnapshot.builder()
            .metricType(AppConstants.METRIC_OUTCOME_VELOCITY)
            .metricDate(LocalDate.now())
            .value(BigDecimal.valueOf(8))
            .build();

        when(snapshotRepository.findByTenantIdAndMetricTypeAndMetricDateBetweenOrderByMetricDateAsc(
            eq(TEST_TENANT_ID), eq(AppConstants.METRIC_DECISION_VELOCITY), any(LocalDate.class), any(LocalDate.class)))
            .thenReturn(List.of(decisionVelocitySnapshot));

        when(snapshotRepository.findByTenantIdAndMetricTypeAndMetricDateBetweenOrderByMetricDateAsc(
            eq(TEST_TENANT_ID), eq(AppConstants.METRIC_OUTCOME_VELOCITY), any(LocalDate.class), any(LocalDate.class)))
            .thenReturn(List.of(outcomeVelocitySnapshot));

        when(cycleLogRepository.findAvgCycleTimeSince(eq(TEST_TENANT_ID), any(Instant.class)))
            .thenReturn(22.5);

        when(cycleLogRepository.countEscalatedSince(eq(TEST_TENANT_ID), any(Instant.class)))
            .thenReturn(3L);

        when(cycleLogRepository.findAvgCycleTimeByStakeholder(eq(TEST_TENANT_ID), any(Instant.class)))
            .thenReturn(List.of(
                new Object[]{TEST_STAKEHOLDER_ID_1, 15.0},
                new Object[]{TEST_STAKEHOLDER_ID_2, 30.0}
            ));

        // Mock Core service data
        when(coreServiceClient.getPendingDecisionCount(TEST_TENANT_ID))
            .thenReturn(5);

        when(coreServiceClient.getHypothesesTestedThisWeek(TEST_TENANT_ID))
            .thenReturn(3);

        when(coreServiceClient.getActiveHypothesisCount(TEST_TENANT_ID))
            .thenReturn(2);

        when(coreServiceClient.getUrgentDecisions(TEST_TENANT_ID))
            .thenReturn(List.of(
                new CoreDecisionSummary(
                    UUID.randomUUID(),
                    "Urgent Decision 1",
                    "BLOCKING",
                    "NEEDS_INPUT",
                    UUID.randomUUID(),
                    "John Doe",
                    Instant.now().minusSeconds(3600),
                    2
                )
            ));

        when(coreServiceClient.getStakeholder(TEST_TENANT_ID, TEST_STAKEHOLDER_ID_1))
            .thenReturn(new CoreStakeholderInfo(
                TEST_STAKEHOLDER_ID_1,
                "Alice Smith",
                "alice@example.com",
                "Manager",
                2,
                15
            ));

        when(coreServiceClient.getStakeholder(TEST_TENANT_ID, TEST_STAKEHOLDER_ID_2))
            .thenReturn(new CoreStakeholderInfo(
                TEST_STAKEHOLDER_ID_2,
                "Bob Johnson",
                "bob@example.com",
                "Analyst",
                1,
                10
            ));

        // Act
        var dashboard = dashboardService.getDashboard(TEST_TENANT_ID, null);

        // Assert
        assertThat(dashboard).isNotNull();
        assertThat(dashboard.decisionsPendingCount()).isEqualTo(5);
        assertThat(dashboard.avgDecisionWaitHours()).isEqualTo(22.5);
        assertThat(dashboard.outcomesValidatedThisWeek()).isEqualTo(8);
        assertThat(dashboard.hypothesesTestedThisWeek()).isEqualTo(3);
        assertThat(dashboard.experimentsRunning()).isEqualTo(2);
        assertThat(dashboard.decisionHealthStatus()).isEqualTo("GREEN");
        assertThat(dashboard.urgentDecisions()).hasSize(1);
        assertThat(dashboard.stakeholderLeaderboard()).hasSize(2);
        assertThat(dashboard.pipelineStatus()).isEqualTo("IDLE");
    }

    @Test
    @DisplayName("getDashboard should return GREEN health status when average cycle time < 24 hours")
    void testGetDashboard_ReturnsGreenHealthStatus() {
        // Arrange
        var snapshot = MetricSnapshot.builder()
            .metricType(AppConstants.METRIC_DECISION_VELOCITY)
            .metricDate(LocalDate.now())
            .value(BigDecimal.valueOf(12.5))
            .build();

        when(snapshotRepository.findByTenantIdAndMetricTypeAndMetricDateBetweenOrderByMetricDateAsc(
            any(UUID.class), anyString(), any(LocalDate.class), any(LocalDate.class)))
            .thenReturn(List.of(snapshot));

        when(cycleLogRepository.findAvgCycleTimeSince(eq(TEST_TENANT_ID), any(Instant.class)))
            .thenReturn(12.0); // Less than 24 hours

        when(cycleLogRepository.countEscalatedSince(eq(TEST_TENANT_ID), any(Instant.class)))
            .thenReturn(1L);

        when(cycleLogRepository.findAvgCycleTimeByStakeholder(eq(TEST_TENANT_ID), any(Instant.class)))
            .thenReturn(List.of());

        when(coreServiceClient.getPendingDecisionCount(TEST_TENANT_ID))
            .thenReturn(2);
        when(coreServiceClient.getHypothesesTestedThisWeek(TEST_TENANT_ID))
            .thenReturn(1);
        when(coreServiceClient.getActiveHypothesisCount(TEST_TENANT_ID))
            .thenReturn(1);
        when(coreServiceClient.getUrgentDecisions(TEST_TENANT_ID))
            .thenReturn(List.of());

        // Act
        var dashboard = dashboardService.getDashboard(TEST_TENANT_ID, null);

        // Assert
        assertThat(dashboard.decisionHealthStatus()).isEqualTo("GREEN");
    }

    @Test
    @DisplayName("getDashboard should return RED health status when average cycle time > 72 hours")
    void testGetDashboard_ReturnsRedHealthStatus() {
        // Arrange
        var snapshot = MetricSnapshot.builder()
            .metricType(AppConstants.METRIC_DECISION_VELOCITY)
            .metricDate(LocalDate.now())
            .value(BigDecimal.valueOf(75.0))
            .build();

        when(snapshotRepository.findByTenantIdAndMetricTypeAndMetricDateBetweenOrderByMetricDateAsc(
            any(UUID.class), anyString(), any(LocalDate.class), any(LocalDate.class)))
            .thenReturn(List.of(snapshot));

        when(cycleLogRepository.findAvgCycleTimeSince(eq(TEST_TENANT_ID), any(Instant.class)))
            .thenReturn(96.0); // Greater than 72 hours

        when(cycleLogRepository.countEscalatedSince(eq(TEST_TENANT_ID), any(Instant.class)))
            .thenReturn(5L);

        when(cycleLogRepository.findAvgCycleTimeByStakeholder(eq(TEST_TENANT_ID), any(Instant.class)))
            .thenReturn(List.of());

        when(coreServiceClient.getPendingDecisionCount(TEST_TENANT_ID))
            .thenReturn(10);
        when(coreServiceClient.getHypothesesTestedThisWeek(TEST_TENANT_ID))
            .thenReturn(0);
        when(coreServiceClient.getActiveHypothesisCount(TEST_TENANT_ID))
            .thenReturn(3);
        when(coreServiceClient.getUrgentDecisions(TEST_TENANT_ID))
            .thenReturn(List.of());

        // Act
        var dashboard = dashboardService.getDashboard(TEST_TENANT_ID, null);

        // Assert
        assertThat(dashboard.decisionHealthStatus()).isEqualTo("RED");
    }

    @Test
    @DisplayName("getDashboardSummary should include average cycle time and health status")
    void testGetDashboardSummary_IncludesAverageTimeAndHealth() {
        // Arrange
        when(cycleLogRepository.findAvgCycleTimeSince(eq(TEST_TENANT_ID), any(Instant.class)))
            .thenReturn(18.5);

        when(coreServiceClient.getPendingDecisionCount(TEST_TENANT_ID))
            .thenReturn(4);

        // Act
        var summary = dashboardService.getDashboardSummary(TEST_TENANT_ID, null);

        // Assert
        assertThat(summary).isNotNull();
        assertThat(summary).containsEntry("avgDecisionTimeHours", 18.5);
        assertThat(summary).containsEntry("healthStatus", "GREEN");
        assertThat(summary).containsEntry("pendingDecisions", 4);
        assertThat(summary).containsKey("lastUpdated");
    }

    @Test
    @DisplayName("getDashboardSummary should handle null average cycle time")
    void testGetDashboardSummary_HandlesNullAverageCycleTime() {
        // Arrange
        when(cycleLogRepository.findAvgCycleTimeSince(eq(TEST_TENANT_ID), any(Instant.class)))
            .thenReturn(null);

        when(coreServiceClient.getPendingDecisionCount(TEST_TENANT_ID))
            .thenReturn(0);

        // Act
        var summary = dashboardService.getDashboardSummary(TEST_TENANT_ID, null);

        // Assert
        assertThat(summary).isNotNull();
        assertThat(summary).containsEntry("avgDecisionTimeHours", 0.0);
        assertThat(summary).containsEntry("healthStatus", "GREEN");
        assertThat(summary).containsEntry("pendingDecisions", 0);
    }

    @Test
    @DisplayName("getDashboard should handle empty stakeholder leaderboard")
    void testGetDashboard_HandlesEmptyLeaderboard() {
        // Arrange
        when(snapshotRepository.findByTenantIdAndMetricTypeAndMetricDateBetweenOrderByMetricDateAsc(
            any(UUID.class), anyString(), any(LocalDate.class), any(LocalDate.class)))
            .thenReturn(List.of());

        when(cycleLogRepository.findAvgCycleTimeSince(eq(TEST_TENANT_ID), any(Instant.class)))
            .thenReturn(null);

        when(cycleLogRepository.countEscalatedSince(eq(TEST_TENANT_ID), any(Instant.class)))
            .thenReturn(0L);

        when(cycleLogRepository.findAvgCycleTimeByStakeholder(eq(TEST_TENANT_ID), any(Instant.class)))
            .thenReturn(List.of());

        when(coreServiceClient.getPendingDecisionCount(TEST_TENANT_ID))
            .thenReturn(0);
        when(coreServiceClient.getHypothesesTestedThisWeek(TEST_TENANT_ID))
            .thenReturn(0);
        when(coreServiceClient.getActiveHypothesisCount(TEST_TENANT_ID))
            .thenReturn(0);
        when(coreServiceClient.getUrgentDecisions(TEST_TENANT_ID))
            .thenReturn(List.of());

        // Act
        var dashboard = dashboardService.getDashboard(TEST_TENANT_ID, null);

        // Assert
        assertThat(dashboard).isNotNull();
        assertThat(dashboard.stakeholderLeaderboard()).isEmpty();
        assertThat(dashboard.avgDecisionWaitHours()).isZero();
    }

    @Test
    @DisplayName("getDashboard should populate urgent decisions list")
    void testGetDashboard_PopulatesUrgentDecisions() {
        // Arrange
        var now = Instant.now();
        var decisionId = UUID.randomUUID();
        var ownerId = UUID.randomUUID();

        when(snapshotRepository.findByTenantIdAndMetricTypeAndMetricDateBetweenOrderByMetricDateAsc(
            any(UUID.class), anyString(), any(LocalDate.class), any(LocalDate.class)))
            .thenReturn(List.of());

        when(cycleLogRepository.findAvgCycleTimeSince(eq(TEST_TENANT_ID), any(Instant.class)))
            .thenReturn(20.0);

        when(cycleLogRepository.countEscalatedSince(eq(TEST_TENANT_ID), any(Instant.class)))
            .thenReturn(1L);

        when(cycleLogRepository.findAvgCycleTimeByStakeholder(eq(TEST_TENANT_ID), any(Instant.class)))
            .thenReturn(List.of());

        var urgentDecision = new CoreDecisionSummary(
            decisionId,
            "Critical System Update",
            "BLOCKING",
            "NEEDS_INPUT",
            ownerId,
            "Jane Smith",
            now.minusSeconds(7200),
            3
        );

        when(coreServiceClient.getPendingDecisionCount(TEST_TENANT_ID))
            .thenReturn(1);
        when(coreServiceClient.getHypothesesTestedThisWeek(TEST_TENANT_ID))
            .thenReturn(0);
        when(coreServiceClient.getActiveHypothesisCount(TEST_TENANT_ID))
            .thenReturn(0);
        when(coreServiceClient.getUrgentDecisions(TEST_TENANT_ID))
            .thenReturn(List.of(urgentDecision));

        // Act
        var dashboard = dashboardService.getDashboard(TEST_TENANT_ID, null);

        // Assert
        assertThat(dashboard.urgentDecisions()).hasSize(1);
        var firstUrgent = dashboard.urgentDecisions().get(0);
        assertThat(firstUrgent.title()).isEqualTo("Critical System Update");
        assertThat(firstUrgent.priority()).isEqualTo("BLOCKING");
        assertThat(firstUrgent.ownerName()).isEqualTo("Jane Smith");
        assertThat(firstUrgent.waitingHours()).isEqualTo(2);
        assertThat(firstUrgent.blockedItemsCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("getDashboard should calculate outcomes from velocity snapshots")
    void testGetDashboard_CalculatesOutcomesFromSnapshots() {
        // Arrange
        var outcomeSnapshot1 = MetricSnapshot.builder()
            .metricType(AppConstants.METRIC_OUTCOME_VELOCITY)
            .metricDate(LocalDate.now().minusDays(3))
            .value(BigDecimal.valueOf(5))
            .build();

        var outcomeSnapshot2 = MetricSnapshot.builder()
            .metricType(AppConstants.METRIC_OUTCOME_VELOCITY)
            .metricDate(LocalDate.now().minusDays(2))
            .value(BigDecimal.valueOf(3))
            .build();

        var outcomeSnapshot3 = MetricSnapshot.builder()
            .metricType(AppConstants.METRIC_OUTCOME_VELOCITY)
            .metricDate(LocalDate.now())
            .value(BigDecimal.valueOf(2))
            .build();

        when(snapshotRepository.findByTenantIdAndMetricTypeAndMetricDateBetweenOrderByMetricDateAsc(
            eq(TEST_TENANT_ID), eq(AppConstants.METRIC_DECISION_VELOCITY), any(LocalDate.class), any(LocalDate.class)))
            .thenReturn(List.of());

        when(snapshotRepository.findByTenantIdAndMetricTypeAndMetricDateBetweenOrderByMetricDateAsc(
            eq(TEST_TENANT_ID), eq(AppConstants.METRIC_OUTCOME_VELOCITY), any(LocalDate.class), any(LocalDate.class)))
            .thenReturn(List.of(outcomeSnapshot1, outcomeSnapshot2, outcomeSnapshot3));

        when(cycleLogRepository.findAvgCycleTimeSince(eq(TEST_TENANT_ID), any(Instant.class)))
            .thenReturn(25.0);

        when(cycleLogRepository.countEscalatedSince(eq(TEST_TENANT_ID), any(Instant.class)))
            .thenReturn(2L);

        when(cycleLogRepository.findAvgCycleTimeByStakeholder(eq(TEST_TENANT_ID), any(Instant.class)))
            .thenReturn(List.of());

        when(coreServiceClient.getPendingDecisionCount(TEST_TENANT_ID))
            .thenReturn(0);
        when(coreServiceClient.getHypothesesTestedThisWeek(TEST_TENANT_ID))
            .thenReturn(0);
        when(coreServiceClient.getActiveHypothesisCount(TEST_TENANT_ID))
            .thenReturn(0);
        when(coreServiceClient.getUrgentDecisions(TEST_TENANT_ID))
            .thenReturn(List.of());

        // Act
        var dashboard = dashboardService.getDashboard(TEST_TENANT_ID, null);

        // Assert
        assertThat(dashboard.outcomesValidatedThisWeek()).isEqualTo(10); // 5 + 3 + 2
    }
}
