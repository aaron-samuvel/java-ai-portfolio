package com.aaron.askyourdata.api;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ReadOnlySqlPolicyTest {
    private static final String APPROVED = "SELECT product AS label, SUM(amount * quantity) AS revenue FROM sample_sales GROUP BY product ORDER BY revenue DESC LIMIT 20";
    private final ReadOnlySqlPolicy policy = new ReadOnlySqlPolicy(Set.of(APPROVED));

    @Test void allowsOnlyReviewedSingleTableSelect() {
        assertTrue(policy.allows(APPROVED));
        assertTrue(policy.allows(APPROVED.toLowerCase().replace("  ", " ")));
        assertFalse(policy.allows("DELETE FROM sample_sales"));
        assertFalse(policy.allows(APPROVED + "; DROP TABLE sample_sales"));
        assertFalse(policy.allows("SELECT * FROM other_table"));
    }
}
