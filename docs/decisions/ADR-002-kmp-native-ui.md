# ADR-002: Kotlin Multiplatform Shared Core with Native UI

## Status

Accepted

## Context

The product must launch on iPhone and Android, but the highest-risk logic is shared: auth handoff parsing, Discogs modeling, session state, recognition matching, and scrobble queue rules. At the same time, deep links, microphone capture, camera flows, and OS lifecycle integration are platform-specific and user-visible.

## Decision

- Use Kotlin Multiplatform for domain, persistence, session engine, and integration logic.
- Build iPhone UI natively with SwiftUI.
- Build Android UI natively rather than forcing a shared Compose layer in MVP.
- Use native bridges for ShazamKit, barcode scanning, secure storage, and lifecycle control.

## Consequences

### Positive

- One source of truth for core product rules.
- Better platform fit for audio, camera, and auth UX.
- Lower risk than forcing a heavily shared UI before the product shape is proven.

### Negative

- Two UI layers must be maintained.
- Native bridge work is required for every platform service.
- Design parity must be managed intentionally rather than assumed from a shared UI toolkit.

