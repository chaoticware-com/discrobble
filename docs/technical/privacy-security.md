# Privacy and Security

## Privacy Position

Discrobble's product promise is that it does not build a personal cloud history of what the user listens to on vinyl. The backend exists only to satisfy provider integration requirements that cannot be handled safely on-device.

## Data Inventory

| Data Type | Device Secure Storage | Device Local DB | Backend Memory | Backend Durable Storage |
| --- | --- | --- | --- | --- |
| Last.fm session key | Yes | No | Yes, transient during requests | No |
| Discogs OAuth token pair | Yes | No | Yes, transient during requests | No |
| Discogs collection cache | No | Yes | Yes, transient during proxy responses | No |
| Active session state | No | Yes | No | No |
| Play history | No | Yes | No | No |
| Scrobble queue | No | Yes | No | No |
| Raw microphone audio | No | No | No | No |
| Recognition summaries | No | Yes | No | No |

## Security Requirements

- Store provider tokens only in platform secure storage.
- Never commit provider keys or secrets to the client bundle.
- Encrypt auth handoff payloads to the device public key created for that auth attempt.
- Use HTTPS for every app-to-backend and backend-to-provider call outside local developer loopback flows.
- Allow loopback HTTP only in debug-only local development against a developer-run Worker on `localhost`, `127.0.0.1`, `::1`, or Android emulator host alias `10.0.2.2`; never allow that exception in CI, staging, or production builds.
- Enforce that exception in code:
  - Android debug hardcodes the local Worker URL, while Android release builds fail task execution unless `DISCROBBLE_WORKER_BASE_URL` or `discrobble.workerBaseUrl` resolves to a non-loopback absolute `https` URL.
  - iPhone debug falls back to `http://127.0.0.1:8787`, while non-Debug Xcode builds fail if `DISCROBBLE_WORKER_BASE_URL` is blank, loopback, or non-`https`.
  - CI, beta, and production workflows must inject a non-loopback `https` Worker base URL when they build native release artifacts.
- Redact provider tokens, barcode values, and raw payload bodies from backend logs.
- Disable analytics SDKs in MVP unless they are proven necessary and privacy-compatible.

## Permission Handling

### Microphone

- Request only when the user starts recognition or a guided session that needs it.
- Explain that microphone access is used to identify the playing song, not to upload or archive audio.
- If denied, keep manual track selection available.

### Camera

- Request only when the user chooses barcode scan.
- Explain that camera access is used to scan a record barcode for Discogs lookup.
- If denied, keep manual search available.

## Backend Handling Rules

- No backend database for user profile or listening history.
- No durable cache of Discogs restricted data on the backend.
- Short-lived cookies or signed state are allowed only to complete browser callback flows.
- Provider tokens may exist in memory for the duration of a request but must not be written to disk.

## Logging Rules

- Log provider error categories and request correlation IDs.
- Do not log:
  - Last.fm session keys
  - Discogs OAuth token secrets
  - raw auth callback query strings
  - raw scrobble payload bodies
  - raw audio or recognition provider payloads
- Session and queue diagnostics on-device should be user-readable and locally stored only.

## Discogs Compliance

- Treat collection and user data as restricted data.
- Show these public notices in the app where required:
  - `This application uses Discogs' API but is not affiliated with, sponsored or endorsed by Discogs. 'Discogs' is a trademark of Zink Media, LLC.`
  - `Data provided by Discogs.`
- Refresh collection data before showing cached Discogs content older than six hours.
- Do not expose Discogs restricted data through exports, public pages, or a Discrobble cloud service.

## Last.fm Compliance

- Follow official scrobble timing rules exactly.
- Do not retry `updateNowPlaying` automatically.
- Keep local retry only for recoverable scrobble failures.

## Retention Rules

- Device history remains until the user deletes the app or explicitly clears local data.
- Active sessions and queued scrobbles survive app restarts.
- Backend callback context expires within minutes and is not recoverable after expiry.
