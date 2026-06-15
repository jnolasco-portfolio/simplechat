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

# Dev run — Spring Boot's Docker Compose auto-starts the full dev graph
# (chat_memory_db + grafana-lgtm + otel-collector) from compose.yaml via
# the `include:` mechanism. The AI provider is NOT a Docker service in dev.
export BASE_URL=http://host.docker.internal:11434/v1
export OPENAI_API_KEY=sk-...
export CHAT_MODEL=granite4:7b-a1b-h
export EMBEDDING_MODEL=mxbai-embed-large
./mvnw spring-boot:run
```

Manual lifecycle (the root `compose.yaml` `include:`s lgtm + collector, so
any `docker compose` command brings up the whole graph; pass service names
to start a subset):

```bash
docker compose up -d chat_memory_db                     # db only
docker compose up -d chat_memory_db grafana-lgtm otel-collector  # all dev deps
docker compose down                                     # stops everything
```

Langfuse is **opt-in** (it pulls in ClickHouse + MinIO + Redis — heavy
for a hot dev loop). See the [Docker Compose Layout](#docker-compose-layout)
section.

## Docker Compose Layout

The dev stack is split across a **root** `compose.yaml` and a per-tool
`infra/` tree so the dev container, Spring Boot's `spring.docker.compose`
integration, and future k8s manifests all consume the same folder
structure. The root file is intentionally small (Spring Boot only
auto-detects the root) and `include:`s the per-tool compose files.

| File / folder | Consumer | Services | Started when |
| --- | --- | --- | --- |
| [`compose.yaml`](compose.yaml) (project root) | Spring Boot `spring.docker.compose` auto-detection; manual `docker compose up` for non-dev-container workflows | `chat_memory_db` (pgvector) | `./mvnw spring-boot:run` |
| [`.devcontainer/compose.yaml`](.devcontainer/compose.yaml) | [`.devcontainer/devcontainer.json`](.devcontainer/devcontainer.json) | `service` (the dev container itself) | Dev container start |
| [`infra/lgtm/compose.yaml`](infra/lgtm/compose.yaml) | `include:`d by the root | `grafana-lgtm` (UI on host port 3002) | `./mvnw spring-boot:run` (via the root) |
| [`infra/observability/otel-collector/compose.yaml`](infra/observability/otel-collector/compose.yaml) | `include:`d by the root | `otel-collector` (OTLP gRPC:4317, HTTP:4318) | `./mvnw spring-boot:run` (via the root) |
| [`infra/langfuse/compose.yaml`](infra/langfuse/compose.yaml) | Standalone (opt-in) | `langfuse-web` (UI on 3000), `langfuse-worker`, plus Postgres/ClickHouse/Redis/MinIO | Manual — see below |

### Observability data path

The OTel collector in `infra/observability/otel-collector/` is the
fan-out hub. The Spring app's existing OTel endpoints
(`http://localhost:4317` / `http://localhost:4318` for traces, metrics,
and logs — see [`application.yaml`](src/main/resources/application.yaml))
hit the collector, which then forwards:

- **traces**  → BOTH `grafana-lgtm:4318` AND `langfuse-web:3000/api/public/otel/v1/traces` (with `Authorization: Basic <…>` from the `LANGFUSE_OTEL_AUTH` env var)
- **metrics** → `grafana-lgtm:4318` only
- **logs**    → `grafana-lgtm:4318` only (Langfuse does not ingest metrics or logs)

The app's OTel URLs didn't change — the collector transparently replaced
the direct LGTM connection with a fan-out. To enable the langfuse
exporter, start the langfuse stack and export `LANGFUSE_OTEL_AUTH="Basic
$(printf '%s:%s' "$LANGFUSE_PUBLIC_KEY" "$LANGFUSE_SECRET_KEY" | base64 -w0)"`,
then `docker compose restart otel-collector`.

### Langfuse (opt-in)

```bash
cp infra/langfuse/.env.example infra/langfuse/.env    # fill in the CHANGEME secrets
docker compose -f infra/langfuse/compose.yaml \
               --env-file infra/langfuse/.env up -d
# wait for "Ready" in `docker logs -f langfuse-web-1` (~2-3 min on a warm host)
# open http://localhost:3000, create a project, copy the public + secret keys
# export LANGFUSE_OTEL_AUTH="Basic $(printf '%s:%s' "$LANGFUSE_PUBLIC_KEY" \
#                                            "$LANGFUSE_SECRET_KEY" | base64 -w0)"
# docker compose restart otel-collector
```

> **`hostname: 0.0.0.0` on `langfuse-web` is intentional.** The
> service is on two networks (`langfuse-internal` and `simplechat-net`)
> and Next.js reads `HOSTNAME` (defaulting to the container id) to
> pick the bind interface. With two networks, Docker may route the
> host:3000 port mapping to the IP on the network that's "primary"
> (langfuse-internal) while Next.js binds to the IP on the other
> (simplechat-net), breaking both the host UI and the otel-collector
> export. Pinning `hostname: 0.0.0.0` (and `HOSTNAME=0.0.0.0`) makes
> Next.js listen on all interfaces. Keep it.

### Network

All included services join the shared `simplechat-net` user-defined
network declared in the root compose file. The dev-container compose
file uses `name: simplechat` so the dev container and the root compose
share the same docker-compose *project* and therefore the same network
namespace. If you clone the repo into a folder with a different name,
set `COMPOSE_PROJECT_NAME=simplechat` for both compose files, or rename
the folder.

> The dev container's compose file intentionally omits
> `depends_on: chat_memory_db` because that service is defined in a
> different file. The DB is started later, when Spring Boot boots the
> app.

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
) |
| **Grafana UI port (3002, not 3000)** | `grafana-lgtm` moved to host port 3002 in `infra/lgtm/compose.yaml` — host port 3000 is reserved for `langfuse-web` if you ever start the langfuse stack |
| **OTel collector owns 4317/4318** | The Spring app's OTel URLs in [`application.yaml`](src/main/resources/application.yaml) point at `http://localhost:4318`; that now hits the **otel-collector** (fan-out), not LGTM directly. If you delete the collector, signals will silently fail |
| **Langfuse port collisions** | `langfuse-web` wants host 3000, the langfuse stack's own postgres wants 5432. Both are remapped (3000/5432 host, langfuse's pg → 127.0.0.1:5433) to coexist with `chat_memory_db` and `grafana-lgtm` |
| **`infra/*/compose.yaml` standalone mode** | The lgtm and otel-collector files pin `simplechat-net` by name (no `external: true`). They work both included (root owns the network) and standalone (Compose creates a `simplechat-net` network on the fly). Do **not** add `external: true` back — Compose's `include:` merge does not override it, and the merged project then fails with `network simplechat-net declared as external, but could not be found` |
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
