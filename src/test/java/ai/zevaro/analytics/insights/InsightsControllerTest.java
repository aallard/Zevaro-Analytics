package ai.zevaro.analytics.insights;

import ai.zevaro.analytics.insights.dto.Insight;
import ai.zevaro.analytics.insights.dto.InsightType;
import ai.zevaro.analytics.insights.dto.Trend;
import ai.zevaro.analytics.insights.dto.TrendDirection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InsightsController.class)
@DisplayName("InsightsController Tests")
class InsightsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private InsightsService insightsService;

    private UUID tenantId;
    private List<Insight> insights;
    private List<Trend> trends;
    private List<String> recommendations;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();

        // Setup insights
        insights = List.of(
            new Insight(
                InsightType.TREND,
                "High decision velocity detected",
                "Your team is resolving decisions 20% faster than last week",
                "Keep up the momentum. Consider sharing best practices across teams.",
                0.85,
                Instant.now()
            ),
            new Insight(
                InsightType.BOTTLENECK,
                "Escalation rate increasing",
                "Escalation rate has increased to 25% from 15%",
                "Review escalation policies and stakeholder availability.",
                0.65,
                Instant.now()
            )
        );

        // Setup trends
        trends = List.of(
            new Trend(
                "Decision Velocity",
                TrendDirection.UP,
                12.5,
                30,
                true
            ),
            new Trend(
                "Outcome Validation Rate",
                TrendDirection.DOWN,
                -5.2,
                30,
                true
            )
        );

        // Setup recommendations
        recommendations = List.of(
            "Focus on reducing escalation rate",
            "Increase stakeholder engagement",
            "Review hypothesis validation process"
        );
    }

    @Test
    @DisplayName("GET /api/v1/insights should return List of insights with 200 OK")
    void testGetInsights_ShouldReturn200WithInsightsList() throws Exception {
        when(insightsService.generateInsights(tenantId, null)).thenReturn(insights);

        mockMvc.perform(get("/api/v1/insights")
                .header("X-Tenant-Id", tenantId.toString())
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)))
            .andExpect(jsonPath("$[0].type", is("TREND")))
            .andExpect(jsonPath("$[0].title", is("High decision velocity detected")))
            .andExpect(jsonPath("$[0].description", containsString("20%")))
            .andExpect(jsonPath("$[1].type", is("BOTTLENECK")))
            .andExpect(jsonPath("$[1].confidence", is(0.65)));
    }

    @Test
    @DisplayName("GET /api/v1/insights should return empty list when no insights")
    void testGetInsights_ShouldReturnEmptyListWhenNoInsights() throws Exception {
        when(insightsService.generateInsights(any(UUID.class), any())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/insights")
                .header("X-Tenant-Id", tenantId.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("GET /api/v1/insights should include all insight fields")
    void testGetInsights_ShouldIncludeAllInsightFields() throws Exception {
        when(insightsService.generateInsights(any(UUID.class), any())).thenReturn(insights);

        mockMvc.perform(get("/api/v1/insights")
                .header("X-Tenant-Id", tenantId.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].type", notNullValue()))
            .andExpect(jsonPath("$[0].title", notNullValue()))
            .andExpect(jsonPath("$[0].description", notNullValue()))
            .andExpect(jsonPath("$[0].confidence", notNullValue()))
            .andExpect(jsonPath("$[0].generatedAt", notNullValue()));
    }

    @Test
    @DisplayName("GET /api/v1/insights should pass tenantId to service")
    void testGetInsights_ShouldPassTenantIdToService() throws Exception {
        UUID testTenantId = UUID.randomUUID();
        when(insightsService.generateInsights(testTenantId, null)).thenReturn(insights);

        mockMvc.perform(get("/api/v1/insights")
                .header("X-Tenant-Id", testTenantId.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    @DisplayName("GET /api/v1/insights/trends should return List of trends with 200 OK")
    void testGetTrends_ShouldReturn200WithTrendsList() throws Exception {
        when(insightsService.detectTrends(tenantId, null)).thenReturn(trends);

        mockMvc.perform(get("/api/v1/insights/trends")
                .header("X-Tenant-Id", tenantId.toString())
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)))
            .andExpect(jsonPath("$[0].metricName", is("Decision Velocity")))
            .andExpect(jsonPath("$[0].direction", is("UP")))
            .andExpect(jsonPath("$[0].percentChange", is(12.5)))
            .andExpect(jsonPath("$[1].metricName", is("Outcome Validation Rate")))
            .andExpect(jsonPath("$[1].direction", is("DOWN")))
            .andExpect(jsonPath("$[1].percentChange", is(-5.2)));
    }

    @Test
    @DisplayName("GET /api/v1/insights/trends should return empty list when no trends")
    void testGetTrends_ShouldReturnEmptyListWhenNoTrends() throws Exception {
        when(insightsService.detectTrends(any(UUID.class), any())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/insights/trends")
                .header("X-Tenant-Id", tenantId.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("GET /api/v1/insights/trends should include all trend fields")
    void testGetTrends_ShouldIncludeAllTrendFields() throws Exception {
        when(insightsService.detectTrends(any(UUID.class), any())).thenReturn(trends);

        mockMvc.perform(get("/api/v1/insights/trends")
                .header("X-Tenant-Id", tenantId.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].metricName", notNullValue()))
            .andExpect(jsonPath("$[0].direction", notNullValue()))
            .andExpect(jsonPath("$[0].percentChange", notNullValue()))
            .andExpect(jsonPath("$[0].periodDays", notNullValue()));
    }

    @Test
    @DisplayName("GET /api/v1/insights/trends should include direction as enum")
    void testGetTrends_ShouldIncludeDirectionAsEnum() throws Exception {
        when(insightsService.detectTrends(any(UUID.class), any())).thenReturn(trends);

        mockMvc.perform(get("/api/v1/insights/trends")
                .header("X-Tenant-Id", tenantId.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].direction", is("UP")))
            .andExpect(jsonPath("$[1].direction", is("DOWN")));
    }

    @Test
    @DisplayName("GET /api/v1/insights/recommendations should return List of recommendations with 200 OK")
    void testGetRecommendations_ShouldReturn200WithRecommendationsList() throws Exception {
        when(insightsService.getRecommendations(tenantId, null)).thenReturn(recommendations);

        mockMvc.perform(get("/api/v1/insights/recommendations")
                .header("X-Tenant-Id", tenantId.toString())
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(3)))
            .andExpect(jsonPath("$[0]", is("Focus on reducing escalation rate")))
            .andExpect(jsonPath("$[1]", is("Increase stakeholder engagement")))
            .andExpect(jsonPath("$[2]", is("Review hypothesis validation process")));
    }

    @Test
    @DisplayName("GET /api/v1/insights/recommendations should return empty list when no recommendations")
    void testGetRecommendations_ShouldReturnEmptyListWhenNoRecommendations() throws Exception {
        when(insightsService.getRecommendations(any(UUID.class), any())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/insights/recommendations")
                .header("X-Tenant-Id", tenantId.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("GET /api/v1/insights/recommendations should return strings")
    void testGetRecommendations_ShouldReturnStrings() throws Exception {
        when(insightsService.getRecommendations(any(UUID.class), any())).thenReturn(recommendations);

        mockMvc.perform(get("/api/v1/insights/recommendations")
                .header("X-Tenant-Id", tenantId.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0]", notNullValue()))
            .andExpect(jsonPath("$[1]", notNullValue()))
            .andExpect(jsonPath("$[2]", notNullValue()));
    }

    @Test
    @DisplayName("GET /api/v1/insights/recommendations should pass tenantId to service")
    void testGetRecommendations_ShouldPassTenantIdToService() throws Exception {
        UUID testTenantId = UUID.randomUUID();
        when(insightsService.getRecommendations(testTenantId, null)).thenReturn(recommendations);

        mockMvc.perform(get("/api/v1/insights/recommendations")
                .header("X-Tenant-Id", testTenantId.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(3)));
    }

    @Test
    @DisplayName("GET /api/v1/insights/trends should pass tenantId to service")
    void testGetTrends_ShouldPassTenantIdToService() throws Exception {
        UUID testTenantId = UUID.randomUUID();
        when(insightsService.detectTrends(testTenantId, null)).thenReturn(trends);

        mockMvc.perform(get("/api/v1/insights/trends")
                .header("X-Tenant-Id", testTenantId.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)));
    }
}
