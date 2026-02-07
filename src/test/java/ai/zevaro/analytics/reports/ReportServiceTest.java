package ai.zevaro.analytics.reports;

import ai.zevaro.analytics.client.CoreServiceClient;
import ai.zevaro.analytics.client.dto.CoreOutcomeInfo;
import ai.zevaro.analytics.client.dto.CoreStakeholderInfo;
import ai.zevaro.analytics.config.AppConstants;
import ai.zevaro.analytics.reports.dto.OutcomeReport;
import ai.zevaro.analytics.reports.dto.WeeklyDigestReport;
import ai.zevaro.analytics.repository.DecisionCycleLog;
import ai.zevaro.analytics.repository.DecisionCycleLogRepository;
import ai.zevaro.analytics.repository.MetricSnapshot;
import ai.zevaro.analytics.repository.MetricSnapshotRepository;
import ai.zevaro.analytics.repository.Report;
import ai.zevaro.analytics.repository.ReportRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReportService Unit Tests")
class ReportServiceTest {

    @Mock
    private MetricSnapshotRepository snapshotRepository;

    @Mock
    private DecisionCycleLogRepository cycleLogRepository;

    @Mock
    private ReportRepository reportRepository;

    @Mock
    private CoreServiceClient coreServiceClient;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private ReportService reportService;

    private static final UUID TEST_TENANT_ID = UUID.randomUUID();
    private static final UUID TEST_OUTCOME_ID = UUID.randomUUID();
    private static final UUID TEST_STAKEHOLDER_ID_1 = UUID.randomUUID();
    private static final UUID TEST_STAKEHOLDER_ID_2 = UUID.randomUUID();

