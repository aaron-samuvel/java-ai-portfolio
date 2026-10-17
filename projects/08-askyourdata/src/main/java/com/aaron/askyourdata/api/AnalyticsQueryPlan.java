package com.aaron.askyourdata.api;

import java.util.List;

/** Provider-neutral query intent; SQL is selected separately from reviewed constants. */
public record AnalyticsQueryPlan(String sourceTable, List<String> dimensions, List<Metric> metrics,
                                 List<Filter> filters, int rowLimit) {
    public AnalyticsQueryPlan {
        dimensions = List.copyOf(dimensions);
        metrics = List.copyOf(metrics);
        filters = List.copyOf(filters);
    }

    public record Metric(String name, List<String> sourceColumns, String aggregation) {
        public Metric { sourceColumns = List.copyOf(sourceColumns); }
    }

    public record Filter(String column, String operator, String value) { }
}
