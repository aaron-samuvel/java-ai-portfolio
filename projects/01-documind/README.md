# DocuMind - private document Q&A with RAG

**Status: initial local application milestone.** Ask questions about uploaded PDFs, Word files, Markdown, HTML and text files. Answers are generated from matching document passages and include source filenames and snippets.

## Problem and users

Students, developers and small teams often need to query a known set of notes or policies without sending those files to a public chatbot. DocuMind indexes a local collection and retrieves relevant passages before generating an answer.

## Learning objectives

- Trace ingestion from multipart upload through extraction, configurable token chunks with bounded word overlap, embeddings and vector storage.
- Compare retrieval results and generated answers as separate stages.
- Carry source metadata through retrieval so answers can show evidence.
- Use local models and protect against instructions embedded in retrieved text.

## Stack

Java 21, Spring Boot 3.5.16, Spring AI 1.1.8, Apache Tika reader, Ollama (`qwen2.5:7b` and `nomic-embed-text`), PostgreSQL with pgvector and Flyway.

## Run

Start PostgreSQL with pgvector and Ollama, pull both models, set `DOCUMIND_DB_URL`, `DOCUMIND_DB_USERNAME` and `DOCUMIND_DB_PASSWORD` if the defaults do not fit, then run `mvn spring-boot:run` from this folder. The upload directory is configurable with `DOCUMIND_STORAGE_DIRECTORY` and defaults to `data/documind/uploads` relative to the process working directory.

Chunking defaults to 700 tokens, 350 minimum characters, at most 2,000 chunks per document, and 40 words of overlap copied from the preceding chunk. Override these with `DOCUMIND_CHUNK_SIZE`, `DOCUMIND_MIN_CHUNK_SIZE_CHARS`, `DOCUMIND_MAX_CHUNKS` and `DOCUMIND_CHUNK_OVERLAP_WORDS`. Overlap is word-count based after the token splitter and preserves page/section metadata from each destination chunk.

## API

- `GET /api/ping` - liveness check.
- `GET /api/collections` - list collections.
- `POST /api/collections` with `{"name":"Study notes"}` - create a collection.
- `POST /api/collections/{id}/documents` as multipart field `file` - upload and asynchronously index a file (maximum 25 MB).
- `GET /api/documents/{id}` - check `PROCESSING`, `INDEXED` or `FAILED` status.
- `POST /api/collections/{id}/ask` with `{"question":"..."}` - return an answer and source records.

Example PowerShell flow:

```powershell
$collection = Invoke-RestMethod -Method Post -Uri http://localhost:8081/api/collections `
  -ContentType 'application/json' -Body '{"name":"Study notes"}'

Invoke-RestMethod -Method Post -Uri "http://localhost:8081/api/collections/$($collection.id)/documents" `
  -Form @{ file = Get-Item .\notes.pdf }

Invoke-RestMethod -Method Post -Uri "http://localhost:8081/api/collections/$($collection.id)/ask" `
  -ContentType 'application/json' -Body '{"question":"What are the key points?"}'
```

An identical file in the same collection returns HTTP 409 and the existing document ID. Questions can use the JSON response or the streaming endpoint described below.

## Web client

From the `frontend` folder, run `npm install` once and then `npm run dev`. Vite proxies `/api` to the Spring server on port 8081. Open the local URL printed by Vite. The client supports creating and selecting collections, uploading files, polling ingestion status and asking questions with source cards.

## Security and limitations

This first milestone is a single-user local learning app. A collection UUID is not an access-control mechanism. Do not expose it to the internet or store sensitive files in it. The API limits file types and upload size, stores files under generated names, filters retrieval to one collection and tells the model to treat document text as data. Tika extraction does not guarantee page-level locations for every file type, so sources currently identify the file and chunk snippet; page values are shown only when a reader provides them.

PostgreSQL/pgvector and Ollama must be running for indexing and question answering. The unit and frontend component tests do not require those services; see the test section below.

## Inspiration and API references

The book points to [Thomas Vitale's Java Spring AI examples](https://github.com/ThomasVitale/llm-apps-java-spring-ai) and the [Spring AI Playground](https://github.com/spring-ai-community/spring-ai-playground). This project uses the documented concepts and does not copy their source code.

- [Spring AI 1.1.8 Tika reader API](https://docs.spring.io/spring-ai/docs/1.1.8/api/org/springframework/ai/reader/tika/TikaDocumentReader.html)
- [Spring AI ETL and text splitting](https://docs.spring.io/spring-ai/reference/api/etl-pipeline.html)
- [Spring AI pgvector reference](https://docs.spring.io/spring-ai/reference/api/vectordbs/pgvector.html)
- [Spring AI Ollama reference](https://docs.spring.io/spring-ai/reference/api/chat/ollama-chat.html)

## Streaming and tests

`GET /api/collections/{id}/ask/stream?q=...` returns Server-Sent Events named `token`, `sources`, and `done`. The `sources` event contains a JSON array with citation number, file name, page when the reader provides one, and a short snippet. The React client renders tokens as they arrive and adds source cards at the end.

Run API tests from this directory with `mvn test`. The tests cover `/api/ping`, unsupported upload rejection, no-retrieval SSE, and feedback persistence without requiring PostgreSQL or Ollama. When Docker is available, a Testcontainers integration test also applies the Flyway migrations against PostgreSQL with pgvector and verifies database constraints; it skips automatically without Docker. Full ingestion/retrieval integration with a live embedding/chat model remains a separate manual check. Run the source-card component test with `npm test` in `frontend`, and build production assets with `npm run build`.

## Exercises

1. Upload the same supported file twice to one collection and confirm the second request returns HTTP 409. Then upload it to a different collection and compare the result.
2. Ask one question whose answer is present and one that is absent. Check that the answer's `[n]` citations match the source cards and that the absent answer uses the fallback text.
3. Change the retrieval `top-k` and similarity threshold in `application.yml`; record how the returned source set changes for a fixed question.

**Current status:** Maven unit tests, the Testcontainers PostgreSQL migration/constraint test, the frontend source-card component test, and the production frontend build pass. A manual PostgreSQL/pgvector/Ollama upload-to-feedback flow also passed. Automated full retrieval/embedding tests, per-user access control, rate limiting, CI execution on GitHub and production Compose hardening remain outstanding. The first milestone is single-user; a collection UUID is not authorization.

