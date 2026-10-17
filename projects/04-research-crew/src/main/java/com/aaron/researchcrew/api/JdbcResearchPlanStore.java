package com.aaron.researchcrew.api;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/** File-backed H2 storage for pending drafts, so human approval can happen after restart. */
@Repository
public class JdbcResearchPlanStore implements ResearchPlanStore {
    private final JdbcTemplate jdbc;

    public JdbcResearchPlanStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        jdbc.execute("CREATE TABLE IF NOT EXISTS research_plan_draft (" +
                "id VARCHAR(36) PRIMARY KEY, topic VARCHAR(300) NOT NULL, revisions INTEGER NOT NULL, " +
                "status VARCHAR(24) NOT NULL, updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP)");
    }

    @Override
    public List<Draft> findPending() {
        return jdbc.query("SELECT id, topic, revisions FROM research_plan_draft WHERE status='PENDING_APPROVAL' ORDER BY updated_at",
                (rs, row) -> new Draft(rs.getString("id"), rs.getString("topic"), rs.getInt("revisions")));
    }

    @Override
    public void savePending(Draft draft) {
        jdbc.update("MERGE INTO research_plan_draft (id, topic, revisions, status, updated_at) KEY(id) VALUES (?, ?, ?, 'PENDING_APPROVAL', CURRENT_TIMESTAMP)",
                draft.id(), draft.topic(), draft.revisions());
    }

    @Override
    public void markApproved(String id) {
        jdbc.update("UPDATE research_plan_draft SET status='APPROVED', updated_at=CURRENT_TIMESTAMP WHERE id=? AND status='PENDING_APPROVAL'", id);
    }
}