    @Test
    @DisplayName("generateWeeklyDigest should pull data from repositories and persist via ReportRepository")
    void testGenerateWeeklyDigest_AggregatesDataAndPersists() {
        // Arrange
        var weekStart = LocalDate.now().minusDays(7);
        var weekEnd = weekStart.plusDays(7);
        var prevWeekStart = weekStart.minusDays(7);

        var startInstant = weekStart.atStartOfDay().toInstant(ZoneOffset.UTC);
        var endInstant = weekEnd.atStartOfDay().toInstant(ZoneOffset.UTC);
        var prevStartInstant = prevWeekStart.atStartOfDay().toInstant(ZoneOffset.UTC);

        var thisWeekLog1 = DecisionCycleLog.builder()
            .cycleTimeHours(BigDecimal.valueOf(10.0))
            .wasEscalated(false)
            .priority("HIGH")
            .build();

        var thisWeekLog2 = DecisionCycleLog.builder()
            .cycleTimeHours(BigDecimal.valueOf(12.0))
            .wasEscalated(false)
            .priority("MEDIUM")
            .build();

        var prevWeekLog1 = DecisionCycleLog.builder()
            .cycleTimeHours(BigDecimal.valueOf(20.0))
            .wasEscalated(true)
            .priority("HIGH")
            .build();

        when(cycleLogRepository.findByTenantIdAndResolvedAtBetween(TEST_TENANT_ID, startInstant, endInstant))
            .thenReturn(List.of(thisWeekLog1, thisWeekLog2));

        when(cycleLogRepository.findByTenantIdAndResolvedAtBetween(TEST_TENANT_ID, prevStartInstant, startInstant))
            .thenReturn(List.of(prevWeekLog1));

        var decisionVelocitySnapshot = MetricSnapshot.builder()
            .metricType(AppConstants.METRIC_DECISION_VELOCITY)
            .metricDate(weekStart)
            .value(BigDecimal.valueOf(15.0))
            .build();

        when(snapshotRepository.findByTenantIdAndMetricTypeAndMetricDateBetweenOrderByMetricDateAsc(
            eq(TEST_TENANT_ID), eq(AppConstants.METRIC_DECISION_VELOCITY), eq(weekStart), eq(weekEnd)))
            .thenReturn(List.of(decisionVelocitySnapshot));

        var outcomeSnapshot = MetricSnapshot.builder()
            .metricType(AppConstants.METRIC_OUTCOME_VELOCITY)
            .metricDate(weekStart)
            .value(BigDecimal.valueOf(5))
            .dimensions(Map.of("invalidated", 1))
            .build();

        when(snapshotRepository.findByTenantIdAndMetricTypeAndMetricDateBetweenOrderByMetricDateAsc(
            eq(TEST_TENANT_ID), eq(AppConstants.METRIC_OUTCOME_VELOCITY), eq(weekStart), eq(weekEnd)))
            .thenReturn(List.of(outcomeSnapshot));

        var hypothesisSnapshot = MetricSnapshot.builder()
            .metricType(AppConstants.METRIC_HYPOTHESIS_THROUGHPUT)
            .metricDate(weekStart)
            .value(BigDecimal.valueOf(3))
            .build();

        when(snapshotRepository.findByTenantIdAndMetricTypeAndMetricDateBetweenOrderByMetricDateAsc(
            eq(TEST_TENANT_ID), eq(AppConstants.METRIC_HYPOTHESIS_THROUGHPUT), eq(weekStart), eq(weekEnd)))
            .thenReturn(List.of(hypothesisSnapshot));

        when(cycleLogRepository.findAvgCycleTimeByStakeholder(eq(TEST_TENANT_ID), any(Instant.class)))
            .thenReturn(List.of(
                new Object[]{TEST_STAKEHOLDER_ID_1, 11.0},
                new Object[]{TEST_STAKEHOLDER_ID_2, 9.0}
            ));

        when(coreServiceClient.getDecisionsCreatedCount(TEST_TENANT_ID))
            .thenReturn(2);

        when(coreServiceClient.getStakeholder(TEST_TENANT_ID, TEST_STAKEHOLDER_ID_1))
            .thenReturn(new CoreStakeholderInfo(
                TEST_STAKEHOLDER_ID_1,
                "Alice Smith",
                "alice@example.com",
                "Manager",
                2,
                10
            ));

        when(coreServiceClient.getStakeholder(TEST_TENANT_ID, TEST_STAKEHOLDER_ID_2))
            .thenReturn(new CoreStakeholderInfo(
                TEST_STAKEHOLDER_ID_2,
                "Bob Johnson",
                "bob@example.com",
                "Analyst",
                1,
                8
            ));

        when(reportRepository.findByTenantIdAndReportTypeAndPeriodStartAndPeriodEnd(
            TEST_TENANT_ID, "WEEKLY_DIGEST", weekStart, weekEnd))
            .thenReturn(Optional.empty());

        when(objectMapper.convertValue(any(), eq(Map.class)))
            .thenReturn(Map.of("test", "data"));

        // Act
        var report = reportService.generateWeeklyDigest(TEST_TENANT_ID, weekStart);

        // Assert
        assertThat(report).isNotNull();
        assertThat(report.tenantId()).isEqualTo(TEST_TENANT_ID);
        assertThat(report.weekStart()).isEqualTo(weekStart);
        assertThat(report.weekEnd()).isEqualTo(weekEnd);
        assertThat(report.decisionsResolvedCount()).isEqualTo(2);
        assertThat(report.decisionsCreatedCount()).isEqualTo(2);
        assertThat(report.avgCycleTimeHours()).isEqualTo(11.0);
        assertThat(report.outcomesValidatedCount()).isEqualTo(5);
        assertThat(report.outcomesInvalidatedCount()).isEqualTo(1);
        assertThat(report.hypothesesTestedCount()).isEqualTo(3);
        assertThat(report.topStakeholders()).hasSize(2);

        verify(reportRepository).save(any(Report.class));
    }

    @Test
    @DisplayName("generateWeeklyDigest should calculate positive change percent when improvement")
    void testGenerateWeeklyDigest_PositiveChangePercent() {
        // Arrange
        var weekStart = LocalDate.now().minusDays(7);
        var weekEnd = weekStart.plusDays(7);
        var prevWeekStart = weekStart.minusDays(7);

        var startInstant = weekStart.atStartOfDay().toInstant(ZoneOffset.UTC);
        var endInstant = weekEnd.atStartOfDay().toInstant(ZoneOffset.UTC);
        var prevStartInstant = prevWeekStart.atStartOfDay().toInstant(ZoneOffset.UTC);

        // This week: 10 hours avg
        var thisWeekLog = DecisionCycleLog.builder()
            .cycleTimeHours(BigDecimal.valueOf(10.0))
            .wasEscalated(false)
            .build();

        // Previous week: 20 hours avg
        var prevWeekLog = DecisionCycleLog.builder()
            .cycleTimeHours(BigDecimal.valueOf(20.0))
            .wasEscalated(false)
            .build();

        when(cycleLogRepository.findByTenantIdAndResolvedAtBetween(TEST_TENANT_ID, startInstant, endInstant))
            .thenReturn(List.of(thisWeekLog));

        when(cycleLogRepository.findByTenantIdAndResolvedAtBetween(TEST_TENANT_ID, prevStartInstant, startInstant))
            .thenReturn(List.of(prevWeekLog));

        when(snapshotRepository.findByTenantIdAndMetricTypeAndMetricDateBetweenOrderByMetricDateAsc(
            anyUUID(), anyString(), any(LocalDate.class), any(LocalDate.class)))
            .thenReturn(List.of());

        when(cycleLogRepository.findAvgCycleTimeByStakeholder(anyUUID(), any(Instant.class)))
            .thenReturn(List.of());

        when(coreServiceClient.getDecisionsCreatedCount(TEST_TENANT_ID))
            .thenReturn(1);

        when(reportRepository.findByTenantIdAndReportTypeAndPeriodStartAndPeriodEnd(
            TEST_TENANT_ID, "WEEKLY_DIGEST", weekStart, weekEnd))
            .thenReturn(Optional.empty());

        when(objectMapper.convertValue(any(), eq(Map.class)))
            .thenReturn(Map.of());

        // Act
        var report = reportService.generateWeeklyDigest(TEST_TENANT_ID, weekStart);

        // Assert
        assertThat(report.changePercentFromPreviousWeek()).isNegative(); // Improvement = negative change
        assertThat(report.highlights()).contains(
            "Decision velocity improved by 50.0% vs last week"
        );
    }

