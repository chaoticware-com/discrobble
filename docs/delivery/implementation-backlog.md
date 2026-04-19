# Implementation Backlog

This document owns the current actionable work order for Discrobble. Phase sequencing, milestone order, and phase exit criteria remain owned by [Roadmap](roadmap.md).

## Working Rules

- Keep backlog items small enough to fit in one pull request or one tightly related pull request set.
- Update this file when priorities change, when an item is completed, or when a planned item is dropped.
- Link to the owner doc for policy, architecture, stack, or API detail instead of restating it here.
- Do not start later-phase backlog items until the earlier phase gate has been satisfied.

## Current Status

- Documentation baseline, architecture, stack, governance rules, and ADRs are already in place.
- Phase 0 repo controls are now in place under `chaoticware-com`, including baseline repository security settings, protected `main`, in-repo review controls, initial GitHub Actions checks, preview/release workflow skeletons, configured GitHub environments, and release provenance scaffolding.
- The first Worker scaffold now exists under `backend/worker/`.
- The Gradle root, checked-in wrapper, shared KMP module tree, and native auth shells now exist.
- The Worker and both native shells now complete the Last.fm auth spike end to end, including signed auth start requests, browser handoff, encrypted callback payloads, and secure local session storage.
- The immediate focus is Phase 1 integration spikes.

## Immediate Next Slice

1. Implement the Last.fm `updateNowPlaying` and `track.scrobble` spike through the Worker.

## Phase 0 Backlog: Repo Controls and Transparency

- [x] B0.1 Create the public GitHub repository, push `main`, and enable baseline repository security settings.
  Owner docs: [CI/CD and Provenance](../technical/ci-cd-and-provenance.md), [Security](../../SECURITY.md)
- [x] B0.2 Protect `main` with pull-request-only merges, required status checks, and no direct pushes.
  Owner docs: [CI/CD and Provenance](../technical/ci-cd-and-provenance.md), [Contributing](../../CONTRIBUTING.md)
- [x] B0.3 Add `.coderabbit.yaml` and `.github/CODEOWNERS` so automated and human review policy becomes enforceable in-repo.
  Owner docs: [CI/CD and Provenance](../technical/ci-cd-and-provenance.md), [ADR-009](../decisions/ADR-009-coderabbit-required-pr-gate.md)
- [x] B0.4 Add `ci.yml` with `ci/docs` and `ci/workflows` as the first required checks.
  Owner docs: [Testing Strategy](../technical/testing-strategy.md), [CI/CD and Provenance](../technical/ci-cd-and-provenance.md)
- [x] B0.5 Add `preview-worker.yml` and `release.yml` skeletons with GitHub environments and tag-triggered production release flow.
  Owner docs: [CI/CD and Provenance](../technical/ci-cd-and-provenance.md), [ADR-010](../decisions/ADR-010-tagged-releases-and-attestations.md)
- [x] B0.6 Configure `preview` and `production` GitHub environments, secret boundaries, and required approvals.
  Owner docs: [CI/CD and Provenance](../technical/ci-cd-and-provenance.md), [Security](../../SECURITY.md)
- [x] B0.7 Add provenance validation for tagged Worker releases and document the public verification path from tag to deploy.
  Owner docs: [CI/CD and Provenance](../technical/ci-cd-and-provenance.md), [ADR-010](../decisions/ADR-010-tagged-releases-and-attestations.md)

### Phase 0 Gate

- `main` is protected and merge policy is enforceable.
- `CodeRabbit` and maintainer review are active on pull requests.
- Docs and workflow checks run in GitHub Actions.
- Preview and production environments are defined.
- Tagged release flow and provenance verification are implemented at least in skeleton form.

## Phase 1 Backlog: Integration Spikes

- [x] B1.1 Scaffold `backend/worker` with `TypeScript`, `Hono`, `Wrangler`, and a minimal health route.
  Owner docs: [Stack](../technical/stack.md), [Repo Layout](../technical/repo-layout.md), [ADR-006](../decisions/ADR-006-cloudflare-workers-hono-backend.md)
