package com.aaron.documind.db;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.sql.DriverManager;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class PostgresMigrationsIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("documind_test").withUsername("documind").withPassword("documind-test");

    @Test
    void migratesFeedbackAndEnforcesDocumentOwnershipAndDeduplication() throws Exception {
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration").load().migrate();
        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            UUID collection = UUID.randomUUID();
            try (var insert = connection.prepareStatement("insert into document_collections(id,name,created_at) values (?,?,?)")) {
                insert.setObject(1, collection); insert.setString(2, "integration"); insert.setObject(3, OffsetDateTime.now()); insert.executeUpdate();
            }
            try (var insert = connection.prepareStatement("insert into documents(id,collection_id,file_name,content_type,sha256,status,storage_key,created_at) values (?,?,?,?,?,?,?,?)")) {
                insert.setObject(1, UUID.randomUUID()); insert.setObject(2, collection); insert.setString(3, "note.md");
                insert.setString(4, "text/markdown"); insert.setString(5, "a".repeat(64)); insert.setString(6, "INDEXED");
                insert.setString(7, UUID.randomUUID().toString()); insert.setObject(8, OffsetDateTime.now()); insert.executeUpdate();
            }
            assertThrows(java.sql.SQLException.class, () -> {
                try (var insert = connection.prepareStatement("insert into documents(id,collection_id,file_name,content_type,sha256,status,storage_key,created_at) values (?,?,?,?,?,?,?,?)")) {
                    insert.setObject(1, UUID.randomUUID()); insert.setObject(2, collection); insert.setString(3, "duplicate.md");
                    insert.setString(4, "text/markdown"); insert.setString(5, "a".repeat(64)); insert.setString(6, "INDEXED");
                    insert.setString(7, UUID.randomUUID().toString()); insert.setObject(8, OffsetDateTime.now()); insert.executeUpdate();
                }
            });
            try (var insert = connection.prepareStatement("insert into answer_feedback(id,collection_id,question,answer,sources_json,rating,created_at) values (?,?,?,?,?,?,?)")) {
                insert.setObject(1, UUID.randomUUID()); insert.setObject(2, collection); insert.setString(3, "What is covered?");
                insert.setString(4, "Testing"); insert.setString(5, "[]"); insert.setString(6, "UP"); insert.setObject(7, OffsetDateTime.now());
                assertEquals(1, insert.executeUpdate());
            }
        }
    }
}
