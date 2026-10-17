CREATE TABLE answer_feedback (
    id UUID PRIMARY KEY,
    collection_id UUID NOT NULL REFERENCES document_collections(id) ON DELETE CASCADE,
    question TEXT NOT NULL,
    answer TEXT NOT NULL,
    sources_json TEXT NOT NULL,
    rating VARCHAR(12) NOT NULL CHECK (rating IN ('UP', 'DOWN')),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_answer_feedback_collection_created
    ON answer_feedback (collection_id, created_at DESC);
