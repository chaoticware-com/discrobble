# ADR-003: Release-Centric, Assisted Session Flow

## Status

Accepted

## Context

Recognition alone is not reliable enough to identify the exact vinyl release, track order, side, or disc with the accuracy expected by the target user. Discogs already contains the release-specific metadata the product needs, while Last.fm requires structured artist and track metadata plus timing correctness.

## Decision

- Every listening session starts from a selected Discogs release, chosen from the user's collection or a confirmed search result.
- Recognition assists with current-track detection inside that chosen release.
- Strong matches may auto-progress.
- Low-confidence matches, side changes, and disc changes require user confirmation unless the user has enabled automatic side progression and confidence is high enough.
- The scrobble payload is built from Discogs metadata, not directly from recognition vendor metadata.

## Consequences

### Positive

- Cleaner and more consistent scrobble metadata.
- Better control over pressings, track order, and side changes.
- Reliable manual fallback when recognition is weak.

### Negative

- More user interaction than a fully automatic listener.
- Faster onboarding for non-Discogs users is sacrificed.
- The app depends on Discogs release quality and the user's willingness to confirm edge cases.

