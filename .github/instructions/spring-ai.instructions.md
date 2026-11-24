---
description: "Use when: editing Spring AI 2.1.0 wiring — ChatClient beans, advisors (QuestionAnswerAdvisor, MessageChatMemoryAdvisor), tool registration, vector-store config, or the system prompt. Triggers: 'add a tool', 'add an advisor', 'change the system prompt', 'wire up memory', 'add a QuestionAnswerAdvisor', 'change embedding model'."
applyTo: "src/main/java/cloud/nolasco/simplechat/config/**,src/main/java/cloud/nolasco/simplechat/ChatController.java,**/*Advisor*.java,**/*Tools*.java"
---

# Spring AI 2.1.0 contract for simplechat

The Spring AI API moved hard between 1.x and 2.x and can move again. The notes below pin what is true *for 2.1.0 only* — the version in [`pom.xml`](pom.xml). Before bumping, read the upstream release notes and update this file.

## Where the wiring lives

| Concern | File | Notes |
| --- | --- | --- |
| `ChatClient` builder | [`config/ChatClientConfig.java`](src/main/java/cloud/nolasco/simplechat/config/ChatClientConfig.java) | Default advisors + tools + system prompt live here |
| `ChatMemory` bean | same file | Built on top of `JdbcChatMemoryRepository` + `MessageWindowChatMemory` |
| Per-request advisors | [`ChatController.java`](src/main/java/cloud/nolasco/simplechat/ChatController.java) | `QuestionAnswerAdvisor` is added **per request**, not as a default — keep it that way unless every endpoint needs RAG |
| `@Tool` methods | [`tools/datetime/CommonTools.java`](src/main/java/cloud/nolasco/simplechat/tools/datetime/CommonTools.java) | Spring discovers `@Tool`-annotated methods reflectively on beans passed to `defaultTools(...)` |
| Vector store config | [`src/main/resources/application.yaml`](src/main/resources/application.yaml) | `spring.ai.vectorstore.pgvector.*` — see pitfalls below |

## Advisor ordering is load-bearing

Advisors are **sorted by their `order` value**, not by the order they are registered — the lower the value, the earlier the advisor runs (it becomes the outermost one). The chain in this app:

| Advisor | Where it comes from | `order` |
| --- | --- | --- |
| `ToolCallingAdvisor` | auto-registered by Spring AI — not configured by this project | `Ordered.HIGHEST_PRECEDENCE + 300` |
| `MessageChatMemoryAdvisor` | `defaultAdvisors(...)` in [`ChatClientConfig.java`](src/main/java/cloud/nolasco/simplechat/config/ChatClientConfig.java) | `ToolCallingAdvisor.DEFAULT_ORDER + 100` |
| `QuestionAnswerAdvisor` | per request in [`ChatController.java`](src/main/java/cloud/nolasco/simplechat/ChatController.java) | `0` — innermost, immediately before the model call |

The memory order is deliberate. When Spring AI sees a `MemoryAdvisor` whose `order` is **higher** than the tool-calling advisor's, it disables the tool advisor's own conversation history, so memory owns history for every tool-call iteration. Placing `MessageChatMemoryAdvisor` at `ToolCallingAdvisor.DEFAULT_ORDER + 100` is what makes that happen — at the framework's own default the memory advisor sits *outside* the tool loop and the history it injects is dropped on 2.1 (see the pitfall row in [`AGENTS.md`](AGENTS.md)).

If you add a new global advisor, give it an `order` between `ToolCallingAdvisor.DEFAULT_ORDER + 100` and `0` so it runs after memory is loaded but before the model call.

## Deprecated APIs in 2.1.0

`ChatMemory`, `MessageChatMemoryAdvisor`, `MessageWindowChatMemory`, `JdbcChatMemoryRepository` and `PostgresChatMemoryRepositoryDialect` are deprecated since 2.1.0 and marked for removal in 3.0.0, in favour of the new session-memory API. The app still uses them on purpose — the implementation predates the replacement — so expect `Deprecated` warnings on the memory path. Do not "fix" them inside an unrelated change; if you migrate to session memory, do it in its own PR and re-check the ordering above, because the replacement changes how history is attached to the chain.

