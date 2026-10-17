# Project overview

## Purpose

Build a practical Java AI portfolio from the supplied book. The repository follows the book's dependency sequence where it helps learning, but keeps each application independently understandable and records what is complete versus planned.

## Intended learners

Java developers learning to design, build and evaluate applications that use language models, embeddings, tool calling and traditional machine learning.

## Design principles

- Prefer local inference and sample data as the safe default.
- Keep model calls behind application services so the provider can change.
- Treat retrieved documents and model output as untrusted data.
- Keep model-supplied arguments away from authorization decisions.
- Make limitations visible and distinguish working behaviour from future work.
- Keep integrations requiring credentials disabled until explicitly configured.

## Milestones

1. Repository foundation, full book-to-project map and DocuMind API.
2. DocuMind web client, operational documentation and local deployment.
3. Add local vertical slices for projects 02–12 in the book's suggested sequence.
4. Finish the local showcase: make all twelve basic demos easy to start and follow, verify the portfolio, and document optional book-depth extensions separately. Production integrations are intentionally not required for this milestone; see [showcase scope](showcase-scope.md).

