package com.aaron.fraudguard.api;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import static org.junit.jupiter.api.Assertions.*;

class FraudReviewStoreTest {
    private final DatabaseHolder database = new DatabaseHolder();
    private final FraudReviewStore store = new FraudReviewStore(new JdbcTemplate(database.db));

    @AfterEach void close() { database.db.shutdown(); }

    @Test void keepsHighRiskReviewUntilAnAnalystLabelsIt() {
        long id = store.record(new ScoringController.Transaction(900, 12, 1, true), 84, "HIGH");
        assertEquals(1, store.openReviewQueue(500).size());
        assertEquals(id, store.openReviewQueue(1).get(0).id());
        assertTrue(store.label(id, "FRAUD"));
        assertFalse(store.label(id, "LEGITIMATE"));
        assertTrue(store.openReviewQueue(50).isEmpty());
    }

    private static class DatabaseHolder {
        final org.springframework.jdbc.datasource.embedded.EmbeddedDatabase db = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2).addScript("classpath:schema.sql").build();
    }
}
