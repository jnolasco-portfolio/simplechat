---
description: "Use when: writing or modifying tests for the simplechat Spring Boot app. Trigger phrases: 'add a test', 'write a unit test', 'create a test class', 'mock the chat client', 'add a test for', 'test the controller'."
applyTo: "src/test/**,**/*Test*.java,**/*Tests.java"
---

# Testing in simplechat

Tests run against H2 in-memory under the `test` profile — **no Docker, no Testcontainers, no real Postgres/pgvector**. Anything that would require the production schema or extensions must be mocked, not exercised.

## Environment: what the `test` profile does

See [`src/test/resources/application-test.yaml`](src/test/resources/application-test.yaml).

| Setting | Value | Why |
| --- | --- | --- |
| `spring.datasource` | H2 `MODE=PostgreSQL;DB_CLOSE_DELAY=-1` | Max SQL compatibility with PG-flavored queries |
| `spring.liquibase.enabled` | `false` | The prod changelog uses `CREATE EXTENSION` and `vector(...)` which H2 can't parse |
| `spring.ai.vectorstore.pgvector.initialize-schema` | `false` | The `vector_store` table does not exist in H2 |
| `spring.ai.chat.memory.repository.jdbc.initialize-schema` | `never` | The `SPRING_AI_CHAT_MEMORY` table does not exist in H2 |
| `spring.main.allow-bean-definition-overriding` | `true` | Test `@TestConfiguration` beans can replace production beans of the same type |

## Mandatory mock list

`SimplechatApplicationTests` already mocks these via an inner `@TestConfiguration`. **New tests that load the full Spring context must mock the same beans or the context will fail:**

- `ChatClient` — the OpenAI client (no real call should ever be made)
- `ChatMemory` — the conversation memory store
- `IngestService` — currently an empty stub but still defined as a bean

To mock more beans (e.g. `VectorStore`), add them to a `@TestConfiguration` static class:

```java
@SpringBootTest
@ActiveProfiles("test")
class MyNewTest {

    @TestConfiguration
    static class Mocks {
        @Bean
        VectorStore vectorStore() { return mock(VectorStore.class); }
    }

    @Test
    void something() { /* ... */ }
}
```

## Things that are NOT safe in tests

- **Calling `chatClient.prompt()...stream().content()`** — the mock returns empty; tests that need streaming output must stub it explicitly.
- **Asserting against the `vector_store` table** — it does not exist. Mock `VectorStore` instead.
- **Asserting against `SPRING_AI_CHAT_MEMORY`** — same reason. Mock `ChatMemory` instead.
- **Running the production Liquibase changelog** — explicitly disabled in `application-test.yaml`. If you need a schema in tests, write a test-only migration or use `@Sql` on H2-compatible DDL.
- **Making real HTTP calls to `BASE_URL`** — tests use `http://localhost:11434/v1` with `test-key`; nothing is actually listening.

## Adding a new test

1. Place it under `src/test/java/cloud/nolasco/simplechat/...` mirroring the main package layout.
2. If the test needs Spring context (`@SpringBootTest`), always pair it with `@ActiveProfiles("test")`.
3. If the test needs the AI stack, mock every bean the controller depends on (`ChatClient`, `ChatMemory`, `VectorStore`).
4. Run with `./mvnw test` — must work without a Docker daemon.
5. For controller-slice tests prefer `@WebMvcTest(ChatController.class)` plus `@MockBean` for `ChatClient` / `ChatMemory` / `VectorStore`; this avoids loading the full context.

## Build command

```bash
./mvnw test
```

A failing test is never acceptable to commit — fix or delete it. Do not skip tests with `@Disabled` to make CI green.
