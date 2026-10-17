# ToolBridge

A local Spring AI MCP server that publishes typed, read-only developer utilities to compatible MCP clients.

## Tools

- `search_sample_artifacts`: searches a small curated in-process Maven catalog. It does not query Maven Central or the network.
- `get_maven_coordinate`: formats group and artifact IDs after validating their allowed characters.
- `search_maven_central`: searches Sonatype's public Maven Central REST API; input is bounded to 80 characters, results are capped at five, requests time out after four seconds, and up to 100 query results are cached in memory. The query text is sent to Sonatype; no project files or credentials are sent.
- `describe_sample_sales_schema`: returns the sole SQL table and its permitted columns.
- `query_sample_sales`: returns up to 25 seeded H2 rows, optionally filtered by exact region. It uses fixed parameterized queries; callers cannot submit SQL, select another table, or mutate data.

The server uses Spring AI 1.1.8 with the WebMVC MCP server starter and Streamable HTTP transport. Spring AI 1.1's `MethodToolCallbackProvider` registers these methods programmatically. It binds to `127.0.0.1:8083` by default. The transport path is `/mcp` for Streamable HTTP; point a compatible client at `http://127.0.0.1:8083/mcp`.

## Run locally

Requires Java 21 and Maven. From this directory:

```powershell
mvn spring-boot:run
```

Catalog and SQL tools use fixed local sample data or validate strings. The local analytics database is an in-memory H2 database seeded at startup. The Maven Central tool makes an outbound HTTPS request only when a client calls it. No GitHub token, filesystem tool, or user-data access is configured.

The SQL MCP tools connect as the separate `TOOL_READER` H2 user, which receives `SELECT` permission only on `sample_sales`. Override the demo password with a private environment value of at least 16 characters:

```powershell
$env:TOOLBRIDGE_SQL_READER_PASSWORD = 'choose-a-private-local-password'
mvn spring-boot:run
```

The fallback password is only for the loopback learning demo. The `sa` account initializes the schema and seed rows; the MCP tool code receives only the reader connection.

## Try it in an MCP client

Add a Streamable HTTP server with this URL to a compatible MCP client:

```text
http://127.0.0.1:8083/mcp
```

List its tools, then call `get_maven_coordinate` with `groupId: org.example` and `artifactId: safe-lib`. The result is a formatted Maven dependency snippet. For a data example, call `query_sample_sales` with `region: North`; the response contains only seeded sample rows. No SQL text is accepted. The same initialize → list tools → call tool flow runs in the automated Streamable HTTP integration test with `mvn test`.

## Safety and limitations

- The HTTP server binds to loopback by default. Changing the bind address can expose tools to other machines and requires adding authentication and network controls first.
- The local artifact list is illustrative. Maven Central search is fresher but still verify artifact metadata before adding a dependency.
- Inputs have length and character bounds; catalog results are capped at ten, SQL results at 25, and the database enforces SELECT-only access for the MCP reader on `sample_sales`.
- `mvn test` includes a protocol-level Streamable HTTP integration test that initializes a session, sends the initialized notification, lists all five tools and executes the safe coordinate tool; focused tests cover SQL schema/query bounds, validation, read-only database permissions and Central response parsing. The live Maven Central lookup itself was not called during these tests.

Reference: [Sonatype Central Search REST API](https://central.sonatype.org/search/rest-api-guide/).