    @Test
    @DisplayName("generateWeeklyDigest should include highlights for validated outcomes")
    void testGenerateWeeklyDigest_IncludesHighlightsForOutcomes() {
        // Arrange
        var weekStart = LocalDate.now().minusDays(7);
        var weekEnd = weekStart.plusDays(7);

        var startInstant = weekStart.atStartOfDay().toInstant(ZoneOffset.UTC);
        var endInstant = weekEnd.atStartOfDay().toInstant(ZoneOffset.UTC);

        when(cycleLogRepository.findByTenantIdAndResolvedAtBetween(anyUUID(), any(Instant.class), any(Instant.class)))
            .thenReturn(List.of());

        var outcomeSnapshot = MetricSnapshot.builder()
            .metricType(AppConstants.METRIC_OUTCOME_VELOCITY)
            .value(BigDecimal.valueOf(7))
            .dimensions(Map.of())
            .build();

        when(snapshotRepository.findByTenantIdAndMetricTypeAndMetricDateBetweenOrderByMetricDateAsc(
            anyUUID(), anyString(), any(LocalDate.class), any(LocalDate.class)))
            .thenReturn(List.of(outcomeSnapshot));

        when(cycleLogRepository.findAvgCycleTimeByStakeholder(anyUUID(), any(Instant.class)))
            .thenReturn(List.of());

        when(coreServiceClient.getDecisionsCreatedCount(TEST_TENANT_ID))
            .thenReturn(0);

        when(reportRepository.findByTenantIdAndReportTypeAndPeriodStartAndPeriodEnd(
            TEST_TENANT_ID, "WEEKLY_DIGEST", weekStart, weekEnd))
            .thenReturn(Optional.empty());

        when(objectMapper.convertValue(any(), eq(Map.class)))
            .thenReturn(Map.of());

        // Act
        var report = reportService.generateWeeklyDigest(TEST_TENANT_ID, weekStart);

        // Assert
        assertThat(report.highlights()).contains("7 outcomes validated this week");
    }

    @Test
    @DisplayName("generateOutcomeReport should call CoreServiceClient.getOutcome()")
    void testGenerateOutcomeReport_CallsCoreServiceClient() {
        // Arrange
        var outcomeInfo = new CoreOutcomeInfo(
            TEST_OUTCOME_ID,
            "Test Outcome",
            "VALIDATED",
            "Success if metric improves",
            Instant.now().minusSeconds(604800),
            Instant.now(),
            UUID.randomUUID()
        );

        when(coreServiceClient.getOutcome(TEST_TENANT_ID, TEST_OUTCOME_ID))
            .thenReturn(outcomeInfo);

        when(cycleLogRepository.findByTenantIdAndResolvedAtBetween(anyUUID(), any(Instant.class), any(Instant.class)))
            .thenReturn(List.of());

        when(snapshotRepository.findByTenantIdAndMetricTypeAndMetricDateBetweenOrderByMetricDateAsc(
            anyUUID(), anyString(), any(LocalDate.class), any(LocalDate.class)))
            .thenReturn(List.of());

        when(coreServiceClient.getActiveHypothesisCount(TEST_TENANT_ID))
            .thenReturn(2);

        when(reportRepository.findByTenantIdAndReportTypeAndPeriodStartAndPeriodEnd(
            anyUUID(), anyString(), any(LocalDate.class), any(LocalDate.class)))
            .thenReturn(Optional.empty());

        when(objectMapper.convertValue(any(), eq(Map.class)))
            .thenReturn(Map.of());

        // Act
        var report = reportService.generateOutcomeReport(TEST_TENANT_ID, TEST_OUTCOME_ID);

        // Assert
        assertThat(report).isNotNull();
        assertThat(report.outcomeId()).isEqualTo(TEST_OUTCOME_ID);
        assertThat(report.title()).isEqualTo("Test Outcome");
        assertThat(report.status()).isEqualTo("VALIDATED");
        assertThat(report.createdAt()).isEqualTo(outcomeInfo.createdAt());
        assertThat(report.validatedAt()).isEqualTo(outcomeInfo.validatedAt());

        verify(coreServiceClient).getOutcome(TEST_TENANT_ID, TEST_OUTCOME_ID);
    }

