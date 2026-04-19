# User Flows

## Flow Index

- U1: Onboarding and account linking
- U2: Initial library sync
- U3: Manual release selection and session start
- U4: Barcode-based release lookup and session start
- U5: Recognition-assisted track progression
- U6: Low-confidence correction and side or disc changes
- U7: Scrobble queue and retry handling
- U8: Session end and local history review

## U1: Onboarding and Account Linking

### Preconditions

- App is freshly installed.
- User has both Last.fm and Discogs accounts.

### Main Path

1. App introduces the product, privacy promise, and required integrations.
2. User taps `Connect Last.fm`.
3. App opens the provider auth flow in the browser via backend `/auth/lastfm/start`.
4. Backend handles callback and deep-links the app with a short-lived encrypted payload containing Last.fm token data.
5. App stores Last.fm session data in secure storage.
6. User taps `Connect Discogs`.
7. App opens browser auth via backend `/auth/discogs/start`.
8. Backend completes OAuth callback and deep-links the app with encrypted Discogs token data.
9. App stores Discogs token data in secure storage.
10. App requests microphone and camera permissions with context-specific explanations.
11. On success, app proceeds to collection sync.

### Edge Cases

- User cancels either auth flow: onboarding remains blocked until both providers are linked.
- Deep link expires: app prompts the user to restart provider auth.
- Permissions denied: app still allows manual release search, but recognition or barcode entry remain disabled until the user grants permissions in settings.

## U2: Initial Library Sync

### Preconditions

- Both integrations are connected.

### Main Path

1. App calls `/discogs/me` to resolve the Discogs username and identity.
2. App calls `/discogs/collection` page by page and builds a lightweight local index.
3. App stores release summaries, identifiers, tracklists, cache timestamps, and search tokens locally.
4. App marks the collection cache as fresh for up to six hours.
5. User lands on the home screen with search, recent releases, and session entry points.

### Edge Cases

- Discogs rate limit or transient failure: app resumes pagination on retry without discarding already synced pages.
- Cache becomes older than six hours: app requires a refresh before full collection browsing or starting a new session from stale collection data.

## U3: Manual Release Selection and Session Start

### Preconditions

- Collection cache is fresh.

### Main Path

1. User searches or filters the Discogs collection.
2. User opens a release and reviews artwork, tracklist, disc count, and side breakdown.
3. User taps `Start Session`.
4. User chooses side progression mode:
   - auto-advance when confidence is high
   - ask before side or disc changes
5. App creates a local `ListeningSession`, sets the first expected track, and sends `updateNowPlaying` only after a track is identified.

### Edge Cases

- User cannot find the record in the synced collection: app offers barcode scan or text search against Discogs search.
- Release data is incomplete: app still allows a session if track order is known well enough to scrobble, otherwise the release cannot be used for MVP sessions.

## U4: Barcode-Based Release Lookup and Session Start

### Preconditions

- Camera permission is available.

### Main Path

1. User taps `Scan Barcode`.
2. Native barcode scanner reads the UPC or EAN.
3. App calls `/discogs/search?barcode=...`.
4. If there is a clear release match, app opens that release directly.
5. If there are multiple plausible matches, app shows a ranked selection list.
6. User confirms the correct release and starts a session.

### Edge Cases

- Barcode is not found in Discogs: app falls back to text search and manual collection browse.
- Barcode maps to a release outside the user's collection: app shows the result, labels it as outside the library, and lets the user decide whether MVP should permit a session from search results. For MVP, session start is allowed if the user confirms the release manually.

## U5: Recognition-Assisted Track Progression

### Preconditions

- An active session exists for a specific release.
- Microphone permission is available.

### Main Path

1. User taps `Listen`.
2. App starts a foreground recognition loop using native ShazamKit bindings.
3. Recognition returns ranked song candidates with core fields such as title, artist, and timing offsets, and the app normalizes them against tracks on the selected Discogs release.
4. If a strong match aligns with the expected track or a plausible next track:
   - app updates the current track
   - app sends `updateNowPlaying` to Last.fm
   - app starts or updates the track timer
5. Once Last.fm timing thresholds are satisfied, the app creates a `QueuedScrobble` and attempts immediate submission.
6. If submission succeeds, app advances session state and waits for the next track.

### Edge Cases

- Recognition fails temporarily: session remains active and the user can retry listening or pick the track manually.
- A track is too short to scrobble under Last.fm rules: app may show it as played locally but does not scrobble it.

## U6: Low-Confidence Correction and Side or Disc Changes

### Main Path

1. Recognition result conflicts with the expected track order or returns low confidence.
2. App presents the top candidate track matches, including title, artist, and timing-offset context, alongside the current expected track.
3. User confirms the correct track, skips forward, skips backward, or switches side or disc manually.
4. App updates the session timeline and only scrobbles confirmed tracks.

### Side and Disc Rules

- Strong match on the first track of the next side can trigger an auto-advance if side changes are set to automatic.
- If confidence is not strong enough, the app asks the user to confirm the side or disc transition.
- Manual side or disc override resets the expected track pointer and future recognition matching window.

## U7: Scrobble Queue and Retry Handling

### Main Path

1. Each scrobble-eligible track produces a local `QueuedScrobble`.
2. App attempts to send queued scrobbles immediately, in FIFO order.
3. Recoverable Last.fm failures move the item back to the queue with exponential backoff.
4. Non-recoverable validation failures mark the item as failed and expose the reason in session history.
5. On app restart, queued items are restored and replayed before new scrobbles are submitted.

### Edge Cases

- Device offline: queue remains local until connectivity returns.
- Session ends before a scrobble was submitted: queue survives and resumes on next launch.

## U8: Session End and Local History Review

### Main Path

1. User taps `End Session` or the app infers no more tracks are pending and the user confirms.
2. App stores the completed session summary locally.
3. User can inspect:
   - release played
   - track order
   - matched versus manually corrected tracks
   - queued, sent, or failed scrobbles
4. User can retry failed scrobbles manually if the failure is recoverable.

### Data Outcome

- Local history remains on-device only.
- No session summary is uploaded to a Discrobble backend.
