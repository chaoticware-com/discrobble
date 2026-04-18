# MVP Scope

## In Scope

- Mandatory Last.fm and Discogs onboarding.
- Fresh on-device Discogs collection index with lightweight search and filtering.
- Barcode-based Discogs search as a second release entry path.
- Session-based playback model tied to a specific Discogs release.
- ShazamKit-based recognition on iPhone and Android.
- Manual confirmation for low-confidence matches.
- Manual side and disc overrides.
- Last.fm `updateNowPlaying`, scrobble timing enforcement, and ordered local retry queue.
- Local play history and active-session recovery.
- Explicit privacy and compliance messaging.

## Out of Scope

- Full background listening or passive ambient recognition.
- Smartwatch, CarPlay, Android Auto, or desktop clients.
- Social activity feeds, friend views, public profiles, or sharing.
- Deep Discogs collection management such as folder editing, notes, wantlist, or marketplace actions.
- SoundHound or any secondary recognition provider.
- Account-less usage, anonymous mode, or single-provider mode.
- Monetization, subscriptions, or ads.
- Cloud backup or cross-device sync.

## Acceptance Criteria

### F1: Dual Account Onboarding

- User cannot enter the home screen until both Last.fm and Discogs auth have succeeded.
- Deep-link auth completion restores the app cleanly after browser handoff.
- Token storage survives app restarts.

### F2: Discogs Collection Index

- User can search by artist, release title, and barcode-derived identifiers when present.
- Collection browsing is blocked behind refresh once cache age exceeds six hours.
- A session can be started from a confirmed release in the user's collection.

### F3: Barcode Lookup

- User can scan a barcode from within the app.
- Barcode matches either open a release directly or present a ranked list when ambiguous.
- Missing barcode coverage falls back to manual text search without trapping the user.

### F4: Guided Session Engine

- Session keeps explicit state for current disc, side, expected track, and session mode.
- User can skip forward, skip backward, and set the current side or disc manually.
- Session state survives app pause, relaunch, and transient network loss.

### F5: Recognition-Assisted Matching

- Strong matches advance automatically.
- Uncertain matches prompt for confirmation before a scrobble is committed.
- Recognition failure does not end the session or corrupt the queue.

### F6: Accurate Last.fm Submission

- `updateNowPlaying` is only sent when a current track is established.
- Scrobbles are only submitted when the track is longer than 30 seconds and has been played for at least half its duration or four minutes, whichever comes first.
- Recoverable failures remain in the queue and are retried in order.

### F7: Local-Only History and Recovery

- Tokens, queue state, and play history are stored only on-device.
- Backend logs and memory do not retain durable user session history.
- Users can inspect failed queue items and retry them.

## Later Phases

- Smarter release matching from audio plus library candidates.
- Better large-library browsing, favorites, and recently spun shelves.
- Background timer continuation with explicit limitations.
- Alternative recognition providers behind a shared abstraction.
- Import or export of local listening history.
- Optional session notes, cartridge or turntable presets, and richer record metadata views.

