# Roadmap

This document owns phase sequencing and exit criteria. The current actionable task order lives in [Implementation Backlog](implementation-backlog.md).

## Phase 0: Public Repo Controls and Transparency

### Goals

- Establish the public operational contract before implementation-heavy work begins.

### Deliverables

- Public GitHub repository setup
- `MIT` license, contribution policy, and security policy
- branch protection on `main`
- required `CodeRabbit` and human review policy
- initial `GitHub Actions` workflow plan for CI, preview deploys, tagged releases, and provenance
- GitHub environments for `preview` and `production`
- Cloudflare deployment path defined through GitHub Actions only
- public provenance model for tagged production releases

### Exit Criteria

- The repository policies, review gates, deploy path, and release verification model are all documented and internally consistent.
- The project no longer has open ambiguity about where code is reviewed, how it is shipped, or how users verify production.

## Phase 1: Integration Spikes

### Goals

- Prove the high-risk third-party paths before building app structure around assumptions.

### Deliverables

- Last.fm browser auth callback completed on iPhone and Android.
- Last.fm `updateNowPlaying` and `track.scrobble` submitted through the proxy.
- Discogs OAuth completed on both platforms.
- Discogs collection fetch and barcode search validated with real data.
- ShazamKit returns candidate matches on both platforms.

### Exit Criteria

- No unresolved questions remain about auth viability, callback handoff, or provider request signing.
- The session engine can be implemented against real provider response shapes.

## Phase 2: MVP Foundation

### Goals

- Build the minimum product skeleton that can support guided sessions and local retries.

### Deliverables

- KMP shared modules for auth, catalog, session, scrobble, and persistence.
- Native app shells with secure storage, deep links, permissions, and basic navigation.
- Stateless backend proxy with documented endpoints.
- Local database schema for releases, sessions, recognition summaries, and queue items.

### Exit Criteria

- A user can onboard, sync a collection, pick a release, and hold local state across restarts.

## Phase 3: Session and Scrobbling

### Goals

- Turn the foundation into a usable vinyl scrobbler.

### Deliverables

- Session creation from collection search and barcode lookup.
- Recognition-assisted matching and manual correction flows.
- Side and disc transition handling.
- Last.fm now playing and scrobble queue execution.
- Local history and failure inspection views.

### Exit Criteria

- The happy path and the main recovery paths all work on both platforms.

## Phase 4: Hardening and Polish

### Goals

- Make the MVP trustworthy enough for external testing by real collectors.

### Deliverables

- Better large-library performance
- refresh and stale-cache UX
- queue observability and retry controls
- explicit privacy and Discogs attribution UI
- onboarding copy and permissions polish

### Exit Criteria

- External alpha users can complete a listening session and see accurate Last.fm results without developer intervention.

## Milestone Order

1. Public GitHub repo controls, branch protection, and CodeRabbit setup
2. GitHub Actions CI, preview deploy, and release provenance setup
3. Last.fm auth plus scrobble spike
4. Discogs auth plus collection spike
5. Barcode scan spike
6. ShazamKit spike
7. Shared data and session engine
8. Session UI and manual controls
9. Queue and retry hardening
10. External alpha

## Main Risks

- Cross-platform auth callback complexity
- Public-repo secret handling and safe preview deploy design
- Discogs cache freshness constraints limiting offline UX
- Recognition mismatches across pressings or alternate versions
- Android ShazamKit integration friction
- Session UX becoming too manual if confidence thresholds are poorly tuned

## Current Recommendation

Start with Phase 0 and use [Implementation Backlog](implementation-backlog.md) as the execution order for the first delivery slices. Do not scaffold the full app until both the public repo controls and the integration spikes confirm the delivery model, auth handoff, and recognition assumptions documented in this repo.
