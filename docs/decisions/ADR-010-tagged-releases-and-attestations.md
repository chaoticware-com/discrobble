# ADR-010: Tagged Releases and Artifact Provenance for Production

## Status

Accepted

## Context

The project wants public confidence in what code is running in production. Visibility into source code and workflow logs helps, but a stronger model ties production deploys to immutable version tags and signed build provenance metadata.

## Decision

- Production releases are created from version tags matching `v*`.
- Release workflows produce public deployment metadata linked to the tag and commit SHA.
- Use GitHub artifact attestations for deployable artifacts where supported.
- Treat release tags, workflow runs, deployment records, and provenance metadata as the public verification chain.

## Consequences

### Positive

- Stronger public verification story for production.
- Cleaner separation between unreleased `main` changes and production code.
- Release process becomes more explicit and auditable.

### Negative

- Releases require a stricter operational process.
- Some artifact types may still need workflow-level documentation where direct attestation support is limited.

