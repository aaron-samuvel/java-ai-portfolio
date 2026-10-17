package com.aaron.askyourdata.api;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/** Maps supported natural-language intent into a structured semantic plan, never generated SQL. */
@Component
public class AnalyticsQueryPlanner {
    public AnalyticsQueryPlanner() { }

    public PlannedQuery plan(String question) {
        String text = question.toLowerCase(Locale.ROOT);
        String report = text.contains("product") ? "sales_by_product" :
                text.contains("region") ? "sales_by_region" : "sales_summary";
        List<String> dimensions = switch (report) {
            case "sales_by_product" -> List.of("product");
            case "sales_by_region" -> List.of("region");
            default -> List.of();
        };
        var revenue = new AnalyticsQueryPlan.Metric("revenue", List.of("amount", "quantity"), "SUM_PRODUCT");
        int rowLimit = dimensions.isEmpty() ? 1 : SemanticCatalog.MAX_ROWS;
        var plan = new AnalyticsQueryPlan(SemanticCatalog.TABLE, dimensions, List.of(revenue), List.of(), rowLimit);
        return new PlannedQuery(report, plan);
    }

    public record PlannedQuery(String reportId, AnalyticsQueryPlan plan) { }
}
