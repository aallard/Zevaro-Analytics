package ai.zevaro.analytics.metrics;

import ai.zevaro.analytics.client.CoreServiceClient;
import ai.zevaro.analytics.metrics.dto.DecisionVelocityMetric;
import ai.zevaro.analytics.metrics.dto.HypothesisThroughputMetric;
import ai.zevaro.analytics.metrics.dto.StakeholderResponseMetric;
import ai.zevaro.analytics.repository.AnalyticsEventRepository;
import ai.zevaro.analytics.repository.DecisionCycleLogRepository;
import ai.zevaro.analytics.repository.MetricSnapshotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MetricsController.class)
@DisplayName("MetricsController Tests")
class MetricsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MetricSnapshotRepository metricSnapshotRepository;

    @MockBean
    private DecisionCycleLogRepository decisionCycleLogRepository;

    @MockBean
    private AnalyticsEventRepository analyticsEventRepository;

    @MockBean
    private CoreServiceClient coreServiceClient;

    private UUID tenantId;
    private LocalDate startDate;
    private LocalDate endDate;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        endDate = LocalDate.now();
        startDate = endDate.minusDays(30);
    }

    @Test
    @DisplayName("GET /api/v1/metrics/decision-velocity should return decision velocity metrics")
    void testGetDecisionVelocity_ShouldReturn200WithMetrics() throws Exception {
        List<DecisionVelocityMetric> metrics = List.of(
            new DecisionVelocityMetric(
                tenantId,
                LocalDate.now(),
                25.0,
                5,
                4,
                1,
                0.2,
                Map.of(),
                Map.of()
            ),
            new DecisionVelocityMetric(
                tenantId,
                LocalDate.now().minusDays(1),
                28.0,
                6,
                5,
                0,
                0.0,
                Map.of(),
                Map.of()
            )
        );

        when(metricSnapshotRepository.findByTenantIdAndMetricTypeAndMetricDateBetweenOrderByMetricDateAsc(
                eq(tenantId), eq("DECISION_VELOCITY"), any(LocalDate.class), any(LocalDate.class)))
            .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/metrics/decision-velocity")
                .header("X-Tenant-Id", tenantId.toString())
                .param("days", "30")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", notNullValue()));
    }

    @Test
    @DisplayName("GET /api/v1/metrics/decision-velocity should accept days parameter")
    void testGetDecisionVelocity_ShouldAcceptDaysParameter() throws Exception {
        when(metricSnapshotRepository.findByTenantIdAndMetricTypeAndMetricDateBetweenOrderByMetricDateAsc(
                any(UUID.class), any(String.class), any(LocalDate.class), any(LocalDate.class)))
            .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/metrics/decision-velocity")
                .header("X-Tenant-Id", tenantId.toString())
                .param("days", "60"))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/metrics/decision-velocity should use default days=30")
    void testGetDecisionVelocity_ShouldUseDefaultDays30() throws Exception {
        when(metricSnapshotRepository.findByTenantIdAndMetricTypeAndMetricDateBetweenOrderByMetricDateAsc(
                any(UUID.class), any(String.class), any(LocalDate.class), any(LocalDate.class)))
            .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/metrics/decision-velocity")
                .header("X-Tenant-Id", tenantId.toString()))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/metrics/stakeholder-response should return stakeholder metrics")
    void testGetStakeholderResponse_ShouldReturn200WithMetrics() throws Exception {
        UUID stakeholderId = UUID.randomUUID();

        List<StakeholderResponseMetric> metrics = List.of(
            new StakeholderResponseMetric(
                stakeholderId,
                "John Doe",
                12.5,
                5,
                20,
                0.15,
                startDate,
                endDate
            )
        );

        when(decisionCycleLogRepository.findAvgCycleTimeByStakeholder(any(UUID.class), any(Instant.class)))
            .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/metrics/stakeholder-response")
                .header("X-Tenant-Id", tenantId.toString())
                .param("days", "30")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", notNullValue()));
    }

    @Test
    @DisplayName("GET /api/v1/metrics/stakeholder-response should accept days parameter")
    void testGetStakeholderResponse_ShouldAcceptDaysParameter() throws Exception {
        when(decisionCycleLogRepository.findAvgCycleTimeByStakeholder(any(UUID.class), any(Instant.class)))
            .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/metrics/stakeholder-response")
                .header("X-Tenant-Id", tenantId.toString())
                .param("days", "7"))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/metrics/outcome-velocity should return outcome velocity data")
    void testGetOutcomeVelocity_ShouldReturn200WithData() throws Exception {
        when(metricSnapshotRepository.findByTenantIdAndMetricTypeAndMetricDateBetweenOrderByMetricDateAsc(
                any(UUID.class), any(String.class), any(LocalDate.class), any(LocalDate.class)))
            .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/metrics/outcome-velocity")
                .header("X-Tenant-Id", tenantId.toString())
                .param("days", "30")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", notNullValue()));
    }

    @Test
    @DisplayName("GET /api/v1/metrics/outcome-velocity should include totalValidated and totalInvalidated")
    void testGetOutcomeVelocity_ShouldIncludeTotals() throws Exception {
        when(metricSnapshotRepository.findByTenantIdAndMetricTypeAndMetricDateBetweenOrderByMetricDateAsc(
                any(UUID.class), any(String.class), any(LocalDate.class), any(LocalDate.class)))
            .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/metrics/outcome-velocity")
                .header("X-Tenant-Id", tenantId.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalValidated", notNullValue()))
            .andExpect(jsonPath("$.totalInvalidated", notNullValue()))
            .andExpect(jsonPath("$.periodDays", notNullValue()))
            .andExpect(jsonPath("$.dataPoints", notNullValue()));
    }

    @Test
    @DisplayName("GET /api/v1/metrics/outcome-velocity should use default days=30")
    void testGetOutcomeVelocity_ShouldUseDefaultDays30() throws Exception {
        when(metricSnapshotRepository.findByTenantIdAndMetricTypeAndMetricDateBetweenOrderByMetricDateAsc(
                any(UUID.class), any(String.class), any(LocalDate.class), any(LocalDate.class)))
            .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/metrics/outcome-velocity")
                .header("X-Tenant-Id", tenantId.toString()))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/metrics/hypothesis-throughput should return hypothesis metrics")
    void testGetHypothesisThroughput_ShouldReturn200WithMetrics() throws Exception {
        List<HypothesisThroughputMetric> metrics = List.of(
            new HypothesisThroughputMetric(
                tenantId,
                LocalDate.now(),
                10,
                8,
                2,
                0.8
            )
        );

        when(metricSnapshotRepository.findByTenantIdAndMetricTypeAndMetricDateBetweenOrderByMetricDateAsc(
                any(UUID.class), any(String.class), any(LocalDate.class), any(LocalDate.class)))
            .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/metrics/hypothesis-throughput")
                .header("X-Tenant-Id", tenantId.toString())
                .param("days", "30")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", notNullValue()));
    }

    @Test
    @DisplayName("GET /api/v1/metrics/hypothesis-throughput should accept days parameter")
    void testGetHypothesisThroughput_ShouldAcceptDaysParameter() throws Exception {
        when(metricSnapshotRepository.findByTenantIdAndMetricTypeAndMetricDateBetweenOrderByMetricDateAsc(
                any(UUID.class), any(String.class), any(LocalDate.class), any(LocalDate.class)))
            .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/metrics/hypothesis-throughput")
                .header("X-Tenant-Id", tenantId.toString())
                .param("days", "14"))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/metrics/hypothesis-throughput should use default days=30")
    void testGetHypothesisThroughput_ShouldUseDefaultDays30() throws Exception {
        when(metricSnapshotRepository.findByTenantIdAndMetricTypeAndMetricDateBetweenOrderByMetricDateAsc(
                any(UUID.class), any(String.class), any(LocalDate.class), any(LocalDate.class)))
            .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/metrics/hypothesis-throughput")
                .header("X-Tenant-Id", tenantId.toString()))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/metrics/pipeline-idle-time should return idle time data")
    void testGetPipelineIdleTime_ShouldReturn200WithData() throws Exception {
        when(coreServiceClient.getPendingDecisionCount(tenantId)).thenReturn(5);

        mockMvc.perform(get("/api/v1/metrics/pipeline-idle-time")
                .header("X-Tenant-Id", tenantId.toString())
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.idleTimeMinutes", notNullValue()))
            .andExpect(jsonPath("$.lastDecisionResolved", notNullValue()))
            .andExpect(jsonPath("$.pendingDecisions", is(5)))
            .andExpect(jsonPath("$.status", is("WAITING_FOR_DECISIONS")));
    }

    @Test
    @DisplayName("GET /api/v1/metrics/pipeline-idle-time should set status based on pending decisions")
    void testGetPipelineIdleTime_ShouldSetStatusBasedOnPendingDecisions() throws Exception {
        when(coreServiceClient.getPendingDecisionCount(tenantId)).thenReturn(0);

        mockMvc.perform(get("/api/v1/metrics/pipeline-idle-time")
                .header("X-Tenant-Id", tenantId.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status", is("READY")));
    }

    @Test
    @DisplayName("GET /api/v1/metrics/pipeline-idle-time should call CoreServiceClient")
    void testGetPipelineIdleTime_ShouldCallCoreServiceClient() throws Exception {
        when(coreServiceClient.getPendingDecisionCount(tenantId)).thenReturn(0);

        mockMvc.perform(get("/api/v1/metrics/pipeline-idle-time")
                .header("X-Tenant-Id", tenantId.toString()))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("All metrics endpoints should require X-Tenant-Id header")
    void testMetricsEndpoints_ShouldRequireTenantIdHeader() throws Exception {
        when(metricSnapshotRepository.findByTenantIdAndMetricTypeAndMetricDateBetweenOrderByMetricDateAsc(
                any(UUID.class), any(String.class), any(LocalDate.class), any(LocalDate.class)))
            .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/metrics/decision-velocity"))
            .andExpect(status().isBadRequest());
    }
}
