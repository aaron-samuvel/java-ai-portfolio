package com.aaron.askyourdata.api;

import java.util.Map;
import java.util.Set;

/** Small semantic layer describing which sample_sales fields the planner may use. */
public final class SemanticCatalog {
    public static final String TABLE = "sample_sales";
    public static final int MAX_ROWS = 20;
    public static final Map<String, Column> COLUMNS = Map.of(
            "id", new Column("BIGINT", "Synthetic row identifier; not a report dimension."),
            "region", new Column("VARCHAR", "Synthetic sales region; supported grouping dimension."),
            "product", new Column("VARCHAR", "Synthetic product category; supported grouping dimension."),
            "amount", new Column("DECIMAL", "Synthetic unit price in unspecified demo currency."),
            "quantity", new Column("INTEGER", "Units sold in the synthetic row."),
            "order_date", new Column("DATE", "Synthetic order date; available for future bounded date filters."));
    public static final Set<String> DIMENSIONS = Set.of("product", "region");
    public static final Map<String, Metric> METRICS = Map.of(
            "revenue", new Metric(Set.of("amount", "quantity"), "SUM_PRODUCT", "Sum of unit price multiplied by quantity."));

    private SemanticCatalog() { }

    public record Column(String type, String description) { }
    public record Metric(Set<String> sourceColumns, String aggregation, String description) { }
}
