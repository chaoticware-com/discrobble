# Contributing

## Working Principles

- This repository is documentation-driven.
- Read `AGENTS.md` and the current Markdown docs before proposing architectural or behavioral changes.
- Keep docs and implementation aligned in the same pull request.
- Prefer small, reviewable pull requests over broad unscoped changes.

## Contribution Flow

1. Branch from `main`.
2. Make your change in a feature branch.
3. Update all affected docs in the same branch.
4. Open a pull request against `main`.
5. Wait for `GitHub Actions`, `CodeRabbit`, and human review before merge.

Direct pushes to `main` are out of policy.

## Pull Request Requirements

Every pull request must:

- pass required `GitHub Actions` checks
- receive a passing `CodeRabbit` review status
- receive at least one human maintainer approval
- update any stale documentation caused by the change
- avoid unresolved `TODO` or `TBD` placeholders in MVP-critical docs

## Documentation Rules

- Markdown docs are the source of truth for product, architecture, delivery, and policy.
- If you change behavior, architecture, CI/CD, release flow, or repo structure, update the affected docs in the same pull request.
- Remove obsolete docs or obsolete sections instead of leaving them behind.
- Keep cross-links, terms, feature IDs, and test IDs consistent.
- Let the most direct doc own the full detail; other docs should link rather than restate it.

## High-Scrutiny Changes

These areas require extra review care:

- `.github/workflows/*`
- auth and secret-handling code
- Last.fm signing and Discogs proxy logic
- session engine and scrobble queue logic
- documentation that changes public guarantees or production policy

## Policy References

- Repo-wide documentation rules and current project constraints live in `AGENTS.md`.
- CI, deployment, release, and provenance policy live in `docs/technical/ci-cd-and-provenance.md`.
- Secret handling and vulnerability reporting live in `SECURITY.md`.

## Licensing

By contributing to this repository, you agree that your contributions are licensed under the repository's `MIT` license.
