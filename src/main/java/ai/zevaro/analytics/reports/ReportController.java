package ai.zevaro.analytics.reports;

import ai.zevaro.analytics.config.AppConstants;
import ai.zevaro.analytics.reports.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping(AppConstants.API_V1 + "/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listReports(
            @RequestHeader("X-Tenant-Id") UUID tenantId) {
        return ResponseEntity.ok(reportService.listAvailableReports(tenantId));
    }

    @GetMapping("/weekly-digest")
    public ResponseEntity<WeeklyDigestReport> getWeeklyDigest(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart) {

        // Default to start of current week (Monday)
        if (weekStart == null) {
            weekStart = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        }

        return ResponseEntity.ok(reportService.generateWeeklyDigest(tenantId, weekStart));
    }

    @GetMapping("/outcome/{outcomeId}")
    public ResponseEntity<OutcomeReport> getOutcomeReport(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID outcomeId) {
        return ResponseEntity.ok(reportService.generateOutcomeReport(tenantId, outcomeId));
    }
}