- [x] B1.2 Scaffold the KMP root and module layout for `shared`, `iosApp`, and `androidApp` without building product features yet.
  Owner docs: [Architecture](../technical/architecture.md), [Repo Layout](../technical/repo-layout.md), [ADR-002](../decisions/ADR-002-kmp-native-ui.md)
- [x] B1.3 Add the minimal iPhone shell needed for deep-link auth callback handoff and secure token storage integration.
  Owner docs: [Architecture](../technical/architecture.md), [Integrations](../technical/integrations.md)
- [x] B1.4 Add the minimal Android shell needed for deep-link auth callback handoff and secure token storage integration.
  Owner docs: [Architecture](../technical/architecture.md), [Integrations](../technical/integrations.md), [ADR-007](../decisions/ADR-007-jetpack-compose-android-ui.md)
- [x] B1.5 Implement the Last.fm auth start and callback spike through the Worker and both native shells.
  Owner docs: [Integrations](../technical/integrations.md), [API Contracts](../technical/api-contracts.md)
- [ ] B1.6 Implement the Last.fm `updateNowPlaying` and `track.scrobble` spike through the Worker.
  Owner docs: [Integrations](../technical/integrations.md), [API Contracts](../technical/api-contracts.md)
- [ ] B1.7 Implement the Discogs auth spike on both platforms.
  Owner docs: [Integrations](../technical/integrations.md), [API Contracts](../technical/api-contracts.md)
- [ ] B1.8 Implement Discogs collection fetch and barcode search spikes against real library data.
  Owner docs: [Integrations](../technical/integrations.md), [Testing Strategy](../technical/testing-strategy.md)
- [ ] B1.9 Implement the iPhone ShazamKit spike and confirm candidate result shape.
  Owner docs: [Integrations](../technical/integrations.md), [User Flows](../product/user-flows.md)
- [ ] B1.10 Implement the Android ShazamKit spike and confirm candidate result shape.
  Owner docs: [Integrations](../technical/integrations.md), [User Flows](../product/user-flows.md)
- [ ] B1.11 Capture real provider payloads, error shapes, and platform caveats from the spikes and fold them back into the owner docs.
  Owner docs: [API Contracts](../technical/api-contracts.md), [Data Model](../technical/data-model.md), [Testing Strategy](../technical/testing-strategy.md)

### Phase 1 Gate

- Last.fm auth and signed write calls work end to end.
- Discogs auth and collection reads work end to end.
- Barcode lookup is validated against real records.
- ShazamKit produces usable candidate data on both platforms.
- API contracts and testing docs reflect the real spike findings rather than assumptions.

## Phase 2 Ready Queue: MVP Foundation

- [ ] B2.1 Create the shared module implementations for auth, catalog, session, scrobble, persistence, network, and DI.
  Owner docs: [Architecture](../technical/architecture.md), [Stack](../technical/stack.md)
- [ ] B2.2 Add `Room KMP` schema, migrations, and `DataStore` setup for local state.
  Owner docs: [Data Model](../technical/data-model.md), [ADR-008](../decisions/ADR-008-room-datastore-persistence.md)
- [ ] B2.3 Add native secure storage adapters and shared token access interfaces.
  Owner docs: [Architecture](../technical/architecture.md), [Privacy and Security](../technical/privacy-security.md)
- [ ] B2.4 Implement onboarding for Last.fm and Discogs account connection.
  Owner docs: [User Flows](../product/user-flows.md), [PRD](../product/prd.md)
- [ ] B2.5 Implement collection sync, stale-cache enforcement, and release selection.
  Owner docs: [User Flows](../product/user-flows.md), [Architecture](../technical/architecture.md)
- [ ] B2.6 Implement the local scrobble queue model and deterministic replay behavior.
  Owner docs: [Data Model](../technical/data-model.md), [Testing Strategy](../technical/testing-strategy.md)

## Sequencing Rule

Treat B0.1 through B1.11 as the ordered implementation start. Do not begin the broader MVP foundation until the integration spike gate is met and the owner docs have been updated with the spike results.
