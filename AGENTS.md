# Simplechat Project Customization Guide

## Project Overview

- Spring Boot 4.0.6 application using Spring AI (version 2.0.0-M6) with OpenAI-compatible API
- PostgreSQL database with JdbcChatMemoryRepository for conversation storage
- Chat functionality via REST API with streaming support (`/stream/{userId}/chat`)
- RAG (Retrieval Augmented Generation) using SimpleVectorStore with context documents
- Environment-based configuration for AI endpoints (supports Ollama, OpenAI compatible APIs)

## Key Components

| Component          | File                                                                                           | Purpose                                                                 |
| ------------------ | ---------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------- |
| `ChatController`   | [ChatController.java](src/main/java/cloud/nolasco/simplechat/ChatController.java)              | REST endpoints for streaming chat, history, and memory management       |
| `ChatClientConfig` | [ChatClientConfig.java](src/main/java/cloud/nolasco/simplechat/config/ChatClientConfig.java)   | Builds ChatClient with system prompt, memory advisor, and DateTimeTools |
| `RagConfiguration` | [RagConfiguration.java](src/main/java/cloud/nolasco/simplechat/config/RagConfiguration.java)   | VectorStore initialization from context documents                       |
| `DateTimeTools`    | [DateTimeTools.java](src/main/java/cloud/nolasco/simplechat/tools/datetime/DateTimeTools.java) | Tool for getting current date/time and generating greetings             |
| `system-prompt.st` | [system-prompt.st](src/main/resources/prompts/system-prompt.st)                                | System prompt template for the AI (uses placeholder variables)          |

## Build & Run Commands

```bash
# Build
./mvnw clean package

# Run (requires environment variables)
docker compose up -d    # Uses compose.yaml for PostgreSQL + app

# Run locally (set env vars first)
export BASE_URL=http://host.docker.internal:11434/v1
export OPENAI_API_KEY=sk-...
export CHAT_MODEL=granite4:7b-a1b-h
export EMBEDDING_MODEL=mxbai-embed-large
export DB_HOST=localhost DB_PORT=5432 DB_USERNAME=postgres DB_PASSWORD=postgres
./mvnw spring-boot:run
```

## Configuration

Environment variables (see [application.yaml](src/main/resources/application.yaml)):

- `BASE_URL` - AI endpoint (default: `http://host.docker.internal:11434/v1`)
- `OPENAI_API_KEY` - API key (default: `sk-...`)
- `CHAT_MODEL` - Chat model identifier (e.g., `granite4:7b-a1b-h`)
- `EMBEDDING_MODEL` - Embedding model (e.g., `mxbai-embed-large`)
- `DB_HOST`, `DB_PORT`, `DB_USERNAME`, `DB_PASSWORD` - PostgreSQL connection

## Context Documents (RAG)

Located in [src/main/resources/context/](src/main/resources/context/):

- `project-overview.md` - Main website content
- `project-brochure.md` - General brochure info
- `project-inventory.md` - Inventory listings
- `developer-profile.md` - Company info

## Conventions

- All Java code under `src/main/java/cloud/nolasco/simplechat`
- Prompts in `resources/prompts/` (Spring Resource path: `classPath:/prompts/`)
- VectorStore persistence in `resources/data/vectorstore.json`
- Database schema via Liquibase (`resources/db/changelog/`)
- System prompt uses template variables: `{greeting}`, `{assistantName}`, `{DevelopmentName}`

## Potential Pitfalls

- **Spring AI 2.0.0-M6**: Milestone release—check for breaking changes when upgrading
- **Reactor streaming**: `chat()` returns `Flux<String>`; errors handled with `.retry(3).onErrorResume()`
- **Liquibase migrations**: Must run in order; check `db.changelog-master.yaml`
- **VectorStore file path**: Uses `src/main/resources/data/` at runtime (not `target/`)

## API Endpoints

| Method | Path                    | Description                                     |
| ------ | ----------------------- | ----------------------------------------------- |
| POST   | `/stream/{userId}/chat` | Stream chat response (body: plain text request) |
| GET    | `/{userId}/history`     | Get conversation history                        |
| DELETE | `/{userId}/history`     | Clear conversation history                      |
