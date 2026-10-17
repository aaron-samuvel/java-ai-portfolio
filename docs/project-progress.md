# Project progress

Updated 18 October 2026.

## Completed in the current milestone

- [x] Confirmed the 87-page book's structure and all twelve project titles.
- [x] Inspected the public GitHub profile and determined no public repository is an obvious host for this portfolio.
- [x] Checked local Java, Maven, Node, Git, Docker, Gradle and Ollama availability.
- [x] Created the repository foundation and book coverage map.
- [x] Added DocuMind's API source for collections, upload validation and duplicate detection, asynchronous ingestion, chunk metadata, collection-filtered answers and source records.
- [x] Added a React/Vite client for collections, uploads, indexing status and questions.
- [x] Added token/source/done SSE answer events, wired the client to stream tokens, and added a source-card component test.
- [x] Added thumbs-up/down feedback records containing the question, answer, sources and rating; added ping, feedback and SSE fallback tests.
- [x] Added a Docker-optional Testcontainers PostgreSQL/pgvector check that applies both Flyway migrations and verifies document uniqueness and feedback persistence schema behavior.
- [x] Added local Compose configuration and a non-root container recipe for the API.
- [x] Implemented HelpDesk Copilot's chat endpoint, authenticated demo identities, principal-scoped conversation memory and customer-owned tools, a role-gated operator ticket queue, FAQ/order tools, eligibility checks, separately confirmed/idempotent return endpoint and support tickets.
- [x] Added HelpDesk local-run instructions covering Basic-auth demo users, environment-managed local passwords, role enforcement and remaining production identity-provider limitations.
- [x] Implemented ToolBridge's loopback MCP server with bounded local artifact search and Maven-coordinate formatting tools.
- [x] Documented ToolBridge transport, local data scope and exposure limitations.
- [x] Added local MVP applications and run instructions for Research Crew, ReviewPilot, FraudGuard, VisionCheck, AskYourData, VoiceNotes, ShelfWise, PaperTrail and EvalOps.
- [x] Added an approval-gated local planner/researcher/writer/critic pipeline with source matching and citation checks.
- [x] Added optional HMAC verification for ReviewPilot pull-request webhook payloads without retaining payloads or writing to GitHub.
- [x] Replaced FraudGuard's hand-coded rules with a deterministic synthetic logistic-regression training flow and holdout average-precision metric.
- [x] Added fixed read-only JDBC analytics over seeded H2 data, optional local whisper.cpp transcription, PDF text extraction with Tika, and a content/collaborative recommender with NDCG@3.
- [x] Added aggregate latency measurement to EvalOps, a root launcher, and a loopback health endpoint for all twelve apps.
- [x] Added GitHub Actions CI for the twelve Maven projects, DocuMind component tests and build, and the DocuMind container image; CI is configured but has not run on GitHub because the portfolio remains local.
- [x] Added weekly Dependabot coverage for all Maven projects, the DocuMind frontend and GitHub Actions.
- [x] Read and mapped the full book, including Appendix A repository directory, and created `docs/book-requirements-checklist.md` with evidence and remaining work.
- [x] Expanded project guides 08–12 with reproducible API examples, flow diagrams, tests/exercises, current limits, and primary references.
- [x] Added HelpDesk input/output guardrails and deterministic escalation suggestions with tests.
- [x] Added an optional Redis-backed HelpDesk chat-memory store, bounded to 20 messages with a refreshed two-hour TTL; serialization/TTL tests and a local Redis storage smoke check passed. The small local chat model did not demonstrate reliable cross-turn recall, which remains an evaluation gap.
- [x] Replaced HelpDesk in-process demo order/return/ticket maps with H2-backed data; return and ticket creation remain idempotent, audit events persist, and a bounded loopback ticket queue is available. Persistence and queue behavior are covered by Spring integration tests.
- [x] Added bounded editing to Research Crew plans before human approval, with edit/approval tests.
- [x] Added bounded in-memory GitHub delivery replay protection to ReviewPilot and HMAC/replay tests.
- [x] Upgraded ReviewPilot unified-diff parsing to report changed file and new-file line coordinates; TODO/secret findings still only inspect added lines, verified by regression tests.
- [x] Expanded FraudGuard to 200,000 deterministic synthetic transactions over 60 days, chronological holdout average precision, and calibration error.
- [x] Initialized FraudGuard's logistic model from training-set prevalence and increased full-batch convergence; holdout ranking beats the base-rate check and an extreme generated case reaches REVIEW. H2 score history, bounded analyst queue and label lifecycle pass tests and a live API smoke check.
- [x] Added FraudGuard per-feature population-stability monitoring across chronological training and holdout distributions, with a thresholded report in scoring responses; targeted tests cover synthetic shift detection and invalid sample rejection.
- [x] Added FraudGuard per-score elapsed microseconds and a synchronized, bounded 512-observation latency window reporting average, p50, p95 and max; tests cover metric summary/rollover and controller integration.
- [x] Added a ToolBridge Streamable HTTP MCP integration test covering initialize/session, the initialized notification, `tools/list` and an executed coordinate-formatting tool call.
- [x] Added a generated PaperTrail invoice PDF fixture and a multipart Spring integration test that runs Tika extraction and verifies invoice/party/GSTIN/line/tax/total fields and reconciliation.
- [x] Integrated PaperTrail into EvalOps with a versioned golden JSONL case, loopback-only multipart upload adapter, field/arithmetic checks, configurable 100% gate and a passing live PaperTrail/EvalOps 1/1 runtime check.
- [x] Added VisionCheck top-three candidate scores and metadata-only human correction queue with tests.
- [x] Added VoiceNotes extractive summary and decision/action analysis with tests.
- [x] Added tag-based MMR diversity reranking and score breakdown to ShelfWise.
- [x] Added optional local Ollama text embeddings for ShelfWise relevance/MMR, with an explicit tag-cosine fallback when the local embed endpoint is unavailable; rank responses disclose which method was used.
- [x] Added PaperTrail supplier/buyer/currency/GSTIN/tax fields, per-field evidence labels, tax reconciliation, and tests.
- [x] Added an exact-query JSqlParser and `sample_sales` table policy plus persisted AskYourData report feedback; parser safety and feedback/seed integration tests pass.
- [x] Added EvalOps versioned JSONL cases and a bounded AskYourData loopback adapter with a golden-runner test.
- [x] Strengthened EvalOps golden cases with expected top-row labels and numeric tolerances captured from the live deterministic AskYourData dataset; the live three-case run passed 3/3 and result details report metric checks without retaining full responses.
- [x] Expanded AskYourData to 10,000 synthetic H2 sales rows and added a startup seed integration test.
- [x] Added asynchronous VoiceNotes jobs with bounded workers/queue/history and a lifecycle test.
- [x] Added bounded search across completed VoiceNotes jobs and a delete endpoint for retained transcript results; lifecycle tests verify search and deletion.
- [x] Added optional VoiceNotes ffmpeg normalization into ordered five-minute mono 16 kHz segments, with a one-hour/12-segment cap, process timeouts, and temporary-file cleanup; fake-process tests verify order, cap rejection, and cleanup without requiring ffmpeg or whisper.cpp in CI.
- [x] Decoupled synchronous and background VoiceNotes flows from whisper.cpp behind an `AudioTranscriptionProvider` interface; the local engine is the sole provider, preserving local-only behavior unless a future cloud provider is explicitly configured.
- [x] Added ShelfWise request-scoped VIEW/LIKE/PURCHASE interactions with event-type weights and a 30-day exponential half-life applied to semantic/tag profiles and synthetic co-like affinity; a test confirms both content and collaborative signals decay for old events.
- [x] Added ToolBridge's cached, timeout-bounded Maven Central REST search tool and offline parsing/validation test.
- [x] Started AskYourData and EvalOps locally; the product/region/summary requests all passed through the live three-case golden runner (3/3).
- [x] Added start/stop portfolio PowerShell scripts with hidden processes, health polling, ignored logs/PID manifest and process-start-time check before shutdown.
- [x] Exercised ToolBridge over MCP Streamable HTTP: initialized a session, listed all three tools, and invoked the validated coordinate formatter successfully.
- [x] Built DocuMind's Docker image and exercised the Compose app/PostgreSQL/pgvector stack with local Ollama models: migrations, collection creation, Markdown upload/indexing, cited RAG answer, token/source/done SSE, and feedback persistence all succeeded. Removed the dedicated smoke-test volumes and fixture afterward.
- [x] Added a PaperTrail OCR-enabled Docker image with Tesseract and CI image-build coverage. A generated image-only invoice was uploaded to the running container; OCR extracted the invoice number, supplier, buyer, date, currency, line item, tax and total. It misread one GSTIN character, and PaperTrail routed the result to manual review as intended. Documented the limitation and local run command.
- [x] Added and ran `projects/11-papertrail/scripts/test-ocr-container.ps1`: it built the PaperTrail image, waited for actuator health, uploaded the checked-in image-only invoice on an ephemeral loopback port, verified the extracted invoice/supplier/subtotal/tax/total and human-verification notice, and stopped its temporary container. This run extracted a checksum-valid GSTIN and returned `EXTRACTED_REVIEW_BEFORE_PAYMENT`; the earlier OCR run's GSTIN misread remains evidence that OCR outcomes can vary and confidence is not calibrated.
- [x] Added a Docker-optional DocuMind Testcontainers end-to-end test: HTTP upload, asynchronous Tika ingestion, deterministic 768-dimensional embeddings, pgvector insert/retrieval with collection metadata, cited chat response, and duplicate-upload conflict all pass. The chat model is stubbed; Ollama generation remains covered by the separate manual smoke test.
- [x] Added configurable DocuMind token chunk size, minimum chunk characters, chunk-count cap and word overlap, with unit coverage for bounded overlap and metadata preservation.
- [x] Added a DocuMind frontend feedback-flow test that streams an answer/source fixture and verifies the question, answer, sources and rating sent to the feedback endpoint.
- [x] Replaced ReviewPilot's in-memory webhook replay set with a file-backed H2 queue of verified repository/PR/action metadata. Added a 1,000-item pending bound, completion endpoint, persistent replay protection, and JDBC tests. A live signed webhook smoke check confirmed metadata-only queueing, restart persistence, 409 replay rejection and completion.
- [x] Replaced VoiceNotes' process-only job history with file-backed H2 metadata/results and transcript search/delete. Audio stays in the bounded in-memory worker queue and is never stored; interrupted jobs recover as failed for safe resubmission. The async lifecycle test verifies a reconstructed service can load the completed transcript.
- [x] Added EvalOps's fixed loopback DocuMind adapter with a versioned three-case retrieval/citation dataset, evidence-term recall and source-ID citation integrity metrics. Added loopback-only base-URL validation and a synthetic fixture. Live DocuMind/Ollama smoke extracted all three evidence targets (recall 3/3), while the small available model omitted citations in two answers (full rubric 1/3); the evaluator correctly failed those cases. Removed the isolated Compose containers and volumes after the run.
- [x] Hardened VisionCheck image intake: PNG/JPEG declaration must match actual format, dimensions and 12 MP limit are checked before pixel decode, and embedded metadata is ignored. Added MIME-mismatch and oversized-dimension tests.
- [x] Added a bounded, process-local LRU cache for ShelfWise Ollama catalogue/profile embeddings (configurable up to 10,000 vectors, default 1,000); tests verify repeated lookup reuse and least-recently-used eviction.
- [x] Added a same-origin VisionCheck browser workflow for local image preview, candidate-score display, metadata-only pending reviews, and human confirmation/correction. The page uses same-origin APIs and browser object URLs; the existing server continues to discard uploaded image bytes after classification.
- [x] Verified VisionCheck's packaged home page and client script over loopback, uploaded a generated PNG through multipart classification, confirmed the expected three-candidate blue-dominant result, corrected the queue label over HTTP, and verified the completed item disappeared from the pending list. All four VisionCheck Java tests pass; the browser script passes `node --check`.
- [x] Added an EvalOps VisionCheck golden adapter using a versioned synthetic PNG case. The loopback-only adapter checks label/candidate/dimension/safety fields, confirms metadata-only queue insertion, submits a human correction, and verifies queue completion. Its HTTP contract tests pass; the full EvalOps suite now passes 16 tests, and the live EvalOps→VisionCheck run passed 1/1 with the 100% gate.
- [x] Added AskYourData's structured semantic query plan and pre-JDBC table/dimension/metric/filter/row-limit validator while retaining the exact reviewed-SQL allowlist. Eight AskYourData tests pass. EvalOps' AskYourData adapter checks plan semantics as well as reports and data; its 16-test suite, including the adapter contract test, passes.
- [x] Expanded the AskYourData golden dataset to 50 unique phrasings across its three fixed reports, including semantic-plan assertions and top-row numeric tolerances. Raised the still-bounded EvalOps queue default to 64 so the full batch fits at four workers; its live quality gate passed 50/50.
- [x] Added PaperTrail GSTIN base-36 checksum verification. Invalid checksums are withheld from extracted fields and force manual review; tests cover a published valid vector and a changed check character. This does not verify registration status.
- [x] Integrated LangGraph4j into Research Crew with a compiled shared-state researcher → writer → critic graph that runs only after the existing human approval. Citation integrity and no-evidence behavior are checked through the controller tests; graph runs/checkpoints remain in memory.
- [x] Added opt-in LangGraph4j PostgreSQL `PostgresSaverV2` checkpoints for approved Research Crew runs, durable run IDs, and a restart lookup path that reconstructs the completed report in a fresh controller. A PostgreSQL Testcontainers integration test verifies restoration with a fresh saver; local Compose configuration validates and uses a persistent loopback-only database volume.
- [x] Added a completed-report Markdown download endpoint that also works through the checkpoint restore path; Spring MVC coverage verifies approval gating, graph output and exported citations.
- [x] Added HelpDesk stateless HTTP Basic authentication for two demo customers and a support operator. Customer identity now comes from the authenticated principal for order ownership, returns, audit, tickets and chat-memory keys; only support operators can read the admin ticket queue. Spring integration tests cover basic-auth principal binding, spoofed identity rejection, cross-customer data isolation, and customer/operator/anonymous access rules.
- [x] Added Research Crew SSE stage events for actual researcher, writer and critic execution, with connected-client delivery and completed-run replay; Spring MVC coverage verifies the streamed timeline contents.
- [x] Persisted unapproved Research Crew drafts and bounded edit counts in a local H2 file; a restart simulation with a fresh controller restores and approves the edited plan. The project suite passes, including PostgreSQL checkpoint integration.
- [x] Added EvalOps Micrometer counters/timers and a low-cardinality evaluation observation, plus configurable opt-in OTLP metrics and trace exporters. The EvalOps Maven suite passes; local collector/Grafana provisioning remains open.
- [x] Added a dependency-free portfolio landing page with twelve project cards, topic filters, keyboard-accessible search, local guide links, and API health links. It runs directly from `index.html` and makes no external font/image requests.
- [x] Added ToolBridge's H2-backed `sample_sales` schema description and fixed, parameterized, read-only query tools; tests verify seed access, bounded results and injection-like filters remain data.
- [x] Isolated ToolBridge MCP analytics on an H2 `TOOL_READER` account with a database-enforced `SELECT` grant only on `sample_sales`; integration coverage confirms `INSERT`, `UPDATE`, `DELETE` and `DROP` are rejected while sample data remains intact. Verified the documented environment variable with a test-only custom password.
- [x] Added configurable EvalOps pass-rate gates to the AskYourData and DocuMind golden reports. Responses include observed rate, configured minimum and gate result; missing/empty datasets fail. Added threshold boundary/invalid-input tests and a PowerShell CI-gate example.
- [x] Added shared bounded parallel execution for EvalOps golden cases, with configurable worker/queue limits, stable fixture ordering and explicit failed results on saturation; tests exercise concurrency and queue rejection.

