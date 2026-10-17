package com.aaron.askyourdata.api;

import org.junit.jupiter.api.Test;

import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class QueryPlanValidatorTest {
    private final QueryPlanValidator validator = new QueryPlanValidator();

    @Test void acceptsAReviewedRevenuePlanAndItsMatchingReport() {
        var plan = plan("sample_sales", List.of("product"), revenue(), List.of(), 20);
        assertTrue(validator.violations(plan).isEmpty());
        assertDoesNotThrow(() -> validator.requireReportCompatible("sales_by_product", plan));
    }

    @Test void rejectsUnreviewedTablesColumnsMetricsAndFilters() {
        assertTrue(validator.violations(plan("customer_private", List.of("product"), revenue(), List.of(), 20))
                .contains("source_not_allowed"));
        assertTrue(validator.violations(plan("sample_sales", List.of("id"), revenue(), List.of(), 20))
                .contains("dimension_not_selectable"));
        assertTrue(validator.violations(plan("sample_sales", List.of("secret_column"), revenue(), List.of(), 20))
                .contains("unknown_dimension"));
        var badMetric = new AnalyticsQueryPlan.Metric("revenue", List.of("amount"), "SUM");
        assertTrue(validator.violations(plan("sample_sales", List.of("product"), badMetric, List.of(), 20))
                .contains("metric_columns_not_allowed"));
        assertTrue(validator.violations(plan("sample_sales", List.of("product"), revenue(),
                List.of(new AnalyticsQueryPlan.Filter("region", "EQ", "North")), 20)).contains("filters_not_supported"));
    }

    @Test void boundsRowsAndRequiresThePlanToMatchItsReport() {
        var oversized = plan("sample_sales", List.of("region"), revenue(), List.of(), 21);
        assertTrue(validator.violations(oversized).contains("row_limit_out_of_range"));
        assertThrows(IllegalArgumentException.class,
                () -> validator.requireReportCompatible("sales_by_region", plan("sample_sales", List.of("product"), revenue(), List.of(), 20)));
        assertThrows(IllegalArgumentException.class,
                () -> validator.requireReportCompatible("unlisted_report", plan("sample_sales", List.of(), revenue(), List.of(), 1)));
    }

    @Test void summaryPlansAreBoundToOneRow() {
        var plan = plan("sample_sales", List.of(), revenue(), List.of(), 1);
        assertTrue(validator.violations(plan).isEmpty());
        assertTrue(validator.violations(plan("sample_sales", List.of(), revenue(), List.of(), 20))
                .contains("row_limit_does_not_match_report"));
    }

    private static AnalyticsQueryPlan plan(String source, List<String> dimensions,
                                           AnalyticsQueryPlan.Metric metric,
                                           List<AnalyticsQueryPlan.Filter> filters, int limit) {
        return new AnalyticsQueryPlan(source, dimensions, List.of(metric), filters, limit);
    }

    private static AnalyticsQueryPlan.Metric revenue() {
        return new AnalyticsQueryPlan.Metric("revenue", List.of("amount", "quantity"), "SUM_PRODUCT");
    }
}