## Adding a new tool

```java
@Component  // or wire it as a bean in a @Configuration class
public class MyTools {

  @Tool(description = "Short, imperative description — the LLM reads this to decide when to call it")
  public String doThing(String input) { /* ... */ }
}
```

Then register it on the `ChatClient.Builder`:

```java
.defaultTools(commonTools, myTools)
```

Both must be Spring beans — pass the instances, never `new`-up a fresh object per request.

The description on `@Tool` is the **only** signal the model has for picking this tool, so be specific ("Get the current date and time in the user's timezone" — not "Get time"). Spring AI reflects on the instances you hand it, so a plain `new MyTools()` also works — but if the tool has dependencies, register it as a Spring bean and pass the bean, exactly as [`CommonTools`](src/main/java/cloud/nolasco/simplechat/tools/datetime/CommonTools.java) is wired.

## System prompt template variables

[`prompts/system-prompt.st`](src/main/resources/prompts/system-prompt.st) uses three placeholders, all populated from `ChatClientConfig`:

| Placeholder | Source | Type |
| --- | --- | --- |
| `{greeting}` | `CommonTools.greeting()` — an **instance** method, evaluated once when the `ChatClient` bean is built, so the token is fixed for the JVM's lifetime. Picks "Good morning/afternoon/evening" from the JVM's default time zone. | String |
| `{assistantName}` | hard-coded `"Sofía"` in `ChatClientConfig` | String |
| `{DevelopmentName}` | hard-coded `"Altavista"` in `ChatClientConfig` | String |

If you add a placeholder, add a corresponding `Map.of(...)` entry in `systemPromptTemplate.create(...)`. Spring will fail at startup if a `{...}` token has no value, so missing entries are loud.

## Vector store / embedding dimension coupling

The pgvector column is `vector(1024)` (see [`changelog-1.0.yaml`](src/main/resources/db/changelog/changelog-1.0.yaml)). If you change `EMBEDDING_MODEL` to one with a different dimension:

1. Add a new Liquibase changeset that drops + recreates the column (and the HNSW index) at the new dimension
2. Update `spring.ai.openai.embedding.model` in [`application.yaml`](src/main/resources/application.yaml)
3. Update the `EMBEDDING_MODEL` row in `AGENTS.md`
4. Re-embed the corpus. The existing rows were produced by the old model and are not comparable to it. `ReferenceDocsLoader` only ingests when `vector_store` is empty, so truncate that table (or drop it) before restarting, otherwise the stale embeddings are kept and the new model never runs.

There is no auto-detection. A mismatch produces a Postgres `ERROR: expected 1024 dimensions, not N` at insert time, not at startup.

## Per-request advisor params

`ChatController.chat(...)` calls `.advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, userId))` to thread the user id into `MessageChatMemoryAdvisor`. If you add another advisor that needs request-scoped data (e.g. a per-user retrieval filter on `QuestionAnswerAdvisor`), use the **same** `.advisors(spec -> ...)` builder — chaining `.advisors(...)` a second time is allowed and merges specs.

## Things to NOT do

- Do not move `QuestionAnswerAdvisor` to `defaultAdvisors(...)` — only `/stream/{userId}/chat` needs RAG, and `/history` would be slowed by retrieval it doesn't use.
- Do not change `MessageChatMemoryAdvisor`'s `order` back to the framework default — at that position the memory advisor wraps the tool-calling loop and the injected history is dropped on 2.1.
- Do not annotate the `MessageWindowChatMemory` bean with `@Primary` if you add a second `ChatMemory` bean — `ChatClient` picks the only one present, and `@Primary` on it would mask the real default.
- Do not call `chatClient.prompt()...stream().content()` from tests against a real `ChatClient` — see [`testing.instructions.md`](.github/instructions/testing.instructions.md) for the mock list.
- Do not use `classPath:` (capital `P`) — it works on case-insensitive filesystems but is non-standard. The codebase uses `classpath:` everywhere; keep it that way.
