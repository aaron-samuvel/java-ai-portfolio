package com.aaron.toolbridge.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/** Read-only, parameterized access to one seeded local analytics table. */
@Component
public class SqlAnalyticsTools {
    private final JdbcTemplate jdbc;

    public SqlAnalyticsTools(@Qualifier("toolbridgeReadOnlyJdbcTemplate") JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Tool(name = "describe_sample_sales_schema", description = "Describe the only local SQL table exposed to MCP; returns table and permitted columns")
    public Schema describeSchema() {
        return new Schema("sample_sales", List.of("id", "region", "product", "units", "revenue"),
                "Read-only sample data. No other tables or SQL statements are exposed.");
    }

    @Tool(name = "query_sample_sales", description = "Read up to 25 rows from the allow-listed local sample_sales table, optionally filtered by exact region")
    public List<SalesRow> querySales(
            @ToolParam(description = "Optional exact region filter; empty string returns all regions; maximum 40 characters") String region,
            @ToolParam(description = "Maximum rows, from 1 to 25") int limit) {
        if (region == null || region.length() > 40 || region.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("region must be empty or contain at most 40 printable characters");
        }
        if (limit < 1 || limit > 25) throw new IllegalArgumentException("limit must be between 1 and 25");
        String sql = region.isBlank()
                ? "SELECT id, region, product, units, revenue FROM sample_sales ORDER BY id LIMIT ?"
                : "SELECT id, region, product, units, revenue FROM sample_sales WHERE region = ? ORDER BY id LIMIT ?";
        return region.isBlank()
                ? jdbc.query(sql, (rs, row) -> new SalesRow(rs.getLong("id"), rs.getString("region"),
                    rs.getString("product"), rs.getInt("units"), rs.getBigDecimal("revenue")), limit)
                : jdbc.query(sql, (rs, row) -> new SalesRow(rs.getLong("id"), rs.getString("region"),
                    rs.getString("product"), rs.getInt("units"), rs.getBigDecimal("revenue")), region, limit);
    }

    public record Schema(String table, List<String> columns, String policy) { }
    public record SalesRow(long id, String region, String product, int units, java.math.BigDecimal revenue) { }
}
