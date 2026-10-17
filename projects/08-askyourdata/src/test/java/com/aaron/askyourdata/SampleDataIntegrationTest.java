package com.aaron.askyourdata;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import com.aaron.askyourdata.api.AnalyticsFeedbackController;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class SampleDataIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired AnalyticsFeedbackController feedback;

    @Test void initializesTenThousandSyntheticSalesRows() {
        assertEquals(10_000, jdbc.queryForObject("SELECT COUNT(*) FROM sample_sales", Integer.class));
        assertEquals(5, jdbc.queryForObject("SELECT COUNT(DISTINCT product) FROM sample_sales", Integer.class));
        assertEquals(4, jdbc.queryForObject("SELECT COUNT(DISTINCT region) FROM sample_sales", Integer.class));
    }

    @Test void recordsBoundedQueryFeedback() {
        var receipt = feedback.submit(new AnalyticsFeedbackController.Feedback("sales_summary", "DOWN", "Summary is unclear"));
        assertTrue(receipt.id() > 0);
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM analytics_feedback WHERE report_id='sales_summary' AND rating='DOWN'", Integer.class));
    }
}
