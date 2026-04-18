# ADR-007: Jetpack Compose for Native Android UI

## Status

Accepted

## Context

ADR-002 already established that Android UI would remain native rather than becoming a shared KMP UI layer. The remaining choice was whether the Android app should use modern Compose UI or traditional XML views.

## Decision

- Build the Android UI with `Jetpack Compose`.
- Keep iPhone UI in `SwiftUI`.
- Keep business logic and data flow in shared Kotlin code.

## Consequences

### Positive

- Modern native Android UI stack for a greenfield application.
- Cleaner mental symmetry with SwiftUI on iPhone.
- Good fit for state-driven UI from shared Kotlin models.

### Negative

- Compose-specific knowledge is required from Android contributors.
- UI testing patterns differ from older Android view-system projects.

