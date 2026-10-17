# Decisions and open items

## Decisions

- Use Java 21, Maven and the book's Spring Boot 3.5 / Spring AI 1.1 family for the first project, so the implementation matches the learning material. Official release pages show Boot 3.5.16 and Spring AI 1.1.8 as stable releases; newer major lines exist and may require API changes.
- Keep the portfolio in a new local folder, independent of the user's existing public GitHub repositories.
- Keep project code isolated per directory rather than share runtime code prematurely.
- DocuMind starts as a local, single-user app with Ollama. Collection filters are not an authentication boundary.
- Store uploads outside the source tree and keep them excluded from Git.
- The DocuMind browser interface uses system fonts only, so opening it does not request web fonts from a third party.

## Open items

- Confirm the installed Ollama version and available models before documenting exact local inference results.
- Run a compile/build after confirming dependency resolution is available.
- Add frontend, container configuration and optional cloud profile in a later milestone.
- Evaluate per-user authentication before any multi-user deployment.
- Add an SSE endpoint and page-aware PDF reader if page citations are needed.