    @Test
    @DisplayName("generateOutcomeReport should build comprehensive report with decisions and hypotheses")
    void testGenerateOutcomeReport_BuildsComprehensiveReport() {
        // Arrange
        var createdAt = Instant.now().minusSeconds(604800);
        var now = Instant.now();

        var outcomeInfo = new CoreOutcomeInfo(
            TEST_OUTCOME_ID,
            "Improve Customer Satisfaction",
            "VALIDATED",
            "NPS Score > 70",
            createdAt,
            now,
            UUID.randomUUID()
        );

        when(coreServiceClient.getOutcome(TEST_TENANT_ID, TEST_OUTCOME_ID))
            .thenReturn(outcomeInfo);

        var decisionLog1 = DecisionCycleLog.builder()
            .cycleTimeHours(BigDecimal.valueOf(8.0))
            .priority("HIGH")
            .build();

        var decisionLog2 = DecisionCycleLog.builder()
            .cycleTimeHours(BigDecimal.valueOf(12.0))
            .priority("MEDIUM")
            .build();

        when(cycleLogRepository.findByTenantIdAndResolvedAtBetween(TEST_TENANT_ID, createdAt, now))
            .thenReturn(List.of(decisionLog1, decisionLog2));

        var hypothesisSnapshot1 = MetricSnapshot.builder()
            .value(BigDecimal.valueOf(5))
            .dimensions(Map.of("validated", 3, "invalidated", 2))
            .build();

        when(snapshotRepository.findByTenantIdAndMetricTypeAndMetricDateBetweenOrderByMetricDateAsc(
            anyUUID(), eq(AppConstants.METRIC_HYPOTHESIS_THROUGHPUT), any(LocalDate.class), any(LocalDate.class)))
            .thenReturn(List.of(hypothesisSnapshot1));

        when(coreServiceClient.getActiveHypothesisCount(TEST_TENANT_ID))
            .thenReturn(1);

        when(reportRepository.findByTenantIdAndReportTypeAndPeriodStartAndPeriodEnd(
            anyUUID(), anyString(), any(LocalDate.class), any(LocalDate.class)))
            .thenReturn(Optional.empty());

        when(objectMapper.convertValue(any(), eq(Map.class)))
            .thenReturn(Map.of());

        // Act
        var report = reportService.generateOutcomeReport(TEST_TENANT_ID, TEST_OUTCOME_ID);

        // Assert
        assertThat(report.totalDecisionsResolved()).isEqualTo(2);
        assertThat(report.avgDecisionTimeHours()).isEqualTo(10.0);
        assertThat(report.totalHypothesesTested()).isEqualTo(5);
        assertThat(report.hypothesesValidated()).isEqualTo(3);
        assertThat(report.hypothesesInvalidated()).isEqualTo(2);
        assertThat(report.hypothesesInProgress()).isEqualTo(1);
        assertThat(report.timeline()).hasSize(2);
    }

    @Test
    @DisplayName("listAvailableReports should query ReportRepository and return recent reports")
    void testListAvailableReports_QueryRepository() {
        // Arrange
        var reportId1 = UUID.randomUUID();
        var reportId2 = UUID.randomUUID();

        var report1 = Report.builder()
            .id(reportId1)
            .tenantId(TEST_TENANT_ID)
            .reportType("WEEKLY_DIGEST")
            .periodStart(LocalDate.now().minusDays(7))
            .periodEnd(LocalDate.now())
            .generatedAt(Instant.now().minusSeconds(3600))
            .build();

        var report2 = Report.builder()
            .id(reportId2)
            .tenantId(TEST_TENANT_ID)
            .reportType("OUTCOME_REPORT")
            .periodStart(LocalDate.now().minusDays(14))
            .periodEnd(LocalDate.now().minusDays(7))
            .generatedAt(Instant.now().minusSeconds(86400))
            .build();

        when(reportRepository.findByTenantIdOrderByGeneratedAtDesc(TEST_TENANT_ID))
            .thenReturn(List.of(report1, report2));

        // Act
        var reports = reportService.listAvailableReports(TEST_TENANT_ID);

        // Assert
        assertThat(reports).hasSize(2);

        var reportTypes = (List<?>) reports.get(0).get("reportTypes");
        var recentReports = (List<?>) reports.get(1).get("recentReports");

        assertThat(reportTypes).hasSize(3);
        assertThat(recentReports).hasSize(2);

        verify(reportRepository).findByTenantIdOrderByGeneratedAtDesc(TEST_TENANT_ID);
    }

