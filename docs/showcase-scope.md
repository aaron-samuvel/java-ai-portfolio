# Showcase completion scope

## Completion target

This portfolio is being prepared as a local learning showcase, not as twelve production services. The goal is to make each of the twelve project ideas easy to understand and demonstrate at a basic level.

A project counts as showcase-ready when it has:

1. A short explanation of the problem it demonstrates.
2. A documented local start command and at least one sample request or workflow.
3. A working core path using local/sample data, a deterministic baseline, or an optional local model.
4. A clear note where a feature is heuristic, synthetic, unavailable without a local prerequisite, or only a demo security boundary.
5. A passing project test suite as part of the portfolio check where practical.

Portfolio-level completion also requires a clear index of all twelve apps, a static local landing page at `index.html`, working launch instructions, and passing `test-all.ps1` checks for all Maven suites and the DocuMind frontend.

## Deliberately deferred stretch work

These items are outside the basic showcase target because they need much more engineering, external accounts, special hardware, or wider data than a local demonstration:

- Production identity management, tenant isolation, rate limiting, compliance and hardened deployment across every app.
- A GitHub App and real pull-request review/comment workflow. ReviewPilot remains a local, non-writing demo.
- Broad autonomous LLM agent planning, live web research, model-based critics, judges, generated SQL, and repair loops. Existing deterministic plans and guarded baselines remain useful teaching examples.
- DJL model training/fine-tuning, production image confidence calibration, and a broad labelled image benchmark.
- Cloud transcription, diarization, timestamp alignment, speech generation, and a full VoiceNotes UI.
- Live external business data, persistent recommender infrastructure, and research-grade recommendation metrics.
- A provisioned OpenTelemetry Collector, Prometheus/Grafana dashboards, scheduled/nightly evaluation, and remote GitHub Actions results. EvalOps provides local gates and opt-in OTLP export configuration.
- Production-grade OCR confidence calibration and an extensive real-invoice benchmark.

Existing implementations of optional features are retained. This scope choice removes unfinished stretch work from the completion estimate; it does not claim that those features are complete or production-ready.

## Verification

Use `./test-all.ps1` from the repository root. It runs each Maven test suite and the DocuMind frontend tests/build. For interactive demos, follow the run and sample workflow in each project's README. Services that need PostgreSQL, Docker, Ollama, Redis, ffmpeg, or a local speech model must say so in their run guide.

The detailed [book requirements checklist](book-requirements-checklist.md) remains the coverage map. Its open items are book-depth extensions unless they prevent one of the showcase checks above from working.
