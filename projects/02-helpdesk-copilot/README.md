# HelpDesk Copilot

Local Spring Boot 3 / LangChain4j demo of a customer support assistant with Ollama, short-window chat memory, FAQ lookup, order lookup, return requests, and human support tickets. The Spring Boot 3 LangChain4j starters use the upstream-documented `1.22.0-beta32` release line.

## Run locally

Requirements: Java 21, Maven, Ollama, and a locally available chat model. The default model is `llama3.2`.

```powershell
ollama pull llama3.2
ollama serve
```

The demo database uses a local H2 file in `./data/` by default and stores the demo orders, return requests, tickets, and audit events. Chat memory stays in-process unless you enable Redis. Start Redis if you want chat history to survive a process restart:

```powershell
docker run --rm --name helpdesk-redis -p 127.0.0.1:6379:6379 redis:7
```

Then configure and start the app:

```powershell
$env:HELPDESK_REDIS_MEMORY_ENABLED = 'true'
$env:REDIS_HOST = 'localhost'
$env:REDIS_PORT = '6379'
$env:HELPDESK_CUSTOMER_1001_PASSWORD = 'use-a-private-local-password'
$env:HELPDESK_CUSTOMER_2002_PASSWORD = 'use-another-private-password'
$env:HELPDESK_OPERATOR_PASSWORD = 'use-a-private-operator-password'
mvn spring-boot:run
```

The API uses stateless HTTP Basic authentication. The demo principals are `customer-1001`, `customer-2002`, and `support-operator`, with passwords set by the environment variables above. Local fallback passwords are `customer-1001-local`, `customer-2002-local`, and `operator-local`; override them before use outside a private loopback learning session. No customer ID is accepted in a request body or header: the authenticated principal determines order ownership and chat-memory identity. `/api/admin/tickets` requires the `SUPPORT_OPERATOR` role; customer principals receive 403.

Redis memory is off by default. When enabled, messages are JSON-serialized to Redis by the demo account memory ID, each update refreshes a two-hour TTL, and the LangChain4j window is capped at 20 messages. Set `HELPDESK_REDIS_MEMORY_ENABLED=false` to use the default in-process store without Redis. Change `HELPDESK_DB_URL`, `HELPDESK_DB_USERNAME`, or `HELPDESK_DB_PASSWORD` to select another database.

The service listens on port 8082. Send a message to `POST http://localhost:8082/api/chat` with the customer credentials:

```json
{"message":"Where is order NS-1042?"}
```

For example, use `curl.exe -u "customer-1001:$env:HELPDESK_CUSTOMER_1001_PASSWORD" -H "Content-Type: application/json" -d '{"message":"Where is order NS-1042?"}' http://127.0.0.1:8082/api/chat`. Use the `support-operator` credentials to view the local ticket queue.

The API responds with `reply`, `outputBlocked`, and `escalationSuggested` fields. Health is available at `/actuator/health`. The local operator queue is `GET http://localhost:8082/api/admin/tickets?limit=50` (maximum 100).

## Safety and scope

- This is a local prototype with three in-memory demo identities and seeded fictional orders. Customer/order tools and chat memory are scoped from the HTTP Basic principal by host code; model arguments never include a customer ID.
- Each customer uses a 20-message in-process window by default, or optional Redis persistence with a two-hour TTL. Orders, return requests, tickets, and audit events are stored in the local H2 database. Production needs a real identity provider, durable user/role administration, stronger secret management, retention controls, and request rate limits.
- The model can only check return eligibility. A separate authenticated `POST /api/returns` action requires `{"orderId":"NS-1042","confirm":true}`, checks ownership and eligibility, and is idempotent per customer/order. It only records a review request; it cannot issue refunds or labels.
- Chat applies deterministic input/output guardrails: it rejects common instruction-override attempts, blocks secret-like output, raw tool-call JSON and unsupported claims that a refund/return/label was issued, and flags language asking for a person or expressing strong dissatisfaction with `escalationSuggested`. This signal is not a ticket by itself; the assistant can offer the existing ticket tool.
- Ticket creation is bounded and idempotent for the same customer and normalized summary. `GET /api/admin/tickets` provides a bounded local queue and requires the operator role. Audit rows persist action, customer, and subject; avoid putting sensitive content in tool arguments.
- FAQ answers and order data are fictional local fixtures. The model may still produce mistakes, so tool results are the source of truth for actions.

## Main pieces

- `SupportAssistant`: LangChain4j AI Service with explicit support rules.
- `StoreSupportTools`: bounded FAQ/order/return/ticket tools with customer scoping.
- `ChatController`: validates message size and provides a single demo conversation.
- `ReturnController`: requires a separate explicit user API action before recording a return request.
- `SecurityConfiguration` and `DemoAccount`: stateless HTTP Basic users; application-level ownership is derived from the authenticated role/principal.
- `TicketQueueController`: bounded local read-only ticket queue restricted to `SUPPORT_OPERATOR`.
- `MemoryConfiguration` and `RedisChatMemoryStore`: 20-message memory window with optional Redis JSON persistence and a two-hour TTL.
- `SupportGuardrails`: simple, testable input/output safety checks and escalation suggestion.

This implementation follows the book's HelpDesk Copilot concepts while keeping dependencies and data local. H2 ticket/return/audit persistence and idempotency have integration tests; Redis-backed memory has serialization/TTL tests and a local storage smoke check. Authentication and customer isolation are implemented for the demo identities, but the small Ollama model did not reliably recall the test token in its reply. Reliable cross-turn scenario evaluation and a production identity provider remain open. The guardrail rules are a learning baseline, not a complete prompt-injection or moderation defense.
