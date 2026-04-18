# Product Requirements Document

## Product Statement

Discrobble helps vinyl collectors scrobble what they are playing on turntables to Last.fm without maintaining a personal backend. The app uses Discogs as the source of truth for release metadata, ShazamKit to assist with track recognition, and a stateless proxy only where provider integrations require callback handling or signed requests.

## Problem

Vinyl listeners cannot rely on digital playback hooks to scrobble to Last.fm. Existing scrobblers assume a digital player knows exactly what track is playing, while vinyl playback is physical, side-based, and often ambiguous across releases, discs, and pressings. Users who already maintain Discogs libraries want an easier way to keep their Last.fm history accurate without manually entering every track.

## Target Users

- Primary: vinyl collectors who actively use Last.fm and Discogs, maintain a reasonably accurate Discogs library, and want clean scrobbles.
- Secondary: vinyl listeners with smaller collections who still value guided scrobbling, barcode lookup, and manual correction.

Detailed personas and adoption assumptions live in [User Base](user-base.md).

## Goals

- Let users connect Last.fm and Discogs with minimal ongoing friction.
- Use Discogs metadata to ground release and track matching in real vinyl editions.
- Make the session flow accurate enough for power users while still being faster than manual entry.
- Keep user data local to the device except for short-lived integration callbacks and signed proxy requests.
- Ensure scrobble timing and retry behavior follow official Last.fm rules.

## Non-Goals

- Fully autonomous background listening or always-on microphone capture.
- Social features, sharing, following, or public profiles.
- A full Discogs replacement with deep collection management, selling, or wantlist workflows.
- Marketplace data, pricing tools, or commercial features in MVP.
- Multi-user backend accounts, web dashboards, or cloud sync.
- SoundHound or alternative recognition vendors in MVP.

## MVP Features

| ID | Feature | Description |
| --- | --- | --- |
| F1 | Dual account onboarding | User must link Last.fm and Discogs before starting a session. |
| F2 | Lightweight Discogs collection index | App syncs enough collection data to search, filter, and choose a release on-device. |
| F3 | Barcode-assisted release lookup | User can scan a barcode and use Discogs search results to identify the record. |
| F4 | Guided listening session | User starts a session for a chosen release and the app tracks side, disc, and track progression in the foreground. |
| F5 | Recognition-assisted matching | ShazamKit helps identify the current track and match it against the selected release's tracklist. |
| F6 | Accurate Last.fm submission | App sends `updateNowPlaying`, scrobbles when timing thresholds are met, and retries failed scrobbles in order. |
| F7 | Local-only history and recovery | App stores tokens, active sessions, play history, and queued scrobbles on-device only. |

## Success Criteria

- A user can complete onboarding and start a session without leaving the app in a broken auth state.
- A user can select a release from a medium-to-large Discogs library quickly enough that manual searching feels practical.
- Strong recognition matches progress without confirmation, while uncertain matches ask for correction before a wrong scrobble is sent.
- Scrobbles are only sent after Last.fm timing rules are satisfied.
- Failed scrobbles are retried locally in order and survive app restarts.
- No durable user library, token, audio, or history data is stored on the backend.

## Primary Risks

- Provider auth friction on mobile deep-link flows.
- Discogs restricted-data and caching requirements constraining offline UX.
- Barcode lookup failing for releases whose barcodes are incomplete or absent in Discogs.
- Recognition ambiguity for quiet passages, live recordings, alternate pressings, or side changes.
- Foreground-only behavior feeling too manual for some users.

## Feature Traceability

Every MVP feature maps to at least one user flow, technical section, and testing scenario.

| Feature | User Flows | Technical Sections | Test Scenarios |
| --- | --- | --- | --- |
| F1 | U1 | Architecture, Integrations, API Contracts | T1, T2 |
| F2 | U2, U3 | Integrations, Data Model | T3, T4 |
| F3 | U4 | Integrations, API Contracts | T5 |
| F4 | U3, U5, U6 | Architecture, Data Model | T6, T7 |
| F5 | U5, U6 | Integrations, Data Model | T6, T8 |
| F6 | U5, U7 | API Contracts, Data Model | T8, T9, T10 |
| F7 | U8 | Privacy and Security, Data Model | T11, T12 |

Flow IDs are defined in [User Flows](user-flows.md). Test IDs are defined in [Testing Strategy](../technical/testing-strategy.md).

