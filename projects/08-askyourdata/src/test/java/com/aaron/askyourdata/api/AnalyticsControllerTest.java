package com.aaron.askyourdata.api;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class AnalyticsControllerTest {
    @Test void choosesAFixedReadOnlyQueryFromQuestionCategory() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class))).thenReturn(List.of());
        var result = new AnalyticsController(jdbc).answer(new AnalyticsController.Question("show revenue by region"));
        assertEquals("sales_by_region", result.report());
        assertEquals("sample_sales", result.queryPlan().sourceTable());
        assertEquals(List.of("region"), result.queryPlan().dimensions());
        assertEquals("revenue", result.queryPlan().metrics().getFirst().name());
        assertEquals(List.of("amount", "quantity"), result.queryPlan().metrics().getFirst().sourceColumns());
        assertEquals(20, result.queryPlan().rowLimit());
        assertTrue(result.executedSql().startsWith("SELECT"));
        assertFalse(result.executedSql().contains("DROP"));
        verify(jdbc).query(contains("GROUP BY region"), any(org.springframework.jdbc.core.RowMapper.class));
    }
}