## Verification

- [x] Compile all twelve Maven applications; all builds pass after the ToolBridge API correction.
- [x] Research Crew focused Maven suite passes after adding SSE stage progress and timeline replay.
- [x] ToolBridge Maven suite passes with the new SQL schema/query MCP tools.
- [x] EvalOps Maven suite passes with configurable golden-evaluation gates.
- [x] Latest full verification passed all twelve Java suites: 84 JUnit tests, 0 failures/errors/skips; both DocuMind frontend tests and the production frontend build also passed. This run includes the Research Crew pending-draft restart test, EvalOps Actuator-metric integration test, FraudGuard drift/latency tests, ToolBridge MCP protocol test, PaperTrail multipart Tika/PDF fixture test, and EvalOps golden adapter tests.
- [x] Checked the showcase JavaScript syntax and verified that all twelve project cards and local guide links are present. Browser rendering was not visually inspected because browser access is disabled in this Work session.
- [x] Install the DocuMind web client dependencies and produce a production Vite build with zero npm audit vulnerabilities reported.
- [x] Run application-level checks for AskYourData and EvalOps golden integration (3/3 pass); other services still need runtime checks.
- [x] Exercise DocuMind against PostgreSQL/pgvector and Ollama manually; add and pass a Docker-optional Testcontainers PostgreSQL/pgvector migration and constraint integration test. Full embedding/RAG integration coverage remains open.
- [x] Add SSE answer streaming and persisted thumbs-up/down feedback; source page numbers are carried when Tika supplies page metadata.
- [x] Reach Docker with elevated access and build/launch the DocuMind container stack; continue using elevation for Docker commands because the default sandbox cannot access its named pipe.
- [x] Add portfolio-wide launch orchestration; syntax-checked the start/stop scripts. Full 12-app launch remains dependent on local service prerequisites.
- [x] Transcribe the book's inspiration repository directory in `docs/inspiration-repositories.md`.
- [x] Attach primary references and complete learner guides to projects 04–12; continue reviewing project 01–03 references as features evolve.

