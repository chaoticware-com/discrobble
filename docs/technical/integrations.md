# Integrations

General deploy and provenance policy is owned by [CI/CD and Provenance](ci-cd-and-provenance.md). This document owns provider-specific auth, data flow, fallback behavior, and compliance.

## Last.fm

### Purpose

- authenticate the user
- send `track.updateNowPlaying`
- send `track.scrobble`

### Auth Method

- Use browser-based auth with backend callback handling.
- Do not use plaintext password entry in-app, even though Last.fm documents a mobile-session flow that accepts username and password.
- The `Cloudflare Worker` start endpoint creates a signed auth context and redirects the user to Last.fm authorization.
- The callback endpoint exchanges the token for a session and deep-links the app with a short-lived encrypted payload.
- Last.fm API credentials are stored as protected deploy secrets and exposed to the Worker as runtime secrets only.

### Data Flow

1. App calls `/auth/lastfm/start`.
2. Browser opens Last.fm auth.
3. Backend callback exchanges for Last.fm session key.
4. App stores the session key in secure storage.
5. During playback, app calls `/lastfm/now-playing` and `/lastfm/scrobble`, sending the session key with each request.

### Operational Rules

- Production Last.fm credentials are rotated through protected GitHub workflows and stored in Cloudflare Worker secrets.
- Direct manual edits to production Last.fm secrets are out of policy.

### Fallback Behavior

- If auth fails, the app blocks session start and asks the user to retry linking.
- If `updateNowPlaying` fails, the app does not retry automatically.
- If `track.scrobble` fails with a recoverable service error or invalid session, the item stays in the local queue for retry after reauth if needed.

### Scrobble Rules

- Only scrobble tracks longer than 30 seconds.
- Only scrobble once playback has reached at least half the track duration or four minutes, whichever is earlier.
- Treat queued scrobbles as ordered and replay older items before new ones.

## Discogs

### Purpose

- identify the user
- fetch the user's collection
- search releases by text or barcode
- provide release-level tracklists and metadata

### Auth Method

- Use OAuth 1.0a via browser with backend callback handling.
- The app opens a browser-targetable `Cloudflare Worker` start URL so the Worker can obtain a request token, store the temporary request-token secret in an encrypted HTTP-only cookie, and redirect in the same browser context to Discogs authorization.
- The callback endpoint exchanges the authorized request token for an access token pair and deep-links the app with a short-lived encrypted payload.
- Discogs consumer credentials are stored as protected deploy secrets and exposed to the Worker as runtime secrets only.

### Data Flow

1. App calls `/auth/discogs/start`.
2. Worker stores the temporary request-token secret in a short-lived encrypted browser cookie and redirects to Discogs authorization.
3. Backend callback exchanges for Discogs access token and secret.
4. App stores them in secure storage.
5. App calls `/discogs/me`, `/discogs/collection`, and `/discogs/search` through the backend proxy with the stored token pair.

### Operational Rules

- Production Discogs credentials are rotated through protected GitHub workflows and stored in Cloudflare Worker secrets.
- Direct manual edits to production Discogs secrets are out of policy.

### Fallback Behavior

- If the collection sync fails mid-pagination, already synced pages remain local and the sync resumes from the failed page.
- If search fails, the user can still browse recent local releases when the cache is fresh.
- If barcode lookup returns no result, the app falls back to manual text search.

### Compliance Notes

- Discogs collection data is restricted data and must not be durably stored server-side.
- Display the required public-facing notice:
  - `This application uses Discogs' API but is not affiliated with, sponsored or endorsed by Discogs. 'Discogs' is a trademark of Zink Media, LLC.`
- Display the required nearby data notice:
  - `Data provided by Discogs.`
- Keep cached Discogs data fresh enough that the app refreshes before showing collection data older than six hours.

## ShazamKit

### Purpose

- recognize the currently playing song during an active listening session
- provide a candidate song identity that can be matched against the selected Discogs release

### Platform Strategy

- iPhone: use native ShazamKit integration through Swift bindings and `SHManagedSession` for the MVP spike shell.
- Android: use native ShazamKit Android integration through the vendor AAR and a Kotlin bridge.

### Data Flow

1. User starts listening in an active session.
2. Native iPhone shell requests microphone permission through `AVAudioApplication` and starts a foreground `SHManagedSession` capture.
3. ShazamKit returns `SHSession.Result`, where successful matches contain a ranked `mediaItems` array.
4. Shared session logic normalizes the result and maps it to the selected Discogs tracklist.
5. Session engine either auto-advances or asks the user to confirm.

### Current iPhone Spike Notes

- The iPhone shell currently uses a one-shot `SHManagedSession.result()` call to prove end-to-end candidate capture before the shared session engine exists.
- The observed ranked candidate shape exposes:
  - `title`
  - `subtitle`
  - `artist`
  - `shazamID`
  - `appleMusicID`
  - `artworkURL`
  - `webURL`
  - `genres`
  - `matchOffset`
  - `predictedCurrentMatchOffset`
  - `frequencySkew`
  - `confidence` on iOS `18.4+`

### Fallback Behavior

- Recognition unavailable or low confidence: user can choose the current track manually.
- Recognition mismatch across versions: Discogs release tracklist remains the scrobble source of truth.

### Constraints

- Requires microphone permission.
- No raw audio or recognition payloads are stored durably.
- Recognition is not sufficient on its own to select a release for MVP sessions; it supports a release-centric flow.

## Barcode Scanning

### Purpose

- provide a fast path to identify a vinyl release before starting a session

### Implementation

- Use native barcode scanning on iPhone and Android.
- Search Discogs by scanned UPC or EAN through `/discogs/search`.
- Normalize and rank results locally.

### Fallback Behavior

- If barcode data is absent or ambiguous, fall back to manual search or collection browse.
- If the scanned release is not in the user's library, the app can still let the user confirm and start a session from the searched release.

## Deferred Integrations

- SoundHound is explicitly out of MVP scope.
- A provider abstraction should exist in the shared core so another recognition provider can be added later without rewriting the session engine.

## References

- [Last.fm Authentication Overview](https://www.last.fm/api/authentication)
- [Last.fm Auth Spec](https://www.last.fm/api/authspec)
- [Last.fm Mobile Auth](https://www.last.fm/api/mobileauth)
- [Last.fm Scrobbling 2.0](https://www.last.fm/api/scrobbling)
- [Discogs API Terms of Use](https://support.discogs.com/hc/en-us/articles/360009334593-API-Terms-of-Use)
- [Discogs iOS Search and Barcode Guide](https://support.discogs.com/hc/en-us/articles/360014043894-How-To-Search-In-The-Database-When-Using-The-Discogs-iOS-App)
- [Discogs Android Search and Barcode Guide](https://support.discogs.com/hc/en-us/articles/360013820574-How-To-Search-In-The-Database-When-Using-The-Discogs-Android-App)
- [Discogs Barcode Guidelines](https://support.discogs.com/hc/en-us/articles/360005054893-Database-Guidelines-5-Barcodes-Identifiers)
- [Apple ShazamKit Overview](https://developer.apple.com/design/human-interface-guidelines/shazamkit)
- [ShazamKit Android](https://developer.apple.com/shazamkit/android/)
- [Apple ShazamKit Setup](https://developer.apple.com/help/account/services/shazamkit/)
