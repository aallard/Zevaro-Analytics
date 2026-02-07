package ai.zevaro.analytics.dashboard;

import ai.zevaro.analytics.dashboard.dto.DashboardData;
import ai.zevaro.analytics.dashboard.dto.DataPoint;
import ai.zevaro.analytics.dashboard.dto.DecisionSummary;
import ai.zevaro.analytics.dashboard.dto.StakeholderScore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DashboardController.class)
@DisplayName("DashboardController Tests")
class DashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DashboardService dashboardService;

    private UUID tenantId;
    private DashboardData dashboardData;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();

        List<DecisionSummary> urgentDecisions = List.of();
        List<DataPoint> decisionVelocityTrend = List.of(
            new DataPoint("2024-01-01", 5),
            new DataPoint("2024-01-02", 8)
        );
        List<DataPoint> outcomeVelocityTrend = List.of(
            new DataPoint("2024-01-01", 2),
            new DataPoint("2024-01-02", 3)
        );
        List<StakeholderScore> leaderboard = List.of();

        dashboardData = new DashboardData(
            42,
            12.5,
            15,
            8,
            3,
            "GREEN",
            urgentDecisions,
            decisionVelocityTrend,
            outcomeVelocityTrend,
            leaderboard,
            "HEALTHY",
            Instant.now(),
            0
        );
    }

    @Test
    @DisplayName("GET /api/v1/dashboard should return DashboardData with 200 OK")
    void testGetDashboard_ShouldReturn200WithDashboardData() throws Exception {
        when(dashboardService.getDashboard(tenantId)).thenReturn(dashboardData);

        mockMvc.perform(get("/api/v1/dashboard")
                .header("X-Tenant-Id", tenantId.toString())
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.decisionsPendingCount", is(42)))
            .andExpect(jsonPath("$.avgDecisionWaitHours", is(12.5)))
            .andExpect(jsonPath("$.outcomesValidatedThisWeek", is(15)))
            .andExpect(jsonPath("$.hypothesesTestedThisWeek", is(8)))
            .andExpect(jsonPath("$.experimentsRunning", is(3)))
            .andExpect(jsonPath("$.decisionHealthStatus", is("GREEN")))
            .andExpect(jsonPath("$.pipelineStatus", is("HEALTHY")))
            .andExpect(jsonPath("$.idleTimeMinutes", is(0)))
            .andExpect(jsonPath("$.decisionVelocityTrend", hasSize(2)))
            .andExpect(jsonPath("$.outcomeVelocityTrend", hasSize(2)))
            .andExpect(jsonPath("$.lastDeployment", notNullValue()));
    }

    @Test
    @DisplayName("GET /api/v1/dashboard should pass tenantId header correctly")
    void testGetDashboard_ShouldPassTenantIdFromHeader() throws Exception {
        UUID testTenantId = UUID.randomUUID();
        when(dashboardService.getDashboard(testTenantId)).thenReturn(dashboardData);

        mockMvc.perform(get("/api/v1/dashboard")
                .header("X-Tenant-Id", testTenantId.toString())
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/dashboard/summary should return Map with 200 OK")
    void testGetDashboardSummary_ShouldReturn200WithMap() throws Exception {
        Map<String, Object> summaryData = Map.of(
            "decisionsPending", 42,
            "avgWaitHours", 12.5,
            "validatedThisWeek", 15,
            "healthStatus", "GREEN",
            "trend", "IMPROVING"
        );

        when(dashboardService.getDashboardSummary(tenantId)).thenReturn(summaryData);

        mockMvc.perform(get("/api/v1/dashboard/summary")
                .header("X-Tenant-Id", tenantId.toString())
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.decisionsPending", is(42)))
            .andExpect(jsonPath("$.avgWaitHours", is(12.5)))
            .andExpect(jsonPath("$.validatedThisWeek", is(15)))
            .andExpect(jsonPath("$.healthStatus", is("GREEN")))
            .andExpect(jsonPath("$.trend", is("IMPROVING")));
    }

    @Test
    @DisplayName("GET /api/v1/dashboard/summary should return correct JSON structure")
    void testGetDashboardSummary_ShouldReturnCorrectStructure() throws Exception {
        Map<String, Object> summaryData = Map.of(
            "totalMetrics", 5,
            "timeRange", "7_DAYS"
        );

        when(dashboardService.getDashboardSummary(any(UUID.class))).thenReturn(summaryData);

        mockMvc.perform(get("/api/v1/dashboard/summary")
                .header("X-Tenant-Id", tenantId.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", notNullValue()))
            .andExpect(jsonPath("$.totalMetrics", is(5)))
            .andExpect(jsonPath("$.timeRange", is("7_DAYS")));
    }

    @Test
    @DisplayName("GET /api/v1/dashboard should include all required fields")
    void testGetDashboard_ShouldIncludeAllRequiredFields() throws Exception {
        when(dashboardService.getDashboard(any(UUID.class))).thenReturn(dashboardData);

        mockMvc.perform(get("/api/v1/dashboard")
                .header("X-Tenant-Id", tenantId.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.decisionsPendingCount", notNullValue()))
            .andExpect(jsonPath("$.avgDecisionWaitHours", notNullValue()))
            .andExpect(jsonPath("$.outcomesValidatedThisWeek", notNullValue()))
            .andExpect(jsonPath("$.hypothesesTestedThisWeek", notNullValue()))
            .andExpect(jsonPath("$.experimentsRunning", notNullValue()))
            .andExpect(jsonPath("$.decisionHealthStatus", notNullValue()))
            .andExpect(jsonPath("$.urgentDecisions", notNullValue()))
            .andExpect(jsonPath("$.decisionVelocityTrend", notNullValue()))
            .andExpect(jsonPath("$.outcomeVelocityTrend", notNullValue()))
            .andExpect(jsonPath("$.stakeholderLeaderboard", notNullValue()))
            .andExpect(jsonPath("$.pipelineStatus", notNullValue()))
            .andExpect(jsonPath("$.lastDeployment", notNullValue()))
            .andExpect(jsonPath("$.idleTimeMinutes", notNullValue()));
    }
}
