package com.aaron.toolbridge.tools;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataAccessException;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class SqlAnalyticsToolsTest {
    @Autowired private SqlAnalyticsTools tools;
    @Autowired @Qualifier("toolbridgeReadOnlyJdbcTemplate") private JdbcTemplate readOnlyJdbc;

    @Test
    void exposesOnlyDocumentedSchemaAndBoundedParameterizedRows() {
        var schema = tools.describeSchema();
        assertEquals("sample_sales", schema.table());
        assertEquals(5, schema.columns().size());
        assertEquals(2, tools.querySales("North", 25).size());
        assertEquals(3, tools.querySales("", 3).size());
    }

    @Test
    void rejectsUnboundedOrControlInputsAndTreatsFilterAsData() {
        assertTrue(tools.querySales("North' OR 1=1 --", 25).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> tools.querySales("North", 26));
        assertThrows(IllegalArgumentException.class, () -> tools.querySales("North\nSouth", 10));
        assertThrows(IllegalArgumentException.class, () -> tools.querySales(null, 10));
    }

    @Test
    void databaseEnforcesSelectOnlyForTheMcpReaderAccount() {
        assertThrows(DataAccessException.class, () -> readOnlyJdbc.update(
                "INSERT INTO sample_sales (id, region, product, units, revenue) VALUES (99, 'North', 'X', 1, 1.00)"));
        assertThrows(DataAccessException.class,
                () -> readOnlyJdbc.update("UPDATE sample_sales SET units = 0 WHERE id = ?", 1));
        assertThrows(DataAccessException.class,
                () -> readOnlyJdbc.update("DELETE FROM sample_sales WHERE id = ?", 1));
        assertThrows(DataAccessException.class, () -> readOnlyJdbc.execute("DROP TABLE sample_sales"));
        assertEquals(5, tools.querySales("", 25).size());
    }
}
