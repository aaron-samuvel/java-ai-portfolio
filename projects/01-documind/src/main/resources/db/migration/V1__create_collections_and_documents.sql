CREATE TABLE document_collections (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE documents (
    id UUID PRIMARY KEY,
    collection_id UUID NOT NULL REFERENCES document_collections(id) ON DELETE CASCADE,
    file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(120) NOT NULL,
    sha256 VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL,
    storage_key VARCHAR(80) NOT NULL,
    error_message VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_documents_collection_sha256 UNIQUE (collection_id, sha256)
);

CREATE INDEX ix_documents_collection_created ON documents(collection_id, created_at DESC);

