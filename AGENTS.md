# AGENTS.md

## Scope

These instructions apply to the entire repository.

## Project Priority

This project is documentation-driven.

- Markdown documentation is the primary source of truth for product, architecture, delivery, and decisions.
- Code, scaffolding, and implementation plans must follow the documented behavior unless the user explicitly requests a docs change as part of the work.
- If docs and code disagree, do not silently pick one. Update the docs and the implementation together so they converge in the same change.

## Mandatory Startup Read

At the start of every task, before planning, coding, or proposing architecture changes, load and read every Markdown file in this repository.

This currently includes:

- `AGENTS.md`
- `CONTRIBUTING.md`
- `README.md`
- `SECURITY.md`
- `docs/product/prd.md`
- `docs/product/user-base.md`
- `docs/product/user-flows.md`
- `docs/product/mvp-scope.md`
- `docs/technical/architecture.md`
- `docs/technical/ci-cd-and-provenance.md`
- `docs/technical/integrations.md`
- `docs/technical/api-contracts.md`
- `docs/technical/data-model.md`
- `docs/technical/privacy-security.md`
- `docs/technical/repo-layout.md`
- `docs/technical/stack.md`
- `docs/technical/testing-strategy.md`
- `docs/delivery/roadmap.md`
- `docs/decisions/ADR-001-no-server-data.md`
- `docs/decisions/ADR-002-kmp-native-ui.md`
- `docs/decisions/ADR-003-assisted-session-flow.md`
- `docs/decisions/ADR-004-public-github-source-of-truth.md`
- `docs/decisions/ADR-005-github-actions-exclusive-ci-cd.md`
- `docs/decisions/ADR-006-cloudflare-workers-hono-backend.md`
- `docs/decisions/ADR-007-jetpack-compose-android-ui.md`
- `docs/decisions/ADR-008-room-datastore-persistence.md`
- `docs/decisions/ADR-009-coderabbit-required-pr-gate.md`
- `docs/decisions/ADR-010-tagged-releases-and-attestations.md`

If new Markdown files are added later, they are also part of the mandatory startup read.

## Documentation Guardrails

- Keep docs updated as part of every meaningful change.
- Do not ship behavior changes, API changes, architectural changes, scope changes, or workflow changes without updating the affected Markdown files in the same task.
- Keep cross-links, terminology, feature IDs, flow IDs, and test IDs consistent across docs.
- Prefer updating existing docs over creating duplicate docs that partially overlap.
- If a change invalidates an ADR, replace it with a new ADR or explicitly supersede it.

## Documentation Ownership

- Keep each major topic owned by a primary doc file.
- Non-owner docs should link to the owner doc and only restate details when they are adding necessary local context.
- Do not copy full policy, stack, workflow, or architecture detail into multiple docs just to make each file self-contained.
- When a new doc becomes the better owner for a topic, move the detail there and prune the old copy.
- `README.md` should stay high-level and navigational.
- ADRs own decision rationale, not broad operational restatements.
- Product docs own product behavior and scope.
- Technical docs own implementation structure, interfaces, and delivery mechanics.
- Governance docs such as `CONTRIBUTING.md` and `SECURITY.md` own contributor and repo operation rules.

## Obsolete Documentation Policy

- Prune obsolete docs when they no longer describe the current product or implementation direction.
- Do not leave stale docs in place with the expectation that they will be cleaned up later.
- If a document is partially obsolete, rewrite or trim the obsolete sections in the same change.
- If a document is fully obsolete, delete it in the same change and remove or update all references to it.
- After pruning or renaming docs, update `README.md` and any internal doc links immediately.

## Required Documentation Checks

Before finishing any task that changes behavior, architecture, scope, or repo structure:

1. Re-scan all Markdown files.
2. Update any file made inaccurate by the change.
3. Remove or rewrite obsolete documentation.
4. Verify that `README.md` still points to the correct doc set.
5. Verify there are no stale references, abandoned phases, or outdated decisions left behind.

## Commit and Git Hygiene

- When a task contains multiple large or logically separate changes, split them into separate commits instead of bundling them into one.
- Keep each commit focused on one primary reason for change.
- Write commit messages around why the change happened and what problem or decision it addresses, not a file-by-file description of what changed.
- Before staging or committing, inspect the working tree for local-only files, generated artifacts, editor configs, machine-specific configs, temporary folders, environment files, and secrets.
- Actively maintain `.gitignore` so local development files, folders, configs, caches, and secrets do not enter git history.
- If a new local-only or sensitive file pattern appears during work, update `.gitignore` before committing related changes.
- Be especially careful with files such as `.env*`, `.dev.vars`, local database files, IDE folders, build outputs, temporary logs, credential files, and machine-specific config.
- Do not commit secrets, local overrides, or development-only artifacts even temporarily.

## Current Project Constraints

These are non-default assumptions and must be preserved unless the user explicitly changes them:

- Discrobble is a privacy-first mobile app for vinyl scrobbling.
- `Last.fm` and `Discogs` are required MVP integrations.
- `ShazamKit` is the MVP recognition provider.
- The product is foreground-first, not a full background listener.
- Barcode lookup is in MVP.
- `Kotlin Multiplatform` is for shared core logic only; iPhone and Android UI remain native.
- Android UI is specifically `Jetpack Compose`.
- Backend scope is a thin stateless proxy only, with no durable storage of user tokens, listening history, or Discogs library data.
- The backend runtime is `Cloudflare Workers` with `TypeScript` and `Hono`.
- Discogs metadata is the source of truth for release and track metadata used in scrobbling.
- The repository is public on GitHub from the start.
- `GitHub Actions` is the only allowed path for build, test, deploy, and release automation.
- `CodeRabbit` is a required pull request review gate alongside human review.
- Production releases are tag-based and must remain publicly traceable to commit, workflow run, deployment record, and provenance metadata.

## Documentation Style Expectations

- Keep documentation decision-complete and implementation-useful.
- Avoid placeholder text such as `TODO`, `TBD`, or vague future notes in MVP-critical docs.
- When changing scope or behavior, update product, technical, delivery, and decision docs if any of them are affected.
- Keep the docs concise, specific, and internally consistent rather than expansive and repetitive.
- Prefer a short summary plus a link to the owner doc over repeating the same detail in full.
