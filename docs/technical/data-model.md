# Data Model

## Storage Split

### Secure Storage

- `IntegrationTokenSet` for Last.fm
- `IntegrationTokenSet` for Discogs
- short-lived auth-attempt private key material

### Local Database

- Discogs release cache
- release search tokens and barcode index
- active listening session
- session tracks
- recognition history summaries
- scrobble queue
- completed local session history

## Shared Domain Types

## `IntegrationTokenSet`

| Field | Type | Notes |
| --- | --- | --- |
| `provider` | enum | `lastfm` or `discogs` |
| `username` | string | Provider username |
| `access_token` | string | Discogs OAuth token or Last.fm session key |
| `access_secret` | string nullable | Discogs token secret only |
| `issued_at` | instant | When auth completed |
| `expires_at` | instant nullable | Null for long-lived provider tokens |

## `DiscogsRelease`

| Field | Type | Notes |
| --- | --- | --- |
| `release_id` | long | Discogs release identifier |
| `instance_id` | long nullable | User collection instance when available |
| `title` | string | Release title |
| `artist` | string | Primary artist summary |
| `year` | int nullable | Release year |
| `formats` | string list | `Vinyl`, `LP`, `7"` and similar |
| `cover_image_url` | string nullable | Cover art |
| `barcode_values` | string list | UPC or EAN values |
| `tracklist` | `DiscogsTrack` list | Ordered track metadata |
| `source` | enum | `collection` or `search` |
| `fetched_at` | instant | Used for cache freshness enforcement |

## `DiscogsTrack`

| Field | Type | Notes |
| --- | --- | --- |
| `position` | string | `A1`, `B2`, `C1`, and similar |
| `disc_number` | int | Derived from position when possible |
| `side_label` | string nullable | `A`, `B`, `C`, `D` |
| `track_number_on_side` | int nullable | Derived from position |
| `title` | string | Track title |
| `duration_seconds` | int nullable | Parsed from Discogs duration when present |
| `artist_override` | string nullable | Optional per-track artist credit |
| `search_tokens` | string list | Local normalized match tokens |

## `BarcodeMatch`

| Field | Type | Notes |
| --- | --- | --- |
| `barcode` | string | Scanned code |
| `release_id` | long | Matched release |
| `match_reason` | enum | `barcode_exact`, `barcode_candidate`, `manual_confirmation` |
| `confidence` | double | Local ranking confidence |
| `in_user_collection` | boolean | Whether the release is already in the user's Discogs collection |

## `ListeningSession`

| Field | Type | Notes |
| --- | --- | --- |
| `session_id` | uuid | Local primary key |
| `release_id` | long | Selected Discogs release |
| `session_source` | enum | `collection_manual`, `barcode`, `search_manual` |
| `status` | enum | `idle`, `listening`, `paused`, `ended` |
| `started_at` | instant | Session start |
| `ended_at` | instant nullable | Session end |
| `current_disc_number` | int | Current disc |
| `current_side_label` | string nullable | Current side |
| `expected_track_index` | int | Index into ordered tracklist |
| `side_change_mode` | enum | `auto` or `confirm` |
| `recognition_mode` | enum | `assisted` |
| `last_confidence` | double nullable | Most recent recognition confidence |

## `SessionTrack`

| Field | Type | Notes |
| --- | --- | --- |
| `session_track_id` | uuid | Local primary key |
| `session_id` | uuid | Parent session |
| `release_id` | long | Release reference |
| `track_index` | int | Ordered track index |
| `position` | string | Discogs position |
| `title` | string | Track title |
| `status` | enum | `pending`, `now_playing`, `scrobble_queued`, `scrobbled`, `failed`, `skipped` |
| `matched_by` | enum | `recognition`, `manual`, `auto_advance` |
| `started_playing_at` | instant nullable | Local playback timer anchor |
| `eligible_for_scrobble_at` | instant nullable | Calculated from duration rules |
| `confirmed_at` | instant nullable | When user or system confirmed the match |

## `RecognitionMatch`

| Field | Type | Notes |
| --- | --- | --- |
| `match_id` | uuid | Local primary key |
| `session_id` | uuid | Parent session |
| `provider` | enum | `shazamkit` |
| `matched_at` | instant | Match timestamp |
| `candidate_title` | string | Provider-reported title |
| `candidate_artist` | string | Provider-reported artist |
| `normalized_track_index` | int nullable | Resolved Discogs track index |
| `confidence` | double | Local confidence score |
| `decision` | enum | `accepted_auto`, `accepted_manual`, `rejected`, `ignored` |

No raw audio is stored in this model.

## `ScrobbleCandidate`

| Field | Type | Notes |
| --- | --- | --- |
| `session_id` | uuid | Parent session |
| `track_index` | int | Target track |
| `artist` | string | Final scrobble artist |
| `track` | string | Final scrobble title |
| `album` | string | Final album |
| `album_artist` | string nullable | Final album artist |
| `started_at_unix` | long | UNIX timestamp for Last.fm |
| `duration_seconds` | int nullable | Track duration |
| `chosen_by_user` | boolean | True for vinyl session scrobbles in MVP |
| `eligible_at` | instant | When Last.fm rules permit submission |

## `QueuedScrobble`

| Field | Type | Notes |
| --- | --- | --- |
| `queue_id` | uuid | Local primary key |
| `session_id` | uuid | Parent session |
| `track_index` | int | Ordered track reference |
| `payload` | json | Frozen scrobble candidate payload |
| `state` | enum | `queued`, `sending`, `sent`, `retry_wait`, `terminal_failure` |
| `attempt_count` | int | Number of send attempts |
| `next_retry_at` | instant nullable | Exponential backoff target |
| `last_error_code` | string nullable | Normalized backend or provider error |
| `last_error_message` | string nullable | User-visible summary |
| `created_at` | instant | Queue insertion timestamp |
| `sent_at` | instant nullable | Success timestamp |

## Freshness Rules

- `DiscogsRelease.fetched_at` is authoritative for cache age.
- Collection views and new session starts must block when release data is older than six hours.
- History views may reference locally stored session summaries without querying Discogs again.

