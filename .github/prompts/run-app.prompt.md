---
description: "Run simplechat locally against an OpenAI-compatible AI endpoint. Sets the four required env vars and invokes ./mvnw spring-boot:run. Use when: 'run the app', 'start the server', 'launch simplechat', 'I want to chat with it'."
---

# Run simplechat locally

Boots the Spring Boot app on port 8082 against an OpenAI-compatible AI endpoint (Ollama, LM Studio, OpenAI, vLLM, etc.). Spring Boot's `spring.docker.compose` auto-starts the `chat_memory_db` (pgvector) and `grafana-lgtm` containers from the root [`compose.yaml`](compose.yaml) on app start.

## Required environment variables

| Variable | Purpose | Default in `application.yaml` |
| --- | --- | --- |
| `BASE_URL` | OpenAI-compatible chat/embeddings endpoint | `http://host.docker.internal:11434/v1` |
| `OPENAI_API_KEY` | Bearer token sent to `BASE_URL` | `sk-...` (placeholder) |
| `CHAT_MODEL` | Model name for chat completions | `granite4:7b-a1b-h` |
| `EMBEDDING_MODEL` | Model name for embeddings (must produce **1024-dim** vectors — see `vector(1024)` in [`changelog-1.0.yaml`](src/main/resources/db/changelog/changelog-1.0.yaml)) | `mxbai-embed-large` |

## Steps

1. **Confirm the four env vars are set.** If any are missing, ask the user for the values. If running inside the dev container, all four are usually already exported in `.env` (loaded by `spring.config.import: optional:file:./.env[.properties]`).
2. **Confirm `chat_memory_db` is reachable.** If `docker ps` shows it running, fine. If not, start it manually:

   ```bash
   docker compose up -d chat_memory_db grafana-lgtm
   ```

   Spring Boot's own `spring.docker.compose` will also start them on app start, so this step is optional.
3. **Build once if needed** (skipped on warm runs):

   ```bash
   ./mvnw -DskipTests package
   ```

4. **Run the app:**

   ```bash
   ./mvnw spring-boot:run
   ```

5. **Wait for `Started SimplechatApplication`** in the log. Then verify with:

   ```bash
   curl -s http://localhost:8082/actuator/health
   ```

   Expect `{"status":"UP"}`. (The `/stream/.../chat` endpoint requires a real model call and is harder to smoke-test with `curl`; use the included [`static/components/streamchat/streamchat.html`](src/main/resources/static/components/streamchat/streamchat.html) UI instead.)

## Common errors

| Symptom | Cause | Fix |
| --- | --- | --- |
| `Connection refused` on `/actuator/health` | App did not start | Check the log for `Failed to initialize vector store` or `Caused by: ...` |
| `401 Unauthorized` from `BASE_URL` | Bad `OPENAI_API_KEY` | Re-export and restart |
| `expected 1024 dimensions, not N` in startup logs | `EMBEDDING_MODEL` dimension mismatch with the pgvector column | Switch model to a 1024-dim one, or apply a new Liquibase changeset to resize the column |
| `pgvector extension not found` | `chat_memory_db` not running or wrong image | Confirm `docker ps | grep pgvector` |
| Container port 5432 already in use | A local Postgres is hogging the port | `docker compose down chat_memory_db`, or stop the local Postgres |

## After running

- Stop with `Ctrl+C` in the terminal that ran `spring-boot:run` — Spring Boot's `spring.docker.compose` hook will stop the auto-started containers on shutdown
- To keep `chat_memory_db` running between app restarts, use `docker compose up -d chat_memory_db` and set `spring.docker.compose.start.command=up -d` (or just leave the auto-start in place)

## Production note

This prompt runs the **dev** profile (no `SPRING_PROFILES_ACTIVE`). For the **prod** profile you must set `SPRING_PROFILES_ACTIVE=prod` plus `DB_HOST`, `POSTGRES_DB`, `DB_USERNAME`, `DB_PASSWORD`; see [`application-prod.yaml`](src/main/resources/application-prod.yaml) and the profiles table in [`AGENTS.md`](AGENTS.md).
