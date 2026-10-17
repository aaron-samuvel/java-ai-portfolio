package com.aaron.voicenotes.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

@Repository
public class TranscriptJobRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public TranscriptJobRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc; this.mapper = mapper;
    }

    @PostConstruct
    void markInterruptedJobs() {
        jdbc.update("update transcript_job set status = 'FAILED', updated_at = current_timestamp, error_message = ? where status in ('QUEUED', 'PROCESSING')",
                "Processing was interrupted; audio was not retained. Submit the recording again.");
    }

    public long count() {
        Long count = jdbc.queryForObject("select count(*) from transcript_job", Long.class);
        return count == null ? 0 : count;
    }

    public void insert(TranscriptJobService.Job job) {
        jdbc.update("insert into transcript_job(id, status, created_at, updated_at, result_json, error_message) values (?, ?, ?, ?, ?, ?)",
                job.id(), job.status(), Timestamp.from(job.createdAt()), Timestamp.from(job.updatedAt()), json(job.result()), job.error());
    }

    public boolean update(TranscriptJobService.Job job) {
        return jdbc.update("update transcript_job set status = ?, updated_at = ?, result_json = ?, error_message = ? where id = ?",
                job.status(), Timestamp.from(job.updatedAt()), json(job.result()), job.error(), job.id()) == 1;
    }

    public Optional<TranscriptJobService.Job> find(String id) {
        List<TranscriptJobService.Job> rows = jdbc.query("select * from transcript_job where id = ?", (rs, row) -> map(rs.getString("id"),
                rs.getString("status"), rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant(),
                rs.getString("result_json"), rs.getString("error_message")), id);
        return rows.stream().findFirst();
    }

    public List<TranscriptJobService.Job> all() {
        return jdbc.query("select * from transcript_job order by updated_at desc", (rs, row) -> map(rs.getString("id"),
                rs.getString("status"), rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant(),
                rs.getString("result_json"), rs.getString("error_message")));
    }

    public boolean delete(String id) { return jdbc.update("delete from transcript_job where id = ?", id) == 1; }

    private TranscriptJobService.Job map(String id, String status, java.time.Instant created, java.time.Instant updated,
                                         String resultJson, String error) {
        TranscriptJobService.TranscriptResult result = null;
        if (resultJson != null) {
            try { result = mapper.readValue(resultJson, TranscriptJobService.TranscriptResult.class); }
            catch (JsonProcessingException e) { throw new IllegalStateException("Stored transcript result is invalid", e); }
        }
        return new TranscriptJobService.Job(id, status, created, updated, result, error);
    }

    private String json(TranscriptJobService.TranscriptResult result) {
        if (result == null) return null;
        try { return mapper.writeValueAsString(result); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Could not store transcript result", e); }
    }
}
