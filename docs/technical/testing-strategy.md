# Testing Strategy

## Goals

- Verify every MVP feature through at least one user flow and one concrete test scenario.
- Catch integration failures early before any UI polish work depends on them.
- Validate privacy constraints and recovery behavior, not just happy paths.
- Validate the public GitHub review, CI, release, and provenance model before first production use.

## Scenario Matrix

| Test ID | Feature | Flow | Scenario | Expected Result |
| --- | --- | --- | --- | --- |
| T1 | F1 | U1 | Complete Last.fm auth from browser callback into app | Session key stored securely and onboarding proceeds |
| T2 | F1 | U1 | Complete Discogs auth, then relaunch app | Token pair survives restart and onboarding remains complete |
| T3 | F2 | U2 | Sync first page of collection and resume after partial failure | Local index resumes without losing completed pages |
| T4 | F2 | U3 | Search collection by artist and title, then start a session | Release opens and session is created correctly |
| T5 | F3 | U4 | Scan barcode with exact match, ambiguous match, and no match | App opens direct result, ranked list, or manual fallback |
| T6 | F4, F5 | U5 | Strong recognition match on expected track | Track auto-advances, now playing updates, timer starts |
| T7 | F4 | U6 | Manual side or disc override mid-session | Session pointer updates and future matching uses new anchor |
| T8 | F5, F6 | U5, U6 | Low-confidence or conflicting match | User confirmation required before scrobble timeline changes |
| T9 | F6 | U7 | Recoverable Last.fm scrobble failure while offline | Queue persists locally and retries in FIFO order later |
| T10 | F6 | U7 | Terminal scrobble error from malformed metadata | Queue item moves to terminal failure with visible reason |
| T11 | F7 | U8 | Review local history after session end | History is visible on-device with no backend dependency |
| T12 | F7 | U1, U8 | Inspect backend behavior during normal use | No durable user data is written server-side |
| T13 | Repo policy | N/A | Open a pull request that lacks required checks or CodeRabbit status | Merge remains blocked by branch protection policy |
| T14 | Workflow policy | N/A | Change a workflow file with invalid syntax or unsafe structure | Workflow lint fails before merge |
| T15 | Release policy | N/A | Create a version tag and run the production release workflow | Release artifacts, deployment record, and production deploy are all linked to the tag and workflow run |
| T16 | Provenance policy | N/A | Inspect a production release from the public repository | Commit SHA, tag, workflow run, deployment metadata, and attestation are all visible and consistent |
| T17 | F4 | U5 | iPhone one-shot ShazamKit spike returns ranked media-item metadata | Title, artist, IDs, offsets, skew, and optional confidence are visible in the shell |
| T18 | F4 | U5 | Android build without local ShazamKit AAR or developer token | App still assembles and the shell surfaces an actionable unavailable state |
| T19 | F4 | U5 | Android one-shot ShazamKit spike with local AAR and developer token | `MatchResult.Match.matchedMediaItems` fields are surfaced in the shell |

## Test Layers

### Shared Core Unit Tests

- track matching and normalization
- scrobble eligibility calculation
- queue ordering and backoff policy
- cache freshness enforcement
- side and disc progression rules

### Backend Contract Tests

- auth start response shape
- encrypted auth fragment envelope shape
- callback state validation
- Last.fm request signing
- Discogs proxy header validation
- normalized error mapping

### Worker Runtime Tests

- Hono route coverage
- Cloudflare Worker bindings and secret access
- preview versus production environment handling
- deploy bundle smoke validation before release

### Native Platform Tests

- deep-link handoff into the app
- secure storage read and write
- microphone permission denial and recovery
- camera permission denial and recovery
- barcode scanner invocation
- iPhone `SHManagedSession` one-shot bridge smoke test
- Android `Session.match(signature)` smoke test with local Apple AAR and developer token
- Android fallback path when the local Apple AAR or developer token is absent

### Workflow and Governance Checks

- `actionlint` for workflow correctness
- docs consistency checks for doc-driven changes
- CodeRabbit-required PR path validation
- release provenance and attestation validation

## GitHub Actions Lanes

Phase 0 wires up `ci/docs` and `ci/workflows` first. The remaining lanes come online as the corresponding mobile and backend scaffolding lands.

- `ci/docs`
  - markdown link checks, stale-doc checks, and required-doc coverage
- `ci/shared`
  - shared Kotlin compile, unit tests, and persistence tests
- `ci/android`
  - Android build, Compose UI tests, and Android-specific integration checks that do not require the local Apple ShazamKit AAR
- `ci/ios`
  - iOS build and smoke tests for native bridges and deep links
- `ci/worker`
  - Worker lint, unit tests, Hono route tests, and Wrangler config validation
- `ci/workflows`
  - workflow linting and policy checks
- `release/provenance`
  - tag-based artifact generation, artifact attestation, and deployment metadata verification

## Platform Coverage

- iPhone on a modern iOS version with ShazamKit enabled
- Android device with the local Apple ShazamKit AAR and developer token configured
- public GitHub Actions runners for shared, worker, and workflow validation
- macOS GitHub Actions runners for iOS builds
- both platforms tested for:
  - fresh install onboarding
  - auth cancellation
  - app restart during queued retries
  - stale Discogs cache refresh blocking

## Current Spike Caveats

- The repo now compiles and assembles Android without committing the Apple ShazamKit AAR, so CI can validate the fallback shell and the build plumbing but not real Android recognition.
- Real Android recognition validation remains a maintainer-local smoke test until the project adopts a compliant way to provision the Apple SDK and developer token in automation.
- iPhone ShazamKit smoke validation can run in the repo without extra vendor binaries, but confidence values remain version-dependent because they appear only on iOS `18.4+`.

## Failure-Path Checklist

- Last.fm auth denied
- Discogs auth denied
- auth payload expired before app resume
- Discogs rate limit returned during sync
- barcode not found
- recognition unavailable
- low-confidence recognition mismatch
- Last.fm invalid session during queue replay
- network loss during active session
- app terminated with queued scrobbles pending
- workflow change breaks required checks
- release tag missing provenance metadata
- attempted production deploy outside GitHub Actions policy

## Exit Criteria Before Coding Beyond Spikes

- Auth flows proven on both platforms.
- Discogs collection sync contract validated against real data.
- Barcode flow validated end-to-end on at least one real record.
- Recognition can produce a usable candidate that maps onto a selected Discogs tracklist.
- Queue replay behavior is deterministic under offline and transient Last.fm failures.
- The required GitHub Actions lanes are documented and runnable.
- Tagged releases can be traced through workflow run, deployment record, and provenance metadata.
