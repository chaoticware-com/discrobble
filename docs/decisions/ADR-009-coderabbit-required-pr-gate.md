# ADR-009: CodeRabbit as Required Pull Request Review Gate

## Status

Accepted

## Context

The project wants AI-assisted review as a standard part of collaboration, not an occasional add-on. At the same time, it should not replace human approval for high-impact changes.

## Decision

- Require `CodeRabbit` on pull requests.
- Keep CodeRabbit configuration in the repository.
- Require human maintainer approval in addition to CodeRabbit before merge.

## Consequences

### Positive

- Review policy is explicit and repeatable.
- Repository-managed configuration keeps review expectations visible and versioned.
- Automation can catch issues early on public pull requests.

### Negative

- Pull requests depend on an external review service being available.
- Contributors need to understand both automated and human review expectations.

