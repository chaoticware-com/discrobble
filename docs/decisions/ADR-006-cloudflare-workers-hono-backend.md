# ADR-006: Cloudflare Workers with TypeScript and Hono for the Stateless Backend

## Status

Accepted

## Context

The backend scope is intentionally small: OAuth callback handling, Last.fm request signing, and Discogs proxying. The user wants a low-cost deployment path, while the project also needs public GitHub-driven deployment visibility.

## Decision

- Use `Cloudflare Workers` as the backend runtime.
- Implement the Worker in `TypeScript`.
- Use `Hono` as the HTTP framework.
- Use `Wrangler` from GitHub Actions as the deployment toolchain.

## Consequences

### Positive

- Low operational overhead for a small stateless proxy.
- Good fit for GitHub-driven deployment automation.
- Simple path to preview and production environments without introducing a backend database.

### Negative

- Backend stack differs from the Kotlin shared application stack.
- Worker runtime constraints must be respected during implementation.
- Secret management must be carefully coordinated between GitHub and Cloudflare.

