# API Contracts

## Contract Principles

- Backend is stateless with no durable user database.
- App sends provider credentials from secure local storage on each proxied request.
- Provider callback flows use signed state and encrypted payload handoff rather than server-side session storage.
- All errors return machine-readable codes plus a user-safe message.

## Authentication Flow Design

### Shared Pattern

1. App generates an ephemeral device key pair for the auth attempt.
2. App calls a `/auth/.../start` endpoint with:
   - `callback_url`: universal link or app link that returns to the app
   - `device_public_key`: PEM or compact JWK string
   - `platform`: `ios` or `android`
3. Backend creates a signed state payload and redirects the user to the provider.
4. Provider redirects to backend callback.
5. Backend exchanges provider credentials and redirects to `callback_url#payload=...`.
6. `payload` is encrypted for the device public key and contains the provider token set plus a short expiry.
7. App decrypts locally and stores the token set in secure storage.

This avoids durable backend storage while keeping provider secrets out of query parameters in plain text.

## `POST /auth/lastfm/start`

### Request

```json
{
  "callback_url": "discrobble://auth/lastfm",
  "device_public_key": "<public-key>",
  "platform": "ios"
}
```

### Response

```json
{
  "authorize_url": "https://www.last.fm/api/auth/?api_key=...&cb=https%3A%2F%2Fproxy.example.com%2Fauth%2Flastfm%2Fcallback%3Fstate%3D..."
}
```

### Error Cases

- `400 invalid_callback_url`
- `400 invalid_device_public_key`
- `500 auth_start_failed`

## `GET /auth/lastfm/callback`

### Provider Input

- Last.fm token in query string
- signed state embedded in callback URL

### Backend Behavior

- validate state signature and expiry
- exchange token for session using Last.fm API secret
- redirect to app callback URL with encrypted fragment payload

### Redirect Payload Shape

```json
{
  "provider": "lastfm",
  "username": "user123",
  "session_key": "lfm-session-key",
  "issued_at": "2026-04-18T20:00:00Z",
  "expires_at": "2026-04-18T20:01:00Z"
}
```

### Error Cases

- `400 invalid_state`
- `401 provider_denied`
- `502 provider_exchange_failed`

## `POST /auth/discogs/start`

### Request

```json
{
  "callback_url": "discrobble://auth/discogs",
  "device_public_key": "<public-key>",
  "platform": "android"
}
```

### Response

```json
{
  "authorize_url": "https://www.discogs.com/oauth/authorize?oauth_token=..."
}
```

### Backend Notes

- Backend stores the temporary Discogs request-token secret in an encrypted, HTTP-only cookie with a fifteen-minute TTL.
- No server-side database row is created for the auth flow.

### Error Cases

- `400 invalid_callback_url`
- `400 invalid_device_public_key`
- `502 request_token_failed`

## `GET /auth/discogs/callback`

### Provider Input

- `oauth_token`
- `oauth_verifier`
- encrypted auth context cookie

### Backend Behavior

- restore temporary request-token secret from cookie
- exchange for Discogs access token and secret
- redirect to app callback URL with encrypted fragment payload

### Redirect Payload Shape

```json
{
  "provider": "discogs",
  "username": "collector123",
  "oauth_token": "discogs-token",
  "oauth_token_secret": "discogs-secret",
  "issued_at": "2026-04-18T20:00:00Z",
  "expires_at": "2026-04-18T20:01:00Z"
}
```

### Error Cases

- `400 invalid_auth_context`
- `401 provider_denied`
- `502 access_token_failed`

## `GET /discogs/me`

### Request Headers

- `X-Discogs-OAuth-Token`
- `X-Discogs-OAuth-Secret`

### Response

```json
{
  "username": "collector123",
  "id": 12345,
  "resource_url": "https://api.discogs.com/users/collector123"
}
```

### Error Cases

