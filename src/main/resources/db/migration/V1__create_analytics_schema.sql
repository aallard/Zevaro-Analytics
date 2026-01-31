-- Create analytics schema
CREATE SCHEMA IF NOT EXISTS analytics;

-- Metric snapshots (time-series)
CREATE TABLE analytics.metric_snapshots (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    metric_type VARCHAR(50) NOT NULL,
    metric_date DATE NOT NULL,
    value DECIMAL(15,4) NOT NULL,
    dimensions JSONB DEFAULT '{}',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(tenant_id, metric_type, metric_date)
);

CREATE INDEX idx_metric_snapshots_tenant_type_date
ON analytics.metric_snapshots(tenant_id, metric_type, metric_date);

-- Decision cycle time log (for detailed analysis)
CREATE TABLE analytics.decision_cycle_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    decision_id UUID NOT NULL,
    created_at TIMESTAMP NOT NULL,
    resolved_at TIMESTAMP NOT NULL,
    cycle_time_hours DECIMAL(10,2) NOT NULL,
    priority VARCHAR(20),
    decision_type VARCHAR(50),
    was_escalated BOOLEAN DEFAULT FALSE,
    stakeholder_id UUID
);

CREATE INDEX idx_decision_cycle_tenant_resolved
ON analytics.decision_cycle_log(tenant_id, resolved_at);

CREATE INDEX idx_decision_cycle_stakeholder
ON analytics.decision_cycle_log(stakeholder_id);

-- Pre-computed reports
CREATE TABLE analytics.reports (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    report_type VARCHAR(50) NOT NULL,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    data JSONB NOT NULL,
    generated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_reports_tenant_type
ON analytics.reports(tenant_id, report_type, period_start, period_end);
