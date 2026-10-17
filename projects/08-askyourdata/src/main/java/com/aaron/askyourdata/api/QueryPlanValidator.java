package com.aaron.askyourdata.api;

import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

/** Checks source, selected columns, measures, filters and row bounds before SQL selection. */
@Component
public class QueryPlanValidator {
    public Set<String> violations(AnalyticsQueryPlan plan) {
        Set<String> issues = new HashSet<>();
        if (plan == null) return Set.of("plan_missing");
        if (!SemanticCatalog.TABLE.equals(plan.sourceTable())) issues.add("source_not_allowed");
        if (plan.dimensions() == null || plan.dimensions().size() > 2) issues.add("invalid_dimensions");
        else {
            Set<String> seen = new HashSet<>();
            for (String dimension : plan.dimensions()) {
                if (dimension == null || !SemanticCatalog.COLUMNS.containsKey(dimension)) issues.add("unknown_dimension");
                else if (!SemanticCatalog.DIMENSIONS.contains(dimension)) issues.add("dimension_not_selectable");
                if (dimension != null && !seen.add(dimension)) issues.add("duplicate_dimension");
            }
        }
        if (plan.metrics() == null || plan.metrics().isEmpty() || plan.metrics().size() > 3) issues.add("invalid_metrics");
        else for (var metric : plan.metrics()) {
            if (metric == null) { issues.add("invalid_metric"); continue; }
            var definition = metric.name() == null ? null : SemanticCatalog.METRICS.get(metric.name());
            if (definition == null) { issues.add("metric_not_allowed"); continue; }
            if (!definition.sourceColumns().equals(Set.copyOf(metric.sourceColumns())) ||
                    !definition.aggregation().equals(metric.aggregation())) issues.add("metric_columns_not_allowed");
        }
        if (plan.filters() == null || !plan.filters().isEmpty()) issues.add("filters_not_supported");
        if (plan.rowLimit() < 1 || plan.rowLimit() > SemanticCatalog.MAX_ROWS) issues.add("row_limit_out_of_range");
        if (plan.dimensions() != null) {
            int requiredLimit = plan.dimensions().isEmpty() ? 1 : SemanticCatalog.MAX_ROWS;
            if (plan.rowLimit() != requiredLimit) issues.add("row_limit_does_not_match_report");
        }
        return Set.copyOf(issues);
    }

    public void requireReportCompatible(String reportId, AnalyticsQueryPlan plan) {
        Set<String> expected = switch (reportId) {
            case "sales_by_product" -> Set.of("product");
            case "sales_by_region" -> Set.of("region");
            case "sales_summary" -> Set.of();
            default -> throw new IllegalArgumentException("Report is not present in the semantic schema.");
        };
        if (!expected.equals(Set.copyOf(plan.dimensions()))) {
            throw new IllegalArgumentException("Query plan dimensions do not match the selected report.");
        }
    }

    public void requireValid(AnalyticsQueryPlan plan) {
        Set<String> issues = violations(plan);
        if (!issues.isEmpty()) throw new IllegalArgumentException("Query plan violates the semantic schema: " + String.join(",", issues));
    }
}
