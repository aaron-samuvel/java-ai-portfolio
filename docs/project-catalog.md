# Project catalog

Every project has an independent Maven application. From the repository root, start one with `./run-project.ps1 -Project <folder>` in PowerShell. Projects bind to localhost by default unless noted.

| # | Project | Port | Local showcase workflow | Optional extensions (not required for showcase) |
|---|---|---:|---|---|
| 01 | DocuMind | 8081 | Upload a document, index it, ask a question and inspect cited sources in the API/web client | Multi-tenant ownership, rate limiting, broad retrieval benchmark, production deployment |
| 02 | HelpDesk Copilot | 8082 | Sign in as a demo customer, ask about orders/returns, and use the operator ticket queue | Production identity provider, large model evaluation set and production container stack |
| 03 | ToolBridge | 8083 | Connect over Streamable HTTP MCP and call bounded catalog, Maven search and read-only sample SQL tools | GitHub/DocuMind tools, stdio transport, production auth and demo client |
| 04 | Research Crew | 8084 | Create/edit a plan, approve it, and receive a cited report from a local evidence corpus | LLM planner/critic, live web/DocuMind tools and durable SSE event history |
| 05 | ReviewPilot | 8085 | Submit a local diff or signed webhook and inspect added-line findings in the queue | GitHub App integration, model findings, comment workflow and dashboard |
| 06 | FraudGuard | 8086 | Score synthetic transaction examples and inspect feature contributions, drift and analyst review | Live transaction feeds, Redis online features, production authentication and calibration dataset |
| 07 | VisionCheck | 8087 | Upload a local PNG/JPEG, view top color candidates, and confirm/correct a review item | DJL classifier, fine-tuning, calibrated confidence and broad image benchmark |
| 08 | AskYourData | 8088 | Ask a supported question and receive one of three bounded, read-only sales reports | General text-to-SQL, PostgreSQL role separation, live schema discovery and charts |
| 09 | VoiceNotes | 8089 | Submit audio with consent, process it through the local provider, and search/delete saved transcripts | Cloud providers, diarization, timestamps, TTS and full web UI |
| 10 | ShelfWise | 8090 | Rank synthetic catalogue items for a request using local embeddings/tag fallback and inspect explanations | Persistent user/item profiles, popularity/stock filters and representative ranking benchmark |
| 11 | PaperTrail | 8091 | Upload a text or scanned invoice and inspect extracted fields, arithmetic and review status | OCR confidence calibration, large real-invoice benchmark, registry lookup and correction UI |
| 12 | EvalOps | 8092 | Run deterministic checks, four versioned local golden adapters, quality gates, metrics and portfolio health | LLM judge/cache, JUnit extension, provisioned Grafana dashboard and scheduled/nightly CI |

The table summarizes basic demos and deferred book extensions. The current implementations are local learning prototypes, not production systems. See the [showcase scope](showcase-scope.md) for the completion target.
