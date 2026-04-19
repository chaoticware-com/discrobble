# API Contracts

## Contract Principles

- Backend is stateless with no durable user database.
- App sends provider credentials from secure local storage on each proxied request.
- Provider callback flows use signed state and encrypted payload handoff rather than server-side session storage.
- All errors return machine-readable codes plus a user-safe message.

## Authentication Flow Design

### Shared Pattern

1. App generates an ephemeral device key pair for the auth attempt.
2. App passes `callback_url`, `device_public_key`, and `platform` to a `/auth/.../start` endpoint.
3. Backend creates a signed state payload and starts the provider auth flow.
4. Provider redirects to backend callback.
5. Backend exchanges provider credentials and redirects to `callback_url#payload=...`.
6. `payload` is encrypted for the device public key and contains the provider token set plus a short expiry.
7. App decrypts locally and stores the token set in secure storage.

This avoids durable backend storage while keeping provider secrets out of query parameters in plain text.

If the backend has already validated the signed state and trusted the `callback_url`, callback failures should redirect back to the app with `#error_code=...&error_message=...` so the native shell can recover without leaving the user stranded in the browser.

### Encrypted Fragment Envelope Shape

The `payload` fragment value is a UTF-8 JSON envelope that the native shell decrypts locally with the pending auth-attempt private key:

```json
{
  "alg": "ECDH-P256+HKDF-SHA256+A256GCM",
  "epk": "-----BEGIN PUBLIC KEY-----\n...\n-----END PUBLIC KEY-----",
  "salt": "<base64url>",
  "iv": "<base64url>",
  "ciphertext": "<base64url>"
}
```

The envelope algorithm and HKDF info string are fixed for the current spikes, and the decrypted payload then matches the provider-specific handoff shapes documented below.

### Standard JSON Error Envelope

Worker JSON failures currently normalize to:

```json
{
  "error": {
    "code": "invalid_payload",
    "message": "User-safe explanation"
  }
}
```

All JSON responses from the current Worker routes also return `Cache-Control: no-store`.

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
- `400 invalid_platform`
- `400 auth_start_failed`

## `GET /auth/lastfm/callback`

### Provider Input

- Last.fm token in query string
- signed state embedded in callback URL

### Backend Behavior

- validate state signature and expiry
- exchange token for session using Last.fm API secret
- redirect to app callback URL with encrypted fragment payload on success
- redirect to app callback URL with `error_code` and `error_message` fragments when the state is valid but the provider exchange fails

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

`expires_at` above refers to the short-lived handoff payload expiry, not the Last.fm session key lifetime.

### Redirect Error Fragment Shape

```text
discrobble://auth/lastfm#error_code=provider_exchange_failed&error_message=Discrobble%20could%20not%20exchange%20the%20Last.fm%20auth%20token%20for%20a%20session.
```

### Error Cases

- `400 invalid_state` when the Worker cannot trust the callback state enough to redirect safely
- `401 provider_denied` redirected to the app as `error_code=provider_denied` once state is valid
- `502 provider_exchange_failed` redirected to the app as `error_code=provider_exchange_failed` once state is valid

## `GET /auth/discogs/start`

### Query Parameters

- `callback_url`
- `device_public_key`
- `platform`

Example:

```text
/auth/discogs/start?callback_url=discrobble%3A%2F%2Fauth%2Fdiscogs&device_public_key=...&platform=android
```

`platform` currently accepts only `ios` or `android`.

### Backend Notes

- Endpoint must be opened in the browser, not fetched first as JSON, so the short-lived encrypted request-token cookie is set in the same browser context that returns on callback.
- Backend stores the temporary Discogs request-token secret in an encrypted, HTTP-only cookie with a fifteen-minute TTL.
- No server-side database row is created for the auth flow.

### Response

- `302` redirect to `https://www.discogs.com/oauth/authorize?oauth_token=...`
- `Set-Cookie` with the encrypted temporary request-token secret

### Error Cases

- `400 invalid_callback_url`
- `400 invalid_device_public_key`
- `400 invalid_platform`
- `502 request_token_failed`

## `GET /auth/discogs/callback`

### Provider Input

- `oauth_token`
- `oauth_verifier`
- signed state in query string
- encrypted auth context cookie

### Backend Behavior

- validate signed state and expiry
- restore temporary request-token secret from cookie
- exchange for Discogs access token and secret
- redirect to app callback URL with encrypted fragment payload on success
- redirect to app callback URL with `error_code` and `error_message` fragments when the state is valid but the browser auth context or access-token exchange fails

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

- `400 invalid_state` when the Worker cannot trust the callback state enough to redirect safely
- `400 invalid_auth_context` redirected to the app as `error_code=invalid_auth_context` once state is valid
- `401 provider_denied` redirected to the app as `error_code=provider_denied` once state is valid
- `502 access_token_failed` redirected to the app as `error_code=access_token_failed` once state is valid

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

Current normalization details from the spike:

- `artist` is the joined `basic_information.artists[].name` string.
- `formats` merges both the Discogs format `name` values and each `descriptions[]` value.
- `tracklist` comes from a follow-up `/releases/{id}` fetch per item, and each track currently keeps only `position`, `title`, and nullable `duration`.
- `cover_image`, `instance_id`, `year`, and `duration` are nullable when Discogs omits them.

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

`match_reason` is `barcode` when the request was barcode-driven and `query` when the request used free-text search input.

Current normalization details from the spike:

- Discogs `title` values shaped like `Artist - Release` are split locally into `artist` and `title`.
- Only Discogs results with `type=release` and a numeric `id` survive normalization.
- `barcode_values` is copied from the provider result array as-is, with no extra local parsing.

### Error Cases

- `400 missing_search_input`
- `401 missing_discogs_credentials`
- `401 discogs_auth_invalid`
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

`ignored` is currently derived from Last.fm's `<ignoredmessage code="...">` value and becomes `true` when that code is non-zero.

### Error Cases

- `400 invalid_payload`
- `401 missing_lastfm_session`
- `401 lastfm_session_invalid`
- `502 lastfm_unavailable`

Requests to this endpoint are not retried automatically by the client.

Current provider error mapping:

- Last.fm codes `4` and `9` normalize to `lastfm_session_invalid`.
- Last.fm codes `2`, `3`, `8`, `10`, and `11` normalize to `lastfm_unavailable`.
- Other structured Last.fm errors normalize to `invalid_payload`.

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

Current provider error mapping for successful HTTP responses matches `POST /lastfm/now-playing`.

## Auth State Rules

- Auth start state expires after fifteen minutes.
- App handoff payload expires after one minute.
- Expired payloads are discarded locally and require the user to restart auth.
- Backend must redact provider tokens from logs and traces.
