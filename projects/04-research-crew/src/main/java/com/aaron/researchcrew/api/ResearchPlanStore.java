package com.aaron.researchcrew.api;

import java.util.List;

/** Stores plans that have not yet been approved, independently of graph checkpoints. */
public interface ResearchPlanStore {
    List<Draft> findPending();
    void savePending(Draft draft);
    void markApproved(String id);

    record Draft(String id, String topic, int revisions) { }
}
