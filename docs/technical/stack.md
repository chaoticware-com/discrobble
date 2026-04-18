# Stack

## Selection Principles

- Prefer official or platform-owner libraries where that choice does not create avoidable complexity.
- Keep the mobile domain and session logic shared through Kotlin Multiplatform.
- Keep platform-sensitive UI and device integration native.
- Keep the backend stateless, low-ops, and cheap to run.
- Keep review, CI, deploys, and releases visible in the public GitHub repository.

## Selected Stack

| Layer | Choice | Why |
| --- | --- | --- |
| Shared application core | `Kotlin Multiplatform` | One shared implementation for auth, catalog sync, session rules, scrobble queue, and persistence orchestration. |
| iPhone UI | `SwiftUI` | Native Apple UI stack with strong support for app lifecycle, permissions, and ShazamKit integration. |
| Android UI | `Jetpack Compose` | Android's modern native UI toolkit and the clearest native complement to SwiftUI. |
| Shared networking | `Ktor Client` | KMP-friendly HTTP client that fits shared models and coroutine-based flows. |
| Serialization | `kotlinx.serialization` | Native fit with Ktor and Kotlin-first domain models. |
| Concurrency and streams | `kotlinx.coroutines` and `Flow` | Shared async model across sync, session state, retries, and UI state observation. |
| Dependency wiring | `Koin` | Pragmatic DI for KMP without the ceremony of heavier compile-time frameworks. |
| Structured local storage | `Room Database for KMP` | Official Android KMP-ready relational persistence for releases, sessions, recognition summaries, and queues. |
| Preferences and light flags | `DataStore Preferences for KMP` | Official KMP-ready preferences store for lightweight local state and cache metadata. |
| Secure token storage | Native platform secure storage | Use iOS Keychain and Android keystore-backed native storage behind shared interfaces. |
| Backend runtime | `Cloudflare Workers` | Stateless edge runtime with low operational overhead and a workable free-tier path. |
| Backend language | `TypeScript` | Best fit for the Cloudflare Worker ecosystem and GitHub-based automation. |
| Backend framework | `Hono` | Minimal web framework with first-class Cloudflare Worker support. |
| Worker toolchain | `Wrangler` | Official Cloudflare development and deploy toolchain. |
| Kotlin build system | `Gradle Kotlin DSL` with version catalog | Standard Kotlin build tooling and a single place to manage shared dependencies. |
| Worker package manager | `pnpm` | Fast, deterministic JavaScript package management for the Worker package. |
| CI/CD | `GitHub Actions` | Public, versioned workflows that align with the repo's transparency goal. |
| AI code review | `CodeRabbit` | Required automated review layer on pull requests, configured from the repo itself. |

## Chosen Technical Solutions

### Shared Mobile Core

- Keep the domain model, auth state, catalog sync, session engine, queue logic, and repository interfaces in KMP.
- Use `Ktor Client` + `kotlinx.serialization` for backend and provider-facing HTTP in shared code.
- Use `Koin` to assemble shared services and expose platform entrypoints cleanly to iOS and Android.
- Use `Room KMP` for normalized local entities and `DataStore Preferences KMP` for lightweight settings and cache timestamps.

### Native App Shells

- iPhone app stays in `SwiftUI`.
- Android app stays native in `Jetpack Compose`.
- Barcode scanning, ShazamKit integration, platform secure storage, and lifecycle-specific behavior remain in native layers.
- Shared code owns business rules; native code owns rendering and platform APIs.

### Stateless Backend

- Use a single `Cloudflare Worker` implemented in `TypeScript` with `Hono`.
- Keep the backend limited to auth callbacks, Last.fm request signing, and Discogs proxying.
- Deploy the Worker with `Wrangler` from GitHub Actions only.
- Store runtime secrets in Cloudflare Worker secrets, managed through GitHub-controlled workflows.

### Public Delivery Model

- Keep the source code in a public GitHub repository.
- Run CI, preview deploys, production deploys, and releases through `.github/workflows`.
- Use `CodeRabbit` as a required PR gate and keep its configuration in repo.
- Generate artifact provenance for deployable outputs where GitHub supports attestations.

## Explicit Non-Selections

| Option | Why it was not selected |
| --- | --- |
| `AWS Lambda` + Kotlin backend | Keeps the language uniform, but increases cloud setup and operational overhead for the MVP. |
| `SQLDelight` | Viable for KMP, but less aligned with the official-first Android guidance now that Room supports KMP. |
| Android XML Views | Native, but not the preferred modern Android UI direction for a new project. |
| Manual dependency wiring everywhere | Reduces dependencies, but adds repetitive setup across shared and native integration boundaries. |
| Cloudflare Workers Builds | Conflicts with the project's decision to make GitHub Actions the only allowed deploy path. |

## Operational Notes

- Pin concrete dependency versions in Gradle version catalogs and lockfiles when scaffolding starts.
- Pin GitHub Actions to immutable references before the first production deployment.
- Keep the stack intentionally small until the integration spikes prove the hardest provider and platform assumptions.

