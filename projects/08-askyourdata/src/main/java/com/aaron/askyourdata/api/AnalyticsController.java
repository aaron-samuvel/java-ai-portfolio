package com.aaron.askyourdata.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {
    private static final String BY_PRODUCT = "SELECT product AS label, SUM(amount * quantity) AS revenue FROM sample_sales GROUP BY product ORDER BY revenue DESC LIMIT 20";
    private static final String BY_REGION = "SELECT region AS label, SUM(amount * quantity) AS revenue FROM sample_sales GROUP BY region ORDER BY revenue DESC LIMIT 20";
    private static final String SUMMARY = "SELECT 'Total' AS label, SUM(amount * quantity) AS revenue FROM sample_sales LIMIT 1";
    private final JdbcTemplate jdbc;
    private final AnalyticsQueryPlanner planner;
    private final QueryPlanValidator planValidator;
    private final ReadOnlySqlPolicy sqlPolicy = new ReadOnlySqlPolicy(java.util.Set.of(BY_PRODUCT, BY_REGION, SUMMARY));
    public AnalyticsController(JdbcTemplate jdbc) {
        this(jdbc, new AnalyticsQueryPlanner(), new QueryPlanValidator());
    }
    @Autowired
    public AnalyticsController(JdbcTemplate jdbc, AnalyticsQueryPlanner planner, QueryPlanValidator planValidator) {
        this.jdbc = jdbc;
        this.planner = planner;
        this.planValidator = planValidator;
    }

    @PostMapping
    @Transactional(readOnly = true)
    public Result answer(@Valid @RequestBody Question q) {
        var planned = planner.plan(q.question());
        String report = planned.reportId();
        AnalyticsQueryPlan queryPlan = planned.plan();
        planValidator.requireValid(queryPlan);
        planValidator.requireReportCompatible(report, queryPlan);
        String sql = switch (report) { case "sales_by_product" -> BY_PRODUCT; case "sales_by_region" -> BY_REGION; default -> SUMMARY; };
        if (!sqlPolicy.allows(sql)) throw new IllegalStateException("Selected report is not permitted by the SQL policy.");
        List<Row> rows = jdbc.query(sql, (rs, row) -> new Row(rs.getString("label"), rs.getBigDecimal("revenue")));
        return new Result(report, queryPlan, rows, sql, "bar",
                "A structured plan was checked against the semantic schema before selecting fixed SQL. Synthetic sample_sales data only.");
    }

    public record Question(@NotBlank @Size(max=500) String question) { }
    public record Row(String label, java.math.BigDecimal revenue) { }
    public record Result(String report, AnalyticsQueryPlan queryPlan, List<Row> rows, String executedSql,
                         String chartType, String notice) { }
}
