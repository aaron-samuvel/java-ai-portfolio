# Book-to-repository coverage map

The supplied book contains a primer on Spring Boot AI and the AI development lifecycle, twelve portfolio projects, a portfolio roadmap, a repository directory and glossary. This map records repository coverage. Status refers to implementation in this repository, not to whether the subject is discussed in the book.

| Book chapter | Subject | Repository location | Status |
|---|---|---|---|
| 01 | Spring Boot AI system design and AI development lifecycle | `docs/architecture.md`, `docs/project-overview.md` | Foundation documented |
| 02 | DocuMind: private document Q&A with RAG | `projects/01-documind/` | RAG API, React SSE client, feedback persistence; services not integration-tested |
| 03 | HelpDesk Copilot: support agent, memory and tools | `projects/02-helpdesk-copilot/` | Local prototype; build verified |
| 04 | ToolBridge: Java MCP server | `projects/03-toolbridge/` | Local MCP server; build verified |
| 05 | Research Crew: multi-agent research and report writer | `projects/04-research-crew/` | Approval-gated local workflow; build verified |
| 06 | ReviewPilot: AI pull-request review bot | `projects/05-reviewpilot/` | Local static review and optional verified webhook; build verified |
| 07 | FraudGuard: transaction fraud scoring | `projects/06-fraudguard/` | Synthetic logistic model; build verified |
| 08 | VisionCheck: image classification and inspection | `projects/07-visioncheck/` | Local image heuristic; build verified |
| 09 | AskYourData: safe text-to-SQL analyst | `projects/08-askyourdata/` | Fixed read-only reports; build verified |
| 10 | VoiceNotes: meeting transcription and summaries | `projects/09-voicenotes/` | Consent-gated transcription MVP; build verified |
| 11 | ShelfWise: semantic recommendations | `projects/10-shelfwise/` | Content/collaborative recommender; build verified |
| 12 | PaperTrail: invoice and receipt extraction | `projects/11-papertrail/` | Checked-in synthetic invoice PDF passes multipart Tika extraction and field/tax/total reconciliation; scanned-PDF OCR also has a documented manual smoke check |
| 13 | EvalOps: evaluation and observability | `projects/12-evalops/` | Deterministic evaluation API; build verified |
| 14 | Putting the portfolio together | `docs/project-progress.md`, `docs/project-catalog.md`, `projects/12-evalops/` | All twelve apps build and are testable; EvalOps aggregates loopback health status |
| Appendix A | Inspiration repository directory | `docs/inspiration-repositories.md` | Book-listed references transcribed; live licences/activity need rechecking before reuse |
| Appendix B | Glossary and official documentation | `docs/glossary.md`, project READMEs | Core terms mapped; reference coverage still being expanded |

See the [chapter requirements checklist](book-requirements-checklist.md) for book feature coverage and concrete remaining work.

## Shared requirements carried into the design

- Keep inference local by default and put credentials in environment variables.
- Separate projects into their own directories and document setup and limitations.
- Apply input validation, privacy boundaries and safe defaults appropriate to each project.
- Add external API integrations only where required and keep write actions disabled until configured.
- Record dependency versions and verify APIs against primary documentation when they change quickly.

