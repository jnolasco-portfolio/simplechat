# Simplechat Project Customization Guide

## Project Overview

- Spring Boot **4.0.6** application using **Spring AI (version 2.0.0-M6)** with OpenAI-compatible API
- PostgreSQL database with **JdbcChatMemoryRepository** for conversation storage
- Chat functionality via REST API with **streaming support** (`/stream/{userId}/chat`)
- **RAG** (Retrieval Augmented Generation) using auto-configured **PgVectorStore** with context documents
- Environment-based configuration for AI endpoints (supports **Ollama**, OpenAI compatible APIs)

## Build & Run Commands

```bash
# Build
./mvnw clean package

# Run tests (uses H2 in-memory DB via application-test.yaml profile)
./mvnw clean test

# Run with Docker Compose (PostgreSQL + app)
docker compose up -d

# Run locally (set env vars first)
export BASE_URL=http://host.docker.internal:11434/v1
export OPENAI_API_KEY=sk-...
export CHAT_MODEL=granite4:7b-a1b-h
export EMBEDDING_MODEL=mxbai-embed-large
export DB_HOST=localhost DB_PORT=5432 DB_USERNAME=postgres DB_PASSWORD=postgres
./mvnw spring-boot:run
```

## Key Components

| Component            | File | Purpose |
| -------------------- | ---- | ------- |
| `ChatController`     | [ChatController.java](src/main/java/cloud/nolasco/simplechat/ChatController.java) | REST endpoints for streaming chat, history, and memory management |
| `ChatClientConfig`   | [ChatClientConfig.java](src/main/java/cloud/nolasco/simplechat/config/ChatClientConfig.java) | Builds ChatClient with system prompt, memory advisor, and DateTimeTools |
| `IngestService`      | [IngestService.java](src/main/java/cloud/nolasco/simplechat/service/IngestService.java) | Loads context documents into the vector store at startup |
| `ReferenceDocsLoader`| [ReferenceDocsLoader.java](src/main/java/cloud/nolasco/simplechat/service/ReferenceDocsLoader.java) | Reads markdown files from `resources/context/` into the vector store |
| `DateTimeTools`      | [DateTimeTools.java](src/main/java/cloud/nolasco/simplechat/tools/datetime/DateTimeTools.java) | Tool for `getCurrentDateTime()` and static `greeting()` method |

## Configuration

Environment variables (see [application.yaml](src/main/resources/application.yaml)):

| Variable | Default | Description |
| -------- | ------- | ----------- |
| `BASE_URL` | `http://host.docker.internal:11434/v1` | AI endpoint |
| `OPENAI_API_KEY` | `sk-...` | API key |
| `CHAT_MODEL` | `granite4:7b-a1b-h` | Chat model identifier |
| `EMBEDDING_MODEL` | `mxbai-embed-large` | Embedding model |
| `DB_HOST` | `localhost` | PostgreSQL host |
| `DB_PORT` | `5432` | PostgreSQL port |
| `DB_USERNAME` | `postgres` | Database user |
| `DB_PASSWORD` | `postgres` | Database password |

## Context Documents (RAG)

Located in [src/main/resources/context/](src/main/resources/context/):
- `project-overview.md` - Main website content
- `project-brochure.md` - General brochure info
- `project-inventory.md` - Inventory listings
- `developer-profile.md` - Company info

VectorStore is persisted at `src/main/resources/data/vectorstore.json` (loaded at runtime via `target/` path).

## Testing

Tests use a dedicated **`test`** profile with H2 in-memory database — no PostgreSQL or Docker required.

| Item | Location |
| ---- | -------- |
| Test profile config | [src/test/resources/application-test.yaml](src/test/resources/application-test.yaml) |
| Test class | [SimplechatApplicationTests.java](src/test/java/cloud/nolasco/simplechat/SimplechatApplicationTests.java) |

**How it works:**

- `@ActiveProfiles("test")` on the test class loads `application-test.yaml`
- H2 runs in `MODE=PostgreSQL` for maximum compatibility with the JPA entity mappings
- **Liquibase is disabled** — the production changelog uses PostgreSQL-specific `pg_extension` syntax that H2 cannot execute
- **Vector store schema initialization is disabled** — the `vector_store` table does not need to exist for a context-load smoke test
- **`ChatClient`, `ChatMemory`, and `IngestService` are mocked** via `@TestConfiguration` so tests don't call the real AI service
- `spring.main.allow-bean-definition-overriding=true` allows the test mocks to override the production `ChatClient`/`ChatMemory` beans defined in [ChatClientConfig.java](src/main/java/cloud/nolasco/simplechat/config/ChatClientConfig.java)

## Database Schema

Managed by **Liquibase** via [db.changelog-master.yaml](src/main/resources/db/changelog/db.changelog-master.yaml):
- `SPRING_AI_CHAT_MEMORY` table for conversation history
- Type constraint check (`SPRING_AI_CHAT_MEMORY_TYPE_CHECK`)

⚠️ **Migration order matters** - Liquibase runs changesets in sequence by id.

## Conventions

- Java code: `src/main/java/cloud/nolasco/simplechat/`
- Prompts: `resources/prompts/` → Spring Resource path: `classPath:/prompts/`
- VectorStore: `resources/data/vectorstore.json`
- System prompt uses template variables: `{greeting}`, `{assistantName}`, `{DevelopmentName}`

## Potential Pitfalls

| Issue | Details |
| ----- | ------- |
| **Spring AI 2.0.0-M6** | Milestone release—check for breaking changes when upgrading |
| **Reactor streaming** | `chat()` returns `Flux<String>`; errors handled with `.retry(3).onErrorResume()` |
| **Liquibase migrations** | Must run in order; check `db.changelog-master.yaml`. **Disabled in tests** (uses PostgreSQL `pg_extension`) |
| **VectorStore path** | Uses `src/main/resources/data/` at runtime (resolved via classpath, not `target/`) |
| **No Testcontainers** | Tests rely on H2; no Docker daemon is required to run `./mvnw test` |
| **`classPath:` typo** | In [ChatClientConfig.java](src/main/java/cloud/nolasco/simplechat/config/ChatClientConfig.java) the resource is `@Value("classPath:/prompts/system-prompt.st")` (capital `P`). It works on case-insensitive filesystems but is non-standard—should be `classpath:` |

## API Endpoints

| Method | Path | Description |
| ------ | ---- | ----------- |
| POST | `/stream/{userId}/chat` | Stream chat response (body: plain text request) |
| GET | `/{userId}/history` | Get conversation history |
| DELETE | `/{userId}/history` | Clear conversation history |