## Optional stretch goals (deferred)

The showcase target is defined in [showcase-scope.md](showcase-scope.md). The checklist still records the book's deeper scope; the following items are deliberately not required to present the 12 local demos:

- [ ] Add LLM planning/critique, broad model judges, generated SQL and repair loops beyond the bounded examples.
- [ ] Add production identity, multi-tenant controls, GitHub App write workflows and hardened deployment across all apps.
- [ ] Add fine-tuned/calibrated models and broad real-world labelled datasets for VisionCheck, FraudGuard and PaperTrail.
- [ ] Add cloud speech, diarization, timestamps, TTS and complete production UIs.
- [ ] Add a provisioned collector/Grafana dashboard, remote scheduled evaluations and nightly CI reporting.

## Current blockers and limitations

- Docker Desktop's engine is reachable with elevated access. The DocuMind image and real PostgreSQL/pgvector/Ollama flow passed a manual smoke test; Docker commands from the default sandbox are still denied.
- All twelve apps passed the latest full portfolio run (84 Java tests, zero failures/errors/skips); both DocuMind frontend tests and the production build passed. EvalOps-to-AskYourData has passed both the earlier 3/3 live smoke check and the expanded 50/50 semantic-plan gate; PaperTrail and VisionCheck passed 1/1 each with their 100% gates. FraudGuard's local `/api/score` smoke call returned the expected four-feature `featureDrift` report, PSI threshold 0.2, stable-synthetic-data flag of false and `scoringLatencyMicros`; `/api/score/metrics` reported the recorded sample, p95 and 512 capacity.
- PaperTrail scanned-PDF OCR extracts key fields but misread one GSTIN character in the generated fixture. Manual review was triggered; GSTIN confidence still is not calibrated.
- The initial DocuMind app is a local single-user learning tool, not a multi-tenant service.
- HelpDesk Copilot uses local HTTP Basic demo identities, not a production identity provider. Redis-backed history storage and TTL were smoke-tested; H2-backed ticket/return/audit persistence and operator-gated queue have passing integration tests. The small local model did not reliably recall the cross-turn test token; model evaluation, production identity and container stack remain open.
- The target is a clear local showcase, not a production-ready system. Advanced integrations listed in the checklist are intentionally deferred; see `docs/showcase-scope.md`.
- ToolBridge's initialize/session/list/tool-call flow is covered by an automated Streamable HTTP integration test; live Maven Central search was not invoked.
- Projects 04–12 compile; local MVP workflows have not all been exercised. Several use fixtures or heuristic baselines, and optional whisper.cpp requires a local binary and model.

