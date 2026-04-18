# ADR-005: GitHub Actions as Exclusive CI/CD and Release System

## Status

Accepted

## Context

The project wants public proof of what code is built and deployed. Using multiple automation systems would fragment the audit trail and weaken the claim that users can verify what is running in production.

## Decision

- Use `GitHub Actions` as the only allowed system for build, test, deploy, and release automation.
- Keep workflows in `.github/workflows`.
- Disallow manual production deploys and alternate vendor-managed production automation paths.

## Consequences

### Positive

- One public control plane for CI, deploys, and releases.
- Easier provenance story for production.
- Less ambiguity about what automation is authoritative.

### Negative

- Workflow correctness and security become especially important.
- Platform-specific workflows such as iOS builds may cost more time and runner resources than a narrower CI strategy.

