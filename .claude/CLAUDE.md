<!-- CODEGRAPH_START -->
## CodeGraph

In repositories indexed by CodeGraph (a `.codegraph/` directory exists at the repo root), reach for it BEFORE grep/find or reading files when you need to understand or locate code:

- **MCP tool** (when available): `codegraph_explore` answers most code questions in one call — the relevant symbols' verbatim source plus the call paths between them, including dynamic-dispatch hops grep can't follow. Name a file or symbol in the query to read its current line-numbered source. If it's listed but deferred, load it by name via tool search.
- **Shell** (always works): `codegraph explore "<symbol names or question>"` prints the same output.

If there is no `.codegraph/` directory, skip CodeGraph entirely — indexing is the user's decision.
<!-- CODEGRAPH_END -->

## Code navigation policy (project rule, overrides defaults)

This repo is indexed by CodeGraph and it is the **primary** way to read and navigate code here — for questions, bug hunts, *and* before every edit.

1. **First call is `codegraph_explore`.** For any task touching code in `app/` (Kotlin/Compose) or `backend/` (Python), start with `codegraph_explore` using the symbol names, file names, or a plain-language question. Treat the source it returns as already Read and `Edit` from it directly.
2. **Before changing a symbol, explore it** so its callers and blast radius are in view. After the change, re-query to check dependents rather than grepping.
3. **Grep/Glob/Read are fallbacks**, only for: what CodeGraph doesn't index (configs, `compose.yaml`, `.github/`, Markdown docs, Gradle/TOML, migrations/SQL), confirming a detail the explore output didn't cover, or a file changed less than about 1s ago (the index catches up through the watcher).
4. **Don't re-verify CodeGraph results with grep.** If a result looks stale or empty, re-query with the suggested names or more specific symbols first.
5. **No subagents for code lookup.** Don't spawn Explore/general-purpose agents to find or understand code; one or two `codegraph_explore` calls are cheaper. If a subagent is spawned for another reason, tell it in the prompt to use `codegraph_explore` (or `codegraph explore "<query>"` in the shell) before Grep/Read.
6. If the MCP tool is unavailable, use the shell: `codegraph explore "<symbols or question>"`.
