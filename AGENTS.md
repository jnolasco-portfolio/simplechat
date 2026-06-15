# Simplechat Project Customization Guide

## Project Overview

- Spring Boot **4.1.0** REST app using **Spring AI 2.0.0-RC2** with an OpenAI-compatible API
- PostgreSQL + pgvector for both chat memory ([`JdbcChatMemoryRepository`](src/main/java/cloud/nolasco/simplechat/config/ChatClientConfig.java)) and RAG (auto-configured `PgVectorStore`)
- Streaming chat via [`/stream/{userId}/chat`](src/main/java/cloud/nolasco/simplechat/ChatController.java) returning `Flux<String>`
- Java 21, Maven (`./mvnw`)

## Build & Run

```bash
# Build
./mvnw clean package

# Tests (H2 in-memory via `test` profile; no Docker needed)
./mvnw test

# Dev run — Spring Boot's Docker Compose auto-starts `chat_memory_db` + `grafana-lgtm`
# from compose.yaml. The AI provider is NOT a Docker service in dev.
export BASE_URL=http://host.docker.internal:11434/v1
export OPENAI_API_KEY=sk-...
export CHAT_MODEL=granite4:7b-a1b-h
export EMBEDDING_MODEL=mxbai-embed-large
./mvnw spring-boot:run
```

Manual DB lifecycle (if you need to control Postgres independently of the app):

```bash
docker compose up -d chat_memory_db grafana-lgtm
docker compose down
```

## Docker Compose Layout (two files on purpose)

The dev stack is split across **two** compose files so the dev container and Spring
Boot's `spring.docker.compose` each manage their own services and never collide:

| File | Consumer | Services |
| --- | --- | --- |
| [`compose.yaml`](compose.yaml) (project root) | Spring Boot `spring.docker.compose` auto-detection; manual `docker compose up` for non-dev-container workflows | `chat_memory_db` (pgvector), `grafana-lgtm` |
| [`.devcontainer/compose.yaml`](.devcontainer/compose.yaml) | [`.devcontainer/devcontainer.json`](.devcontainer/devcontainer.json) | `service` (the dev container itself) |

Both compose files share a project name (`name: simplechat` in the devcontainer
file) and the default network, so the dev container can reach the DB by service
name (`chat_memory_db:5432`). If you clone the repo into a folder with a
different name, set `COMPOSE_PROJECT_NAME=simplechat` so they stay on the same
network — or rename the folder to `simplechat`.

> The dev container's compose file intentionally omits `depends_on: chat_memory_db`
> because that service is defined in a different file. The DB is started later by
> Spring Boot at app boot.

## Profiles

| Profile | Active when | DataSource | Compose |
| --- | --- | --- | --- |
| *(default / dev)* | no `SPRING_PROFILES_ACTIVE` | Auto-wired from `compose.yaml` container | Active |
| `test` | `@ActiveProfiles("test")` | H2 in-memory ([`application-test.yaml`](src/test/resources/application-test.yaml)) | Skipped (`skip.in-tests: true`) |
| `prod` | `SPRING_PROFILES_ACTIVE=prod` | Explicit JDBC URL ([`application-prod.yaml`](src/main/resources/application-prod.yaml)) | Excluded from fat JAR |

In production you **must** set `SPRING_PROFILES_ACTIVE=prod` and provide
`DB_HOST`, `POSTGRES_DB`, `DB_USERNAME`, `DB_PASSWORD`. The prod profile uses
Spring's `${VAR:?message}` placeholder syntax, so the app fails fast at startup
with a clear error if any required variable is missing.

## Key Components

| Component | File | Purpose |
| --- | --- | --- |
| `ChatController` | [`ChatController.java`](src/main/java/cloud/nolasco/simplechat/ChatController.java) | REST endpoints for streaming chat, history, and memory management |
| `ChatClientConfig` | [`config/ChatClientConfig.java`](src/main/java/cloud/nolasco/simplechat/config/ChatClientConfig.java) | Builds `ChatClient` with system prompt, `MessageChatMemoryAdvisor`, and `DateTimeTools` |
| `ReferenceDocsLoader` | [`service/ReferenceDocsLoader.java`](src/main/java/cloud/nolasco/simplechat/service/ReferenceDocsLoader.java) | Loads RAG context documents into the `vector_store` table on `@PostConstruct` |
| `IngestService` | [`service/IngestService.java`](src/main/java/cloud/nolasco/simplechat/service/IngestService.java) | Stub (currently empty) |
| `DateTimeTools` | [`tools/datetime/DateTimeTools.java`](src/main/java/cloud/nolasco/simplechat/tools/datetime/DateTimeTools.java) | `@Tool` for `getCurrentDateTime()`; static `greeting()` used in the system prompt |
| `InstallOpenTelemetryAppender` | [`config/InstallOpenTelemetryAppender.java`](src/main/java/cloud/nolasco/simplechat/config/InstallOpenTelemetryAppender.java) | Wires the OTel logback appender to the LGTM stack started by compose |

