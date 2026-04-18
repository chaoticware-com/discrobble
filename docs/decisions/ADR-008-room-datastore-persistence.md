# ADR-008: Room KMP and DataStore Preferences KMP for Local Persistence

## Status

Accepted

## Context

The app needs structured local persistence for release metadata, sessions, recognition summaries, and queued scrobbles, plus lightweight preferences for settings and cache state. The user preferred an official-first stack where possible.

## Decision

- Use `Room Database for KMP` for structured relational data.
- Use `DataStore Preferences for KMP` for lightweight preferences and flags.
- Keep provider tokens in platform-native secure storage rather than the shared database.

## Consequences

### Positive

- Persistence stack aligns with current Android KMP guidance.
- Clear separation between relational data, preferences, and secure secrets.
- Shared code can own more persistence behavior without giving up platform-appropriate secret storage.

### Negative

- Persistence configuration is more complex than a single-library approach.
- iOS integration still requires careful setup even when the shared logic is common.

