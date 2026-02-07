package ai.zevaro.analytics.reports;

import ai.zevaro.analytics.dashboard.dto.DataPoint;
import ai.zevaro.analytics.reports.dto.KeyResultProgress;
import ai.zevaro.analytics.reports.dto.OutcomeReport;
import ai.zevaro.analytics.reports.dto.TimelineEvent;
import ai.zevaro.analytics.reports.dto.WeeklyDigestReport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReportController.class)
@DisplayName("ReportController Tests")
class ReportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReportService reportService;

    private UUID tenantId;
    private UUID outcomeId;
    private WeeklyDigestReport weeklyDigestReport;
    private OutcomeReport outcomeReport;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        outcomeId = UUID.randomUUID();

        // Setup WeeklyDigestReport
        LocalDate weekStart = LocalDate.of(2024, 1, 1);
        LocalDate weekEnd = LocalDate.of(2024, 1, 7);
        List<DataPoint> dailyVelocity = List.of(
            new DataPoint("2024-01-01", 5),
            new DataPoint("2024-01-02", 7)
        );
        List<String> topStakeholders = List.of("John Doe", "Jane Smith");
        List<String> highlights = List.of("Resolved 42 decisions", "Validated 3 outcomes");
        List<String> concerns = List.of("High escalation rate");

        weeklyDigestReport = new WeeklyDigestReport(
            tenantId,
            weekStart,
            weekEnd,
            42,
            38,
            12.5,
            -5.2,
            3,
            1,
            8,
            dailyVelocity,
            topStakeholders,
            highlights,
            concerns
        );

        // Setup OutcomeReport
        List<KeyResultProgress> keyResults = List.of();
        List<TimelineEvent> timeline = List.of();

        outcomeReport = new OutcomeReport(
            outcomeId,
            "Increase user engagement",
            "VALIDATED",
            Instant.now().minusSeconds(86400 * 30),
            Instant.now(),
            15,
            12,
            8.5,
            8,
            6,
            2,
            0,
            keyResults,
            timeline
        );
    }

    @Test
    @DisplayName("GET /api/v1/reports should return List of reports with 200 OK")
    void testListReports_ShouldReturn200WithReportsList() throws Exception {
        List<Map<String, Object>> reports = List.of(
            Map.of(
                "id", "report-1",
                "name", "Weekly Digest",
                "type", "WEEKLY"
            ),
            Map.of(
                "id", "report-2",
                "name", "Monthly Summary",
                "type", "MONTHLY"
            )
        );

        when(reportService.listAvailableReports(tenantId)).thenReturn(reports);

        mockMvc.perform(get("/api/v1/reports")
                .header("X-Tenant-Id", tenantId.toString())
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)))
            .andExpect(jsonPath("$[0].id", is("report-1")))
            .andExpect(jsonPath("$[0].name", is("Weekly Digest")))
            .andExpect(jsonPath("$[1].id", is("report-2")))
            .andExpect(jsonPath("$[1].type", is("MONTHLY")));
    }

    @Test
    @DisplayName("GET /api/v1/reports should return empty list when no reports available")
    void testListReports_ShouldReturnEmptyListWhenNoReports() throws Exception {
        when(reportService.listAvailableReports(any(UUID.class))).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/reports")
                .header("X-Tenant-Id", tenantId.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("GET /api/v1/reports/weekly-digest should return WeeklyDigestReport with 200 OK")
    void testGetWeeklyDigest_ShouldReturn200WithReport() throws Exception {
        LocalDate weekStart = LocalDate.of(2024, 1, 1);

        when(reportService.generateWeeklyDigest(tenantId, weekStart))
            .thenReturn(weeklyDigestReport);

        mockMvc.perform(get("/api/v1/reports/weekly-digest")
                .header("X-Tenant-Id", tenantId.toString())
                .param("weekStart", "2024-01-01")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tenantId", notNullValue()))
            .andExpect(jsonPath("$.weekStart", is("2024-01-01")))
            .andExpect(jsonPath("$.weekEnd", is("2024-01-07")))
            .andExpect(jsonPath("$.decisionsResolved", is(42)))
            .andExpect(jsonPath("$.decisionsCreated", is(38)))
            .andExpect(jsonPath("$.avgCycleTimeHours", is(12.5)))
            .andExpect(jsonPath("$.cycleTimeChangePercent", is(-5.2)))
            .andExpect(jsonPath("$.outcomesValidated", is(3)))
            .andExpect(jsonPath("$.outcomesInvalidated", is(1)))
            .andExpect(jsonPath("$.hypothesesTested", is(8)))
            .andExpect(jsonPath("$.dailyDecisionVelocity", hasSize(2)))
            .andExpect(jsonPath("$.topStakeholders", hasSize(2)))
            .andExpect(jsonPath("$.highlights", hasSize(2)))
            .andExpect(jsonPath("$.concerns", hasSize(1)));
    }

    @Test
    @DisplayName("GET /api/v1/reports/weekly-digest without weekStart should use default")
    void testGetWeeklyDigest_ShouldUseDefaultWeekStartWhenNotProvided() throws Exception {
        when(reportService.generateWeeklyDigest(eq(tenantId), any(LocalDate.class)))
            .thenReturn(weeklyDigestReport);

        mockMvc.perform(get("/api/v1/reports/weekly-digest")
                .header("X-Tenant-Id", tenantId.toString())
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.decisionsResolved", is(42)));
    }

    @Test
    @DisplayName("GET /api/v1/reports/outcome/{outcomeId} should return OutcomeReport with 200 OK")
    void testGetOutcomeReport_ShouldReturn200WithReport() throws Exception {
        when(reportService.generateOutcomeReport(tenantId, outcomeId))
            .thenReturn(outcomeReport);

        mockMvc.perform(get("/api/v1/reports/outcome/{outcomeId}", outcomeId)
                .header("X-Tenant-Id", tenantId.toString())
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.outcomeId", notNullValue()))
            .andExpect(jsonPath("$.outcomeTitle", is("Increase user engagement")))
            .andExpect(jsonPath("$.status", is("VALIDATED")))
            .andExpect(jsonPath("$.createdAt", notNullValue()))
            .andExpect(jsonPath("$.validatedAt", notNullValue()))
            .andExpect(jsonPath("$.totalDecisions", is(15)))
            .andExpect(jsonPath("$.decisionsResolved", is(12)))
            .andExpect(jsonPath("$.avgDecisionTimeHours", is(8.5)))
            .andExpect(jsonPath("$.totalHypotheses", is(8)))
            .andExpect(jsonPath("$.hypothesesValidated", is(6)))
            .andExpect(jsonPath("$.hypothesesInvalidated", is(2)))
            .andExpect(jsonPath("$.hypothesesInProgress", is(0)));
    }

    @Test
    @DisplayName("GET /api/v1/reports/outcome/{outcomeId} should include timeline and key results")
    void testGetOutcomeReport_ShouldIncludeTimelineAndKeyResults() throws Exception {
        when(reportService.generateOutcomeReport(any(UUID.class), any(UUID.class)))
            .thenReturn(outcomeReport);

        mockMvc.perform(get("/api/v1/reports/outcome/{outcomeId}", outcomeId)
                .header("X-Tenant-Id", tenantId.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.keyResults", notNullValue()))
            .andExpect(jsonPath("$.timeline", notNullValue()));
    }

    @Test
    @DisplayName("GET /api/v1/reports/weekly-digest should include highlights and concerns")
    void testGetWeeklyDigest_ShouldIncludeHighlightsAndConcerns() throws Exception {
        when(reportService.generateWeeklyDigest(any(UUID.class), any(LocalDate.class)))
            .thenReturn(weeklyDigestReport);

        mockMvc.perform(get("/api/v1/reports/weekly-digest")
                .header("X-Tenant-Id", tenantId.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.highlights[0]", is("Resolved 42 decisions")))
            .andExpect(jsonPath("$.concerns[0]", is("High escalation rate")));
    }
}
