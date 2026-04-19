# Discrobble

Discrobble is a privacy-first open-source mobile app for vinyl listeners who want to scrobble real records to Last.fm without typing every track by hand. The app uses a listener's Discogs library to identify the release, ShazamKit to help recognize the currently playing song, and a thin stateless backend only where third-party integrations require server-side signing or callback handling.

## Product Summary

- Audience: vinyl collectors who already use Last.fm and Discogs, and care more about scrobble accuracy than frictionless automation.
- Core value proposition: turn a physical vinyl listening session into an accurate, guided scrobbling flow with clean metadata and local retry handling.
- Privacy promise: no durable user data, library data, audio, or listening history is stored server-side.
- Platform strategy: Kotlin Multiplatform shared core with `SwiftUI` on iPhone and `Jetpack Compose` on Android.
- Repo and delivery strategy: public GitHub repository, `CodeRabbit`-reviewed pull requests, and `GitHub Actions`-only CI, deploys, and releases.

## MVP Snapshot

- Require both Last.fm and Discogs at onboarding.
- Sync a lightweight index of the user's Discogs collection on-device.
- Start a listening session by selecting a release from the library or by scanning a barcode and matching it in Discogs.
- Use ShazamKit to help match the currently playing track against the selected release's tracklist.
- Scrobble to Last.fm only when official timing thresholds are met.
- Keep pending scrobbles in a local ordered retry queue.
- Run as a foreground-first listening experience, not a fully autonomous background listener.

## Current Status

- Repository status: public GitHub repo created under `chaoticware-com`, with `main` protected and initial GitHub Actions checks added; docs-first bootstrap is still in progress.
- Product definition: MVP scope, user flows, architecture, API contracts, stack, repo layout, CI/CD, and ADRs are documented.
- Delivery planning status: a tactical implementation backlog now exists alongside the roadmap.
- Implementation status: no code or app scaffolding has been created yet.
- This README is intentionally brief; detailed behavior and policy live in the docs it links to.

## Documentation Index

### Product

- [PRD](docs/product/prd.md)
- [User Base](docs/product/user-base.md)
- [User Flows](docs/product/user-flows.md)
- [MVP Scope](docs/product/mvp-scope.md)

### Technical

- [Architecture](docs/technical/architecture.md)
- [CI/CD and Provenance](docs/technical/ci-cd-and-provenance.md)
- [Integrations](docs/technical/integrations.md)
- [API Contracts](docs/technical/api-contracts.md)
- [Data Model](docs/technical/data-model.md)
- [Privacy and Security](docs/technical/privacy-security.md)
- [Repo Layout](docs/technical/repo-layout.md)
- [Stack](docs/technical/stack.md)
- [Testing Strategy](docs/technical/testing-strategy.md)

### Delivery

- [Roadmap](docs/delivery/roadmap.md)
- [Implementation Backlog](docs/delivery/implementation-backlog.md)

### Decisions

- [ADR-001: No Server Data](docs/decisions/ADR-001-no-server-data.md)
- [ADR-002: KMP Shared Core with Native UI](docs/decisions/ADR-002-kmp-native-ui.md)
- [ADR-003: Assisted Session Flow](docs/decisions/ADR-003-assisted-session-flow.md)
- [ADR-004: Public GitHub Source of Truth](docs/decisions/ADR-004-public-github-source-of-truth.md)
- [ADR-005: GitHub Actions as Exclusive CI/CD](docs/decisions/ADR-005-github-actions-exclusive-ci-cd.md)
- [ADR-006: Cloudflare Workers and Hono Backend](docs/decisions/ADR-006-cloudflare-workers-hono-backend.md)
- [ADR-007: Jetpack Compose for Android UI](docs/decisions/ADR-007-jetpack-compose-android-ui.md)
- [ADR-008: Room and DataStore Persistence](docs/decisions/ADR-008-room-datastore-persistence.md)
- [ADR-009: CodeRabbit as Required PR Gate](docs/decisions/ADR-009-coderabbit-required-pr-gate.md)
- [ADR-010: Tagged Releases and Provenance](docs/decisions/ADR-010-tagged-releases-and-attestations.md)

### Open Source

- [Contributing](CONTRIBUTING.md)
- [Security](SECURITY.md)
- [License](LICENSE)

## Where Details Live

- Product behavior, scope, and feature traceability: [PRD](docs/product/prd.md), [User Flows](docs/product/user-flows.md), and [MVP Scope](docs/product/mvp-scope.md)
- Concrete implementation stack: [Stack](docs/technical/stack.md)
- System boundaries and data ownership: [Architecture](docs/technical/architecture.md)
- Repo structure and code ownership boundaries: [Repo Layout](docs/technical/repo-layout.md)
- Phase sequence and exit criteria: [Roadmap](docs/delivery/roadmap.md)
- Current implementation task order: [Implementation Backlog](docs/delivery/implementation-backlog.md)
- CI, deploys, releases, and public verification model: [CI/CD and Provenance](docs/technical/ci-cd-and-provenance.md)
- Decision rationale: ADRs in [docs/decisions](docs/decisions)
