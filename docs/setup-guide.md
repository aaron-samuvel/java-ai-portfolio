# Setup guide

## Available tools on the development machine

| Tool | Discovered version/state | Use |
|---|---|---|
| Java | Temurin 21.0.12 | Application runtime |
| Maven | 3.10.0 | Java builds |
| Node.js | 24.14.0 | DocuMind web client and Vitest tests |
| Git | 2.47.1 | Local version control |
| Docker CLI | 29.8.2; engine available with elevated access (default sandbox denied its named pipe) | DocuMind image and Compose integration |
| Gradle | Installed, native startup failure | Not used |
| Ollama | Installed | Local inference; model availability not yet checked |

## DocuMind prerequisites

1. Install or start PostgreSQL 16+ with the `vector`, `hstore` and `uuid-ossp` extensions.
2. Create a database and configure `DOCUMIND_DB_URL`, `DOCUMIND_DB_USERNAME` and `DOCUMIND_DB_PASSWORD`.
3. Start Ollama and pull `qwen2.5:7b` and `nomic-embed-text`.
4. Run the Spring application from `projects/01-documind`, or use Compose for the API and PostgreSQL while pointing it at a native Ollama instance.

The default URL assumes PostgreSQL on `localhost:5432`, database/user/password `documind`. Change the environment values for your setup. Uploads are written below `data/documind/uploads` by default and are excluded from Git.

## Run commands

To run one application from the repository root:

```powershell
./run-project.ps1 -Project 04-research-crew
```

To launch all twelve APIs after configuring their dependencies, run `./start-portfolio.ps1`; it waits up to 90 seconds for health checks and writes logs/PIDs under the ignored `.run/` directory. Stop only those managed process trees with `./stop-portfolio.ps1`. DocuMind needs PostgreSQL with pgvector and Ollama; apps that fail startup remain visible in their per-project `.err.log` file.

To run all focused unit tests:

```powershell
./test-all.ps1
```

Each project can also be started and tested directly from its own directory with `mvn spring-boot:run` and `mvn test`.

```powershell
cd projects/01-documind
mvn spring-boot:run
```

For the containerized API and database, copy `.env.example` to `.env` in the DocuMind folder, adjust the local-only password if needed, and run `docker compose up --build`. The default Ollama URL uses the host machine so an existing native installation can use its configured hardware. For the web client, run `npm install` and `npm run dev` in the `frontend` folder.

The first build may need Maven Central access to resolve dependencies. If network access is unavailable, Maven reports the missing artifact and the application cannot start until the dependency is available locally.

