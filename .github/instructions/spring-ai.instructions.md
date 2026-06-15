---
description: "Use when: editing Spring AI 2.0.0-RC2 wiring — ChatClient beans, advisors (QuestionAnswerAdvisor, MessageChatMemoryAdvisor), tool registration, vector-store config, or the system prompt. Triggers: 'add a tool', 'add an advisor', 'change the system prompt', 'wire up memory', 'add a QuestionAnswerAdvisor', 'change embedding model'."
applyTo: "src/main/java/cloud/nolasco/simplechat/config/**,src/main/java/cloud/nolasco/simplechat/ChatController.java,**/*Advisor*.java,**/*Tools*.java"
---

# Spring AI 2.0.0-RC2 contract for simplechat

Spring AI is on a **release-candidate** line. Bumping versions is not free — assume the public API moves. The notes below pin what is true *for 2.0.0-RC2 only*. Before bumping, read the upstream release notes and update this file.

## Where the wiring lives

| Concern | File | Notes |
| --- | --- | --- |
| `ChatClient` builder | [`config/ChatClientConfig.java`](src/main/java/cloud/nolasco/simplechat/config/ChatClientConfig.java) | Default advisors + tools + system prompt live here |
| `ChatMemory` bean | same file | Built on top of `JdbcChatMemoryRepository` + `MessageWindowChatMemory` |
| Per-request advisors | [`ChatController.java`](src/main/java/cloud/nolasco/simplechat/ChatController.java) | `QuestionAnswerAdvisor` is added **per request**, not as a default — keep it that way unless every endpoint needs RAG |
| `@Tool` methods | [`tools/datetime/DateTimeTools.java`](src/main/java/cloud/nolasco/simplechat/tools/datetime/DateTimeTools.java) | Spring discovers `@Tool`-annotated methods reflectively on beans passed to `defaultTools(...)` |
| Vector store config | [`src/main/resources/application.yaml`](src/main/resources/application.yaml) | `spring.ai.vectorstore.pgvector.*` — see pitfalls below |

## Advisor ordering is load-bearing

`defaultAdvisors(...)` runs advisors in the order they are registered. For this app the intended order is:

1. `MessageChatMemoryAdvisor` — fetches prior turns **before** the new user message is processed
2. *(no global RAG advisor — `QuestionAnswerAdvisor` is added per request in the controller)*

If you add a new global advisor, put it **after** `MessageChatMemoryAdvisor` and **before** any future RAG advisor so memory is always populated first.

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
.defaultTools(new DateTimeTools(), new MyTools())
```

The description on `@Tool` is the **only** signal the model has for picking this tool, so be specific ("Get the current date and time in the user's timezone" — not "Get time"). Spring resolves the tool from the bean instance passed to `defaultTools`, so the class **must be a Spring bean** (or constructed inline as above).

## System prompt template variables

[`prompts/system-prompt.st`](src/main/resources/prompts/system-prompt.st) uses three placeholders, all populated from `ChatClientConfig`:

| Placeholder | Source | Type |
| --- | --- | --- |
| `{greeting}` | `DateTimeTools.greeting()` static — picks "Good morning/afternoon/evening" from server time | String |
| `{assistantName}` | hard-coded `"Sofía"` in `ChatClientConfig` | String |
| `{DevelopmentName}` | hard-coded `"Altavista"` in `ChatClientConfig` | String |

If you add a placeholder, add a corresponding `Map.of(...)` entry in `systemPromptTemplate.create(...)`. Spring will fail at startup if a `{...}` token has no value, so missing entries are loud.

## Vector store / embedding dimension coupling

The pgvector column is `vector(1024)` (see [`changelog-1.0.yaml`](src/main/resources/db/changelog/changelog-1.0.yaml)). If you change `EMBEDDING_MODEL` to one with a different dimension:

1. Add a new Liquibase changeset that drops + recreates the column (and the HNSW index) at the new dimension
2. Update `spring.ai.openai.embedding.model` in [`application.yaml`](src/main/resources/application.yaml)
3. Update the `EMBEDDING_MODEL` row in `AGENTS.md`
4. Bump `ReferenceDocsLoader` chunk size only if the model context window changed

There is no auto-detection. A mismatch produces a Postgres `ERROR: expected 1024 dimensions, not N` at insert time, not at startup.

## Per-request advisor params

`ChatController.chat(...)` calls `.advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, userId))` to thread the user id into `MessageChatMemoryAdvisor`. If you add another advisor that needs request-scoped data (e.g. a per-user retrieval filter on `QuestionAnswerAdvisor`), use the **same** `.advisors(spec -> ...)` builder — chaining `.advisors(...)` a second time is allowed and merges specs.

## Things to NOT do

- Do not move `QuestionAnswerAdvisor` to `defaultAdvisors(...)` — only `/stream/{userId}/chat` needs RAG, and `/history` would be slowed by retrieval it doesn't use.
- Do not annotate the `MessageWindowChatMemory` bean with `@Primary` if you add a second `ChatMemory` bean — `ChatClient` picks the only one present, and `@Primary` on it would mask the real default.
- Do not call `chatClient.prompt()...stream().content()` from tests against a real `ChatClient` — see [`testing.instructions.md`](.github/instructions/testing.instructions.md) for the mock list.
- Do not use `classPath:` (capital `P`) — it works on case-insensitive filesystems but is non-standard. The existing typo in `ChatClientConfig` is a known debt; new code should use `classpath:`.
