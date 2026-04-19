# Repo Layout

## Monorepo Shape

The project should stay in a single public GitHub repository with this top-level structure:

```text
.
├── AGENTS.md
├── CONTRIBUTING.md
├── LICENSE
├── README.md
├── SECURITY.md
├── .coderabbit.yaml
├── .github/
│   └── workflows/
├── androidApp/
├── iosApp/
│   ├── Discrobble.xcodeproj/
│   └── Discrobble/
├── shared/
│   ├── auth/
│   ├── catalog/
│   ├── di/
│   ├── domain/
│   ├── network/
│   ├── persistence/
│   ├── scrobble/
│   └── session/
├── backend/
│   └── worker/
├── docs/
├── libs/
├── build.gradle.kts
├── gradlew
├── gradlew.bat
├── gradle/
│   ├── libs.versions.toml
│   └── wrapper/
├── settings.gradle.kts
├── package.json
└── pnpm-workspace.yaml
```

## Directory Responsibilities

### `shared/`

- Kotlin Multiplatform business logic only.
- Split into `domain`, `auth`, `catalog`, `session`, `scrobble`, `persistence`, `network`, and `di` submodules.
- Owns domain models, repositories, auth/session rules, persistence abstractions, local database schema, networking clients, and scrobble logic.
- Does not own platform UI rendering, camera access, microphone access, or direct secret storage implementations.

### `androidApp/`

- Android application shell implemented with `Jetpack Compose`.
- Owns Android permission flows, Activity and lifecycle integration, barcode scanning bridge, Android ShazamKit bridge, and Android secure storage adapter.
- Consumes shared services and models from `shared/`.

### `iosApp/`

- iPhone application shell implemented with `SwiftUI`.
- Tracks the checked-in `Discrobble.xcodeproj` entrypoint used to open and run the iPhone shell in `Xcode`.
- Keeps the app source tree under `iosApp/Discrobble/`, alongside the project metadata bundle.
- Owns deep links, permission flows, camera integration, ShazamKit bindings, and Keychain-backed secure storage adapter.
- Consumes shared services and models from `shared/`.

### `backend/worker/`

- Stateless backend proxy implemented in `TypeScript`.
- Owns Last.fm callback handling, Discogs callback handling, Last.fm request signing, Discogs proxy endpoints, and runtime secret access.
- Does not own durable user data, databases, or background processing.

### `.github/workflows/`

- Sole automation surface for CI, preview deploys, production deploys, and releases.
- Must remain publicly visible in the repository.
- Holds the canonical workflows that define what code is built and what code is shipped.

### `docs/`

- Source of truth for product, architecture, delivery, policy, and decisions.
- Any structural or behavioral change must update this tree in the same change.

### `libs/`

- Holds local-only third-party Android AAR drops that cannot be resolved from public Maven repositories.
- Currently reserved for the Apple ShazamKit Android SDK at `libs/shazamkit-android-release.aar`.
- AAR files remain gitignored; only the directory placeholder is tracked.

## Tooling Boundaries

- Root Kotlin build uses `Gradle Kotlin DSL`.
- Shared Kotlin dependency versions live in `gradle/libs.versions.toml`.
- The repository-owned Gradle entrypoint is the checked-in wrapper at `./gradlew`.
- Root JavaScript workspace uses `pnpm`.
- Worker-specific dependencies and scripts live under `backend/worker/`.
- `CodeRabbit` configuration lives at repo root in `.coderabbit.yaml`.

## Ownership Rules

- Shared code owns business logic and contracts.
- Native apps own user interface and platform APIs.
- The Worker owns external callback handling and signed/proxied HTTP requests only.
- GitHub workflows own automation, deployment sequencing, and release provenance.
- Docs own the approved operating model and must be updated with every material change.

## Boundaries To Preserve

- Do not put UI rendering logic into `shared/`.
- Do not put provider client secrets into mobile code or version-controlled config files.
- Do not put durable user state into `backend/worker/`.
- Do not create a second automation system outside `.github/workflows/`.
- Do not split governance docs away from the repo root.