    @Test
    @DisplayName("generateWeeklyDigest should update existing report if already generated for period")
    void testGenerateWeeklyDigest_UpdatesExistingReport() {
        // Arrange
        var weekStart = LocalDate.now().minusDays(7);
        var weekEnd = weekStart.plusDays(7);

        var existingReport = Report.builder()
            .id(UUID.randomUUID())
            .tenantId(TEST_TENANT_ID)
            .reportType("WEEKLY_DIGEST")
            .periodStart(weekStart)
            .periodEnd(weekEnd)
            .generatedAt(Instant.now().minusSeconds(3600))
            .data(Map.of("old", "data"))
            .build();

        when(cycleLogRepository.findByTenantIdAndResolvedAtBetween(anyUUID(), any(Instant.class), any(Instant.class)))
            .thenReturn(List.of());

        when(snapshotRepository.findByTenantIdAndMetricTypeAndMetricDateBetweenOrderByMetricDateAsc(
            anyUUID(), anyString(), any(LocalDate.class), any(LocalDate.class)))
            .thenReturn(List.of());

        when(cycleLogRepository.findAvgCycleTimeByStakeholder(anyUUID(), any(Instant.class)))
            .thenReturn(List.of());

        when(coreServiceClient.getDecisionsCreatedCount(TEST_TENANT_ID))
            .thenReturn(0);

        when(reportRepository.findByTenantIdAndReportTypeAndPeriodStartAndPeriodEnd(
            TEST_TENANT_ID, "WEEKLY_DIGEST", weekStart, weekEnd))
            .thenReturn(Optional.of(existingReport));

        when(objectMapper.convertValue(any(), eq(Map.class)))
            .thenReturn(Map.of("new", "data"));

        // Act
        reportService.generateWeeklyDigest(TEST_TENANT_ID, weekStart);

        // Assert
        var captor = ArgumentCaptor.forClass(Report.class);
        verify(reportRepository).save(captor.capture());

        var savedReport = captor.getValue();
        assertThat(savedReport.getId()).isEqualTo(existingReport.getId());
        assertThat(savedReport.getData()).containsEntry("new", "data");
    }

    @Test
    @DisplayName("generateOutcomeReport should handle missing outcome info gracefully")
    void testGenerateOutcomeReport_HandlesMissingOutcomeInfo() {
        // Arrange
        when(coreServiceClient.getOutcome(TEST_TENANT_ID, TEST_OUTCOME_ID))
            .thenReturn(null);

        when(cycleLogRepository.findByTenantIdAndResolvedAtBetween(anyUUID(), any(Instant.class), any(Instant.class)))
            .thenReturn(List.of());

        when(snapshotRepository.findByTenantIdAndMetricTypeAndMetricDateBetweenOrderByMetricDateAsc(
            anyUUID(), anyString(), any(LocalDate.class), any(LocalDate.class)))
            .thenReturn(List.of());

        when(coreServiceClient.getActiveHypothesisCount(TEST_TENANT_ID))
            .thenReturn(0);

        when(reportRepository.findByTenantIdAndReportTypeAndPeriodStartAndPeriodEnd(
            anyUUID(), anyString(), any(LocalDate.class), any(LocalDate.class)))
            .thenReturn(Optional.empty());

        when(objectMapper.convertValue(any(), eq(Map.class)))
            .thenReturn(Map.of());

        // Act
        var report = reportService.generateOutcomeReport(TEST_TENANT_ID, TEST_OUTCOME_ID);

        // Assert
        assertThat(report).isNotNull();
        assertThat(report.outcomeId()).isEqualTo(TEST_OUTCOME_ID);
        assertThat(report.title()).isEqualTo("Outcome Report");
        assertThat(report.status()).isEqualTo("UNKNOWN");
        assertThat(report.totalDecisionsResolved()).isZero();
    }
}