## Configuration

Env vars (see [`application.yaml`](src/main/resources/application.yaml)):

| Variable | Default | Description |
| --- | --- | --- |
| `BASE_URL` | `http://host.docker.internal:11434/v1` | OpenAI-compatible AI endpoint |
| `OPENAI_API_KEY` | `sk-...` | API key |
| `CHAT_MODEL` | `granite4:7b-a1b-h` | Chat model identifier |
| `EMBEDDING_MODEL` | `mxbai-embed-large` | Embedding model (1024-dim; must match `vector(1024)` in the changelog) |
| `DB_HOST` | `localhost` | PostgreSQL host (prod) |
| `DB_PORT` | `5432` | PostgreSQL port |
| `DB_USERNAME` | `postgres` | Database user |
| `DB_PASSWORD` | `postgres` | Database password |

## RAG (Retrieval Augmented Generation)

- Source documents: [`src/main/resources/context/`](src/main/resources/context/) (markdown files)
- `ReferenceDocsLoader` reads them at startup, chunks with `TokenTextSplitter` (chunk size 128), and writes to the `vector_store` table in PostgreSQL.
- At chat time, [`ChatController`](src/main/java/cloud/nolasco/simplechat/ChatController.java) injects `QuestionAnswerAdvisor` over the auto-configured `PgVectorStore`.

## Testing

Tests use a dedicated **`test`** profile with H2 in-memory — no PostgreSQL or Docker required. See [`.github/instructions/testing.instructions.md`](.github/instructions/testing.instructions.md) for the constraints an agent must respect when adding tests.

| Item | Location |
| --- | --- |
| Test profile config | [`src/test/resources/application-test.yaml`](src/test/resources/application-test.yaml) |
| Test class | [`src/test/java/cloud/nolasco/simplechat/SimplechatApplicationTests.java`](src/test/java/cloud/nolasco/simplechat/SimplechatApplicationTests.java) |

## Database Schema

Managed by **Liquibase** via [`db.changelog-master.yaml`](src/main/resources/db/changelog/db.changelog-master.yaml):

- `SPRING_AI_CHAT_MEMORY` — chat history
- `SPRING_AI_CHAT_MEMORY_TYPE_CHECK` — check constraint on message type
- `vector_store` — pgvector embeddings (1024-dim, HNSW index)
- Required extensions: `vector`, `hstore`, `uuid-ossp`

> ⚠️ **Migration order matters** — changesets run in sequence by id. **Disabled in tests** — the production changelog uses `CREATE EXTENSION` and `vector(...)` syntax H2 cannot execute.

## Conventions

- Java code: `src/main/java/cloud/nolasco/simplechat/`
- Prompts: `src/main/resources/prompts/` → Spring `classpath:/prompts/`
- System prompt template variables: `{greeting}`, `{assistantName}`, `{DevelopmentName}` (see [`system-prompt.st`](src/main/resources/prompts/system-prompt.st))
- Static UI: [`static/components/chat/`](src/main/resources/static/components/chat/) and [`static/components/streamchat/`](src/main/resources/static/components/streamchat/)

## Pitfalls

| Issue | Details |
| --- | --- |
| **Spring AI 2.0.0-RC2** | Release-candidate — check the changelog for breaking changes before bumping |
| **Reactor streaming** | `chat()` returns `Flux<String>`; errors handled with `.retry(3).onErrorResume()`. Do not remove the `retry` |
| **Liquibase migrations** | Run in order per the master changelog. Disabled in tests; the production changelog is not H2-compatible |
| **No Testcontainers** | Tests rely on H2; no Docker daemon is required to run `./mvnw test` |
| **`classPath:` typo** | In [`ChatClientConfig.java`](src/main/java/cloud/nolasco/simplechat/config/ChatClientConfig.java) the resource is `@Value("classPath:/prompts/system-prompt.st")` (capital `P`). It works on case-insensitive filesystems but is non-standard — should be `classpath:` |
| **Vector store dimensions** | The pgvector column is `vector(1024)`; the embedding model dimension (set in `application.yaml`) must match. Switching to a different embedding model requires a migration |
| **Dev container project name** | The two compose files only share a network if the docker-compose project name is `simplechat`. See [Docker Compose Layout](#docker-compose-layout-two-files-on-purpose) |

## API Endpoints

| Method | Path | Description |
| --- | --- | --- |
| POST | `/stream/{userId}/chat` | Stream chat response (body: plain text request) |
| GET | `/{userId}/history` | Get conversation history |
| DELETE | `/{userId}/history` | Clear conversation history |
