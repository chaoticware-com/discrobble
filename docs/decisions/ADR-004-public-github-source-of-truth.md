# ADR-004: Public GitHub Repository as Source of Truth

## Status

Accepted

## Context

The project is intended to be open source, and the user wants public visibility into how code is reviewed, built, deployed, and released. That requirement means the repository host is not just a place to mirror code; it is part of the product's public trust model.

## Decision

- Host the project in a public GitHub repository.
- Treat GitHub as the source of truth for code, reviews, workflows, deployments, releases, and public verification metadata.
- Keep project policy, technical decisions, and governance in version-controlled repo docs.

## Consequences

### Positive

- Clear public audit trail for project evolution.
- Simple contributor entry point for an open-source project.
- Strong alignment with public CI/CD and provenance requirements.

### Negative

- Workflow and governance changes become part of the public surface area and need careful review.
- Some operational details that might otherwise stay private must now be documented precisely enough to be publicly understandable.

