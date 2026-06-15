---
description: "Run simplechat locally against an OpenAI-compatible AI endpoint. Sets the four required env vars and invokes ./mvnw spring-boot:run. Use when: 'run the app', 'start the server', 'launch simplechat', 'I want to chat with it'."
---

# Run simplechat locally

Boots the Spring Boot app on port 8082 against an OpenAI-compatible AI endpoint (Ollama, LM Studio, OpenAI, vLLM, etc.). Spring Boot's `spring.docker.compose` auto-starts the full dev graph — `chat_memory_db` (pgvector), `grafana-lgtm` (UI on `http://localhost:3002`), and the `otel-collector` fan-out — from the root [`compose.yaml`](compose.yaml) and its `include:`d per-tool files under [`infra/`](infra/) on app start.

## Required environment variables

| Variable | Purpose | Default in `application.yaml` |
| --- | --- | --- |
| `BASE_URL` | OpenAI-compatible chat/embeddings endpoint | `http://host.docker.internal:11434/v1` |
| `OPENAI_API_KEY` | Bearer token sent to `BASE_URL` | `sk-...` (placeholder) |
| `CHAT_MODEL` | Model name for chat completions | `granite4:7b-a1b-h` |
| `EMBEDDING_MODEL` | Model name for embeddings (must produce **1024-dim** vectors — see `vector(1024)` in [`changelog-1.0.yaml`](src/main/resources/db/changelog/changelog-1.0.yaml)) | `mxbai-embed-large` |

## Steps

1. **Confirm the four env vars are set.** If any are missing, ask the user for the values. If running inside the dev container, all four are usually already exported in `.env` (loaded by `spring.config.import: optional:file:./.env[.properties]`).
2. **Confirm `chat_memory_db` is reachable.** If `docker ps` shows it running, fine. If not, start it manually. The root `compose.yaml` `include:`s lgtm + the otel-collector, so any `docker compose` command brings up the whole graph:

   ```bash
   docker compose up -d chat_memory_db
   ```

   Spring Boot's own `spring.docker.compose` will also start the full graph on app start, so this step is optional. (The OTel collector is a transparent fan-out — the app's OTel URLs still point at `localhost:4318`; the collector forwards to grafana-lgtm, and to langfuse if you start the langfuse stack with `LANGFUSE_OTEL_AUTH` set.)
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
| Grafana UI not at 3000 | Port 3000 is reserved for langfuse-web; grafana-lgtm is on **3002** | Open `http://localhost:3002` instead |
| 401/403 from langfuse-web in collector logs | `LANGFUSE_OTEL_AUTH` is unset or stale | Export a fresh `Basic <base64(public_key:secret_key)>` and `docker compose restart otel-collector` |

## After running

- Stop with `Ctrl+C` in the terminal that ran `spring-boot:run` — Spring Boot's `spring.docker.compose` hook will stop the auto-started containers on shutdown
- To keep `chat_memory_db` running between app restarts, use `docker compose up -d chat_memory_db` and set `spring.docker.compose.start.command=up -d` (or just leave the auto-start in place)

## Production note

This prompt runs the **dev** profile (no `SPRING_PROFILES_ACTIVE`). For the **prod** profile you must set `SPRING_PROFILES_ACTIVE=prod` plus `DB_HOST`, `POSTGRES_DB`, `DB_USERNAME`, `DB_PASSWORD`; see [`application-prod.yaml`](src/main/resources/application-prod.yaml) and the profiles table in [`AGENTS.md`](AGENTS.md).
