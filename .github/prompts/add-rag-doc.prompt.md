---
description: "Add a new RAG source document to the simplechat vector store. Drops a markdown file into src/main/resources/context/, wires it into ReferenceDocsLoader, and verifies the loader sees it on next startup."
---

# Add a RAG document

Wire a new markdown source into [`ReferenceDocsLoader`](src/main/java/cloud/nolasco/simplechat/service/ReferenceDocsLoader.java) so it gets chunked, embedded, and pushed into the `vector_store` table on the next startup.

## When to use

- A new brochure, FAQ, or spec sheet needs to be queryable through the chat
- The user has placed a `.md` file (or convertible one) somewhere in the repo and wants it indexed

## Inputs

Ask the user for:

1. **Source path** — where the `.md` lives. If it is not under `src/main/resources/context/` already, copy it there first.
2. **Field name** — short Java identifier for the `@Value` injection, in camelCase. The existing convention is: `developerProfile`, `projectOverview`, `projectBrochure`, `projectInventory`.
3. **Filename metadata** — already auto-populated as `filename` in `TextReader`; do not set it manually.

## Steps

1. Move/copy the markdown file under `src/main/resources/context/`. If the file lives in a sub-folder, keep the sub-folder — the `classpath:/context/...` pattern is recursive.
2. Open [`ReferenceDocsLoader.java`](src/main/java/cloud/nolasco/simplechat/service/ReferenceDocsLoader.java) and add:

   ```java
   @Value("classpath:/context/<relative-path>")
   private Resource <fieldName>;
   ```

   near the other `@Value` injections (alphabetical is not required, group by topic).

3. In `init()`, add a `safeLoad(<fieldName>);` line next to the other `safeLoad(...)` calls. **Do not call `loadData` directly** — `safeLoad` is the version that catches per-doc failures without breaking the rest of startup.
4. Re-run `./mvnw test` to make sure the loader bean still wires.
5. If the project is running locally, restart it and tail the logs for `Loading context documents into Vector Store` followed by no `Skipping reference document` warnings.

## Pitfalls

- **Newline endings / encoding** — `TextReader` reads bytes. If the file is UTF-8 with BOM, strip the BOM; otherwise the first chunk starts with `\ufeff` and degrades embedding quality.
- **Oversize files** — `TokenTextSplitter.withChunkSize(128)` produces many chunks. A 100-page brochure is fine; a 1000-page dump will bloat the `vector_store` table and slow `/stream/.../chat`. Consider splitting upstream.
- **Tables and code blocks** — markdown tables are tokenized naively; if the source has many short rows, consider a custom splitter or a pre-processed CSV. Out of scope for this prompt.
- **Filename collisions** — `filename` is stored in the document metadata. Two docs with the same name will collide in the metadata even if their paths differ. Rename before adding.

## After the change

- Commit the new markdown file and the `ReferenceDocsLoader.java` change together
- Mention the added file in the PR description so reviewers know what knowledge the chat gained
