# Java AI Project Portfolio

A hands-on learning repository based on *Building AI with Java & Spring Boot*. It turns the book's twelve project briefs into small, understandable applications that demonstrate current Java AI patterns.

## Learning path

The book begins with Spring Boot AI system design and the AI development lifecycle, then develops these projects:

1. **DocuMind** — local document question-answering with retrieval-augmented generation (RAG), citations, Spring AI, Ollama and pgvector.
2. **HelpDesk Copilot** — customer support tools, conversation memory, guardrails and human handoff.
3. **ToolBridge** — typed Java tools served through the Model Context Protocol (MCP).
4. **Research Crew** — a reviewable multi-agent research workflow with human approval.
5. **ReviewPilot** — an AI-assisted pull request reviewer with line-grounded findings.
6. **FraudGuard** — classical machine-learning fraud scoring with explanations.
7. **VisionCheck** — JVM image classification and visual inspection.
8. **AskYourData** — constrained, read-only natural-language database analysis.
9. **VoiceNotes** — meeting transcription, summaries, decisions and actions.
10. **ShelfWise** — embedding-based recommendations.
11. **PaperTrail** — structured invoice and receipt extraction.
12. **EvalOps** — evaluation and observability shared across AI projects.

Chapter 14 explains how to connect and present the portfolio. The suggested order is DocuMind, HelpDesk Copilot and ToolBridge; add EvalOps early, then pick projects based on interest.

## Repository map

```text
docs/                         Architecture, setup, glossary, coverage and progress
projects/01-documind/         Local document RAG (Spring Boot API + React client)
projects/02-helpdesk-copilot/ Local support assistant (Spring Boot + LangChain4j)
projects/03-toolbridge/       Local Spring AI MCP server with bounded catalog, SQL and Maven Central tools
projects/04-research-crew/    Approval-gated, editable local research plan
projects/05-reviewpilot/     Local diff scanner and replay-checked webhook receiver
projects/06-fraudguard/      Explainable synthetic temporal fraud experiment
projects/07-visioncheck/     Image heuristic and metadata-only review queue
projects/08-askyourdata/    Fixed, read-only synthetic sales reports
projects/09-voicenotes/     Consent-gated async local transcription and extractive analysis
projects/10-shelfwise/      Synthetic recommender with tag-based MMR
projects/11-papertrail/     Invoice extraction and tax/line reconciliation
projects/12-evalops/        Golden checks, aggregate metrics and portfolio health
```

Each project has its own README and independent Maven application. All twelve have local implementations; several use fixture data or heuristic baselines. This is a local learning showcase, not a production deployment. The agreed completion target and deferred stretch items are in the [showcase scope](docs/showcase-scope.md); the [book checklist](docs/book-requirements-checklist.md) remains a coverage map.

Use `./run-project.ps1 -Project 04-research-crew` to start an app from the repository root. See the [project catalog](docs/project-catalog.md) for all twelve ports and demo workflows.

Open the [portfolio showcase](index.html) directly in a browser; it needs no package install, build step, server, or external font/image downloads. Each card links to its project guide and shows the port used when you run that API locally.

When local service prerequisites are configured, `./start-portfolio.ps1` launches all twelve APIs in hidden Maven processes, records their PIDs under ignored `.run/`, and waits up to 90 seconds for health. Use `./stop-portfolio.ps1` to stop only process trees recorded by that launcher. DocuMind requires PostgreSQL/pgvector and Ollama; check `.run/*.err.log` if any project does not become healthy.

Run the focused Java tests for all projects and the DocuMind frontend test/build with `./test-all.ps1`. Each app can also be tested independently with `mvn test` from its project directory.

GitHub Actions is configured in `.github/workflows/ci.yml` to verify all twelve Maven applications, test and build the DocuMind frontend, and build the DocuMind image without publishing it. The Pages workflow in `.github/workflows/pages.yml` publishes only the static showcase files; it does not publish the Java source trees as website content. Weekly dependency updates are configured in `.github/dependabot.yml`.

## GitHub Pages

After this repository is pushed to `aaron-samuvel/java-ai-portfolio`, open **Settings → Pages** and select **GitHub Actions** as the build and deployment source. The Pages workflow deploys on each push to `main`; the portfolio URL will be `https://aaron-samuvel.github.io/java-ai-portfolio/`. The APIs remain local demos and are not hosted by GitHub Pages.

## Requirements

- Java 21 (Temurin recommended)
- Maven 3.9+
- Ollama for local chat and embedding models
- PostgreSQL with pgvector for DocuMind
- Node.js for the DocuMind web client
- Docker Desktop is optional when PostgreSQL is already available locally