- `401 missing_discogs_credentials`
- `401 discogs_auth_invalid`
- `429 discogs_rate_limited`
- `502 discogs_unavailable`

## `GET /discogs/collection`

### Query Parameters

- `page`: integer, required
- `per_page`: integer, default `50`, max `100`
- `folder_id`: integer, default `0`
- `query`: optional search text applied server-side when supported

### Request Headers

- `X-Discogs-OAuth-Token`
- `X-Discogs-OAuth-Secret`

### Response

```json
{
  "page": 1,
  "per_page": 50,
  "pages": 10,
  "items": [
    {
      "release_id": 98765,
      "instance_id": 45678,
      "title": "Example Album",
      "artist": "Example Artist",
      "year": 1984,
      "formats": ["Vinyl", "LP"],
      "cover_image": "https://...",
      "barcode_values": ["1234567890123"],
      "tracklist": [
        {
          "position": "A1",
          "title": "Track One",
          "duration": "3:45"
        }
      ],
      "fetched_at": "2026-04-18T20:00:00Z"
    }
  ]
}
```

### Error Cases

- `401 missing_discogs_credentials`
- `401 discogs_auth_invalid`
- `429 discogs_rate_limited`
- `502 discogs_unavailable`

## `GET /discogs/search`

### Query Parameters

- `query`: optional free text
- `barcode`: optional UPC or EAN
- `type`: fixed to `release` in MVP
- `per_page`: default `10`

At least one of `query` or `barcode` is required.

### Request Headers

- `X-Discogs-OAuth-Token`
- `X-Discogs-OAuth-Secret`

### Response

```json
{
  "results": [
    {
      "release_id": 98765,
      "title": "Example Album",
      "artist": "Example Artist",
      "year": 1984,
      "barcode_values": ["1234567890123"],
      "in_user_collection": true,
      "match_reason": "barcode"
    }
  ]
}
```

### Error Cases

- `400 missing_search_input`
- `401 missing_discogs_credentials`
- `429 discogs_rate_limited`
- `502 discogs_unavailable`

## `POST /lastfm/now-playing`

### Request Headers

- `X-Lastfm-Session-Key`

### Request Body

```json
{
  "artist": "Example Artist",
  "track": "Track One",
  "album": "Example Album",
  "album_artist": "Example Artist",
  "duration_seconds": 225,
  "track_number": 1,
  "release_id": 98765,
  "chosen_by_user": true
}
```

### Response

```json
{
  "status": "ok",
  "ignored": false
}
```

### Error Cases

- `400 invalid_payload`
- `401 missing_lastfm_session`
- `401 lastfm_session_invalid`
- `502 lastfm_unavailable`

Requests to this endpoint are not retried automatically by the client.

## `POST /lastfm/scrobble`

### Request Headers

- `X-Lastfm-Session-Key`

### Request Body

```json
{
  "artist": "Example Artist",
  "track": "Track One",
  "album": "Example Album",
  "album_artist": "Example Artist",
  "timestamp_started_at": 1776542400,
  "duration_seconds": 225,
  "track_number": 1,
  "release_id": 98765,
  "chosen_by_user": true
}
```

### Response

```json
{
  "status": "ok",
  "accepted": 1,
  "ignored": 0,
  "retryable": false
}
```

### Error Cases

- `400 invalid_payload`
- `401 missing_lastfm_session`
- `401 lastfm_session_invalid`
- `409 scrobble_not_yet_eligible`
- `502 lastfm_unavailable`

### Retry Classification

- retryable:
  - transport failure
  - service offline or temporarily unavailable
  - invalid session after the user reauthenticates
- terminal:
  - missing required metadata
  - malformed timestamp
  - filtered metadata that Last.fm accepts but ignores

## Auth State Rules

- Auth start state expires after fifteen minutes.
- App handoff payload expires after one minute.
- Expired payloads are discarded locally and require the user to restart auth.
- Backend must redact provider tokens from logs and traces.

