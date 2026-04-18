# ADR-001: Stateless Proxy Only, No Durable Server User Data

## Status

Accepted

## Context

Discrobble's main product promise is privacy: users should not need to trust a custom cloud service with their listening history, Discogs library, or provider tokens. At the same time, Last.fm and Discogs integrations require callback handling and signed server-side requests that cannot safely live entirely in a mobile client.

## Decision

- Use a thin backend proxy with no durable user database.
- Keep provider tokens, play history, release selections, and queue state on-device only.
- Allow only short-lived auth context in signed state, encrypted cookies, or in-memory request handling.
- Proxy Discogs and Last.fm requests without persisting user data beyond the lifetime of a request.

## Consequences

### Positive

- Strong privacy story aligned with the product's core value.
- Lower backend operational complexity.
- No custom cloud migration problem later.

### Negative

- Mobile auth handoff is more complex because state must be carried without a durable session store.
- Every proxied provider call must include device-held credentials.
- Cross-device sync is unavailable by design in MVP.