This machine has Java 21, Maven 3.10, Node 24 and Ollama installed. Docker's engine is reachable with elevated access; the DocuMind image and PostgreSQL/pgvector/Ollama Compose flow were smoke-tested. The default sandbox cannot access Docker's named pipe. Gradle is installed but currently fails to start; these projects use Maven.

## Start DocuMind

The first milestone is the DocuMind API. It accepts PDF, DOCX, Markdown, HTML and plain-text files, indexes extracted text, and answers questions from one collection at a time.

1. Start PostgreSQL with the pgvector extension and create a `documind` database.
2. Start Ollama, then pull the models:

   ```powershell
   ollama pull qwen2.5:7b
   ollama pull nomic-embed-text
   ```

3. In `projects/01-documind`, run:

   ```powershell
   mvn spring-boot:run
   ```

4. Check `http://localhost:8081/api/ping` and use the endpoints listed in the project README.

To containerize the API and PostgreSQL, copy `projects/01-documind/.env.example` to `.env` in that directory and run `docker compose up --build`. The database stays on the internal Compose network; set `DOCUMIND_PORT` if host port 8081 is already in use. The Ollama URL points to the host machine, and chat/embedding model names can be selected with `OLLAMA_CHAT_MODEL` and `OLLAMA_EMBEDDING_MODEL`.

Database connection settings and the upload directory can be changed through environment variables. No model or database password is committed.

## Start HelpDesk Copilot

HelpDesk Copilot is a local single-user demo backed by Ollama and an H2 file database. Pull the model with `ollama pull llama3.2`, then, from `projects/02-helpdesk-copilot`, run `mvn spring-boot:run`. It listens on port 8082; see its README for the chat request, optional Redis memory, persistent ticket queue, and fixed demo identity limitations.

## Start ToolBridge

From `projects/03-toolbridge`, run `mvn spring-boot:run`. ToolBridge exposes bounded catalog, read-only sample SQL and Maven Central search utilities over Spring AI's MCP Streamable HTTP transport and binds to `127.0.0.1:8083` by default. Set `TOOLBRIDGE_SQL_READER_PASSWORD` to a private value of at least 16 characters; see the project README for details.

## Coverage and progress

- [PDF requirements map](docs/pdf-requirements-mapping.md)
- [Book requirements checklist](docs/book-requirements-checklist.md)
- [Inspiration repository directory](docs/inspiration-repositories.md)
- [Project catalog](docs/project-catalog.md)
- [Showcase completion scope](docs/showcase-scope.md)
- [Project progress](docs/project-progress.md)
- [Architecture](docs/architecture.md)
- [Setup guide](docs/setup-guide.md)
- [Troubleshooting](docs/troubleshooting.md)
- [Glossary](docs/glossary.md)

## Scope and limitations

The book is the learning source, not a guarantee that every integration is production-ready. Implementations will call out local-only assumptions, model limitations, privacy boundaries and external-service requirements. Projects that would post to GitHub, transmit files, or need credentials will use local fixtures or clearly documented configuration until explicitly enabled.

All twelve Maven applications and 84 Java tests passed in the latest full run (0 failures, errors, or skips); both DocuMind frontend tests and the production Vite build pass. This run includes the Research Crew pending-plan restart test and EvalOps Actuator metric integration test. EvalOps' AskYourData plan-aware 50-case golden gate passed live 50/50; its VisionCheck gate passed live 1/1, exercising image classification, review insertion, correction, and queue completion across both local services. EvalOps' PaperTrail golden adapter passed its live 1/1 quality gate; PaperTrail's checked-in invoice PDF passed its multipart Tika extraction test and its OCR container smoke. DocuMind's Testcontainers migration/constraint check passed against PostgreSQL/pgvector, and the full Ollama upload, indexing, cited answer, streaming and feedback flow was manually exercised. FraudGuard's scoring API was exercised locally and returned its four-feature PSI report and scoring latency; the metrics endpoint returned the sample and p95. ToolBridge has a protocol-level MCP initialize/list/call integration test. GitHub CI has not run remotely. See [project progress](docs/project-progress.md) for showcase readiness and deferred stretch work.

## References

- [Spring AI 1.1.8 reference](https://docs.spring.io/spring-ai/reference/1.1/index.html)
- [Spring Boot 3.5 reference](https://docs.spring.io/spring-boot/3.5/reference/index.html)
- [Ollama model library](https://ollama.com/library)
- [LangChain4j Spring Boot integration](https://docs.langchain4j.dev/tutorials/spring-boot-integration/)
- [LangChain4j Ollama integration](https://docs.langchain4j.dev/integrations/language-models/ollama/)
- [Spring AI MCP server starter](https://docs.spring.io/spring-ai/reference/1.1/api/mcp/mcp-server-boot-starter-docs.html)

