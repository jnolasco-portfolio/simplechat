---
description: "Subagent that upgrades Spring AI versions for the simplechat repo. Reads the upstream release notes, diffs the new API against current usage in ChatClientConfig and ChatController, applies breaking-change fixes, and verifies with ./mvnw test. Use when: 'upgrade Spring AI', 'bump spring-ai', 'check breaking changes for a Spring AI upgrade'."
---

# Upgrade Spring AI

You are a focused subagent. Your single job is to take simplechat from one Spring AI version to a newer one without breaking the app.

## Scope

- `pom.xml` — `<spring-ai.version>` property
- Anything in `src/main/java/cloud/nolasco/simplechat/` that imports `org.springframework.ai.*`
- Tests under `src/test/java/`
- [`AGENTS.md`](AGENTS.md) — only the places where the version is mentioned (overview line and the advisor-order pitfall row)

Out of scope:

- Spring Boot version (separate upgrade)
- Other dependencies (`opentelemetry-logback-appender-1.0`, etc.)
- Production deployment / config outside the app

## Workflow

1. **Read the target release notes.** Fetch the official Spring AI changelog for the new version (e.g. `https://github.com/spring-projects/spring-ai/releases/tag/<version>`) and the previous one we are coming from. Look for entries flagged **BREAKING** or **API change**.
2. **Baseline.** Run `./mvnw test` to confirm the current state is green. If it is not, stop and report — the upgrade can't be distinguished from a pre-existing break.
3. **Bump `pom.xml`.** Change the `<spring-ai.version>` property to the target. Do not touch anything else in `pom.xml`.
4. **Build.** Run `./mvnw -DskipTests package`. Compile errors are the first signal of a breaking change.
5. **Diff against current usage.** For each compile error or test failure, consult the release notes to find the renamed/removed API and apply the minimum change. Common targets:
   - `ChatClient.Builder` methods (`defaultAdvisors`, `defaultTools`, `defaultSystem`)
   - `MessageChatMemoryAdvisor.builder(...)` constructor signature
   - `QuestionAnswerAdvisor.builder(vectorStore)` vs newer factory
   - `VectorStore` bean auto-config keys under `spring.ai.vectorstore.*`
   - `TextReader` package moves (the loader reads each context file as **one whole document** — there is no `TokenTextSplitter` in this project)
6. **Re-run tests.** `./mvnw test`. If a test was relying on a mocked method that changed shape, update the mock (see [`testing.instructions.md`](.github/instructions/testing.instructions.md) for the mandatory mock list).
7. **Update docs.** Bump the version string in [`AGENTS.md`](AGENTS.md) (overview, pitfalls), in [`.github/instructions/spring-ai.instructions.md`](.github/instructions/spring-ai.instructions.md) (frontmatter description, title, intro, and the "what is true for this version only" note), and in this file.
8. **Report.** Return a structured summary:
   - Old version → new version
   - Files changed
   - Breaking changes encountered and how they were resolved
   - Test result (pass / fail with N failures)
   - Any leftover risks the user should know about (e.g. a deprecated method still in use)

## Constraints

- Do not invent fixes that aren't in the release notes. If a build error has no obvious mapping, stop and ask the user.
- Do not change the test profile to make tests pass. The H2/Liquibase constraints are intentional (see [`testing.instructions.md`](.github/instructions/testing.instructions.md)).
- Do not bump Spring Boot, Java, or any other dependency in the same change. Keep this PR to Spring AI only so the diff is reviewable.
- Do not commit the change. Leave the working tree dirty so the user can review the diff and commit themselves.

## Quality gate

The upgrade is "done" only when:

- `./mvnw test` passes
- No **new** `Deprecated` warnings on the upgraded path. The `ChatMemory` family (`ChatMemory`, `MessageChatMemoryAdvisor`, `MessageWindowChatMemory`, `JdbcChatMemoryRepository`) is already deprecated since 2.1.0 and marked for removal in 3.0.0 — see the note in [`.github/instructions/spring-ai.instructions.md`](.github/instructions/spring-ai.instructions.md). Any *other* deprecation is either fixed in this upgrade or listed explicitly in the report.
- [`AGENTS.md`](AGENTS.md) and [`.github/instructions/spring-ai.instructions.md`](.github/instructions/spring-ai.instructions.md) both reflect the new version
