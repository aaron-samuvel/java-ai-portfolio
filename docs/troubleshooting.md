# Troubleshooting

## PostgreSQL connection refused

Confirm PostgreSQL is running and that `DOCUMIND_DB_URL`, user and password point to the intended database. Ensure its pgvector extension is installed.

## Ollama connection refused

Start Ollama and check `http://localhost:11434`. Pull both configured models. When the Java app itself runs in a container, `localhost` points to that container; use the documented host gateway or the Ollama service name instead.

## Model not found

Run `ollama list` and confirm the exact configured names `qwen2.5:7b` and `nomic-embed-text` are present.

## Upload rejected

Only PDF, DOCX, Markdown, HTML and plain text are accepted, up to 25 MB. A duplicate file in the same collection returns HTTP 409 with the existing document ID.

## Gradle on this machine

The installed Gradle launcher currently fails to load `native-platform.dll`. This repository uses Maven, which starts successfully.

## Docker on this machine

Docker Desktop is available, but the default sandbox cannot access its named pipe. Run Docker commands with elevated access in Codex. The DocuMind image and Compose path have been verified this way. If a host port is already occupied, set `DOCUMIND_PORT` (the DocuMind smoke test used 18081); the database stays on the private Compose network and does not publish port 5432.

## Web fonts and network access

The web client uses system fonts and does not fetch fonts from a third-party CDN. Vite proxies API requests to the local Spring server.

