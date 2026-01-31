package ai.zevaro.analytics.insights;

import ai.zevaro.analytics.config.AppConstants;
import ai.zevaro.analytics.insights.dto.*;
import ai.zevaro.analytics.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class InsightsService {

    private final MetricSnapshotRepository snapshotRepository;
    private final DecisionCycleLogRepository cycleLogRepository;

    private static final double SIGNIFICANCE_THRESHOLD = 10.0;  // 10% change

    @Cacheable(value = AppConstants.CACHE_METRICS, key = "'insights:' + #tenantId")
    public List<Insight> generateInsights(UUID tenantId) {
        var insights = new ArrayList<Insight>();

        // Analyze decision velocity trend
        var decisionTrend = analyzeDecisionVelocityTrend(tenantId);
        if (decisionTrend.isSignificant()) {
            insights.add(createTrendInsight(decisionTrend));
        }

        // Check for bottlenecks
        var bottleneckInsight = detectBottlenecks(tenantId);
        if (bottleneckInsight != null) {
            insights.add(bottleneckInsight);
        }

        // Check for achievements
        var achievementInsight = detectAchievements(tenantId);
        if (achievementInsight != null) {
            insights.add(achievementInsight);
        }

        return insights;
    }

    public List<Trend> detectTrends(UUID tenantId) {
        var trends = new ArrayList<Trend>();

        trends.add(analyzeDecisionVelocityTrend(tenantId));
        trends.add(analyzeOutcomeVelocityTrend(tenantId));

        return trends;
    }

    public List<String> getRecommendations(UUID tenantId) {
        var recommendations = new ArrayList<String>();
        var thirtyDaysAgo = Instant.now().minus(30, ChronoUnit.DAYS);

        // Check average cycle time
        var avgCycleTime = cycleLogRepository.findAvgCycleTimeSince(tenantId, thirtyDaysAgo);
        if (avgCycleTime != null && avgCycleTime > 48) {
            recommendations.add("Consider breaking down complex decisions into smaller, time-boxed choices");
        }

        // Check escalation rate
        var escalatedCount = cycleLogRepository.countEscalatedSince(tenantId, thirtyDaysAgo);
        var logs = cycleLogRepository.findByTenantIdAndResolvedAtBetween(
            tenantId, thirtyDaysAgo, Instant.now());

        if (!logs.isEmpty()) {
            var escalationRate = (double) escalatedCount / logs.size();
            if (escalationRate > 0.2) {
                recommendations.add("High escalation rate detected. Review stakeholder availability and SLA settings");
            }
        }

        // Default recommendations
        if (recommendations.isEmpty()) {
            recommendations.add("Continue monitoring decision velocity trends");
            recommendations.add("Consider setting up weekly digest reports for stakeholders");
        }

        return recommendations;
    }

    private Trend analyzeDecisionVelocityTrend(UUID tenantId) {
        var endDate = LocalDate.now();
        var midDate = endDate.minusDays(15);
        var startDate = endDate.minusDays(30);

        var firstHalf = snapshotRepository
            .findByTenantIdAndMetricTypeAndMetricDateBetweenOrderByMetricDateAsc(
                tenantId, AppConstants.METRIC_DECISION_VELOCITY, startDate, midDate);

        var secondHalf = snapshotRepository
            .findByTenantIdAndMetricTypeAndMetricDateBetweenOrderByMetricDateAsc(
                tenantId, AppConstants.METRIC_DECISION_VELOCITY, midDate, endDate);

        var firstAvg = firstHalf.stream()
            .mapToDouble(s -> s.getValue().doubleValue())
            .average()
            .orElse(0.0);

        var secondAvg = secondHalf.stream()
            .mapToDouble(s -> s.getValue().doubleValue())
            .average()
            .orElse(0.0);

        var percentChange = firstAvg > 0 ? ((secondAvg - firstAvg) / firstAvg) * 100 : 0.0;
        var direction = percentChange < -5 ? TrendDirection.DOWN
            : percentChange > 5 ? TrendDirection.UP
            : TrendDirection.STABLE;

        return new Trend(
            "Decision Velocity",
            direction,
            percentChange,
            30,
            Math.abs(percentChange) >= SIGNIFICANCE_THRESHOLD
        );
    }

    private Trend analyzeOutcomeVelocityTrend(UUID tenantId) {
        var endDate = LocalDate.now();
        var midDate = endDate.minusDays(15);
        var startDate = endDate.minusDays(30);

        var firstHalf = snapshotRepository
            .findByTenantIdAndMetricTypeAndMetricDateBetweenOrderByMetricDateAsc(
                tenantId, AppConstants.METRIC_OUTCOME_VELOCITY, startDate, midDate);

        var secondHalf = snapshotRepository
            .findByTenantIdAndMetricTypeAndMetricDateBetweenOrderByMetricDateAsc(
                tenantId, AppConstants.METRIC_OUTCOME_VELOCITY, midDate, endDate);

        var firstSum = firstHalf.stream().mapToInt(s -> s.getValue().intValue()).sum();
        var secondSum = secondHalf.stream().mapToInt(s -> s.getValue().intValue()).sum();

        var percentChange = firstSum > 0 ? ((double)(secondSum - firstSum) / firstSum) * 100 : 0.0;
        var direction = percentChange < -5 ? TrendDirection.DOWN
            : percentChange > 5 ? TrendDirection.UP
            : TrendDirection.STABLE;

        return new Trend(
            "Outcome Velocity",
            direction,
            percentChange,
            30,
            Math.abs(percentChange) >= SIGNIFICANCE_THRESHOLD
        );
    }

    private Insight createTrendInsight(Trend trend) {
        var title = trend.direction() == TrendDirection.UP
            ? trend.metricName() + " is improving"
            : trend.metricName() + " is declining";

        var description = String.format("%s has changed by %.1f%% over the last %d days",
            trend.metricName(), Math.abs(trend.percentChange()), trend.periodDays());

        var recommendation = trend.direction() == TrendDirection.UP
            ? "Keep up the momentum. Consider sharing best practices across teams."
            : "Review recent process changes that may have impacted velocity.";

        return new Insight(
            InsightType.TREND,
            title,
            description,
            recommendation,
            0.8,
            Instant.now()
        );
    }

    private Insight detectBottlenecks(UUID tenantId) {
        var thirtyDaysAgo = Instant.now().minus(30, ChronoUnit.DAYS);
        var stakeholderData = cycleLogRepository.findAvgCycleTimeByStakeholder(tenantId, thirtyDaysAgo);

        if (stakeholderData.isEmpty()) return null;

        // Find stakeholders with significantly higher cycle times
        var avgAll = stakeholderData.stream()
            .mapToDouble(row -> ((Number) row[1]).doubleValue())
            .average()
            .orElse(0.0);

        var slowStakeholders = stakeholderData.stream()
            .filter(row -> ((Number) row[1]).doubleValue() > avgAll * 1.5)
            .count();

        if (slowStakeholders > 0) {
            return new Insight(
                InsightType.BOTTLENECK,
                "Stakeholder response bottleneck detected",
                String.format("%d stakeholders have response times 50%% above average", slowStakeholders),
                "Consider redistributing decisions or adjusting SLA targets for affected stakeholders",
                0.7,
                Instant.now()
            );
        }

        return null;
    }

    private Insight detectAchievements(UUID tenantId) {
        var sevenDaysAgo = Instant.now().minus(7, ChronoUnit.DAYS);
        var logs = cycleLogRepository.findByTenantIdAndResolvedAtBetween(
            tenantId, sevenDaysAgo, Instant.now());

        if (logs.size() >= 10) {
            var avgCycleTime = logs.stream()
                .mapToDouble(l -> l.getCycleTimeHours().doubleValue())
                .average()
                .orElse(0.0);

            if (avgCycleTime < 24) {
                return new Insight(
                    InsightType.ACHIEVEMENT,
                    "Excellent decision velocity!",
                    String.format("Your team resolved %d decisions this week with an average cycle time under 24 hours", logs.size()),
                    "Consider documenting your current process as a best practice",
                    0.9,
                    Instant.now()
                );
            }
        }

        return null;
    }
}
