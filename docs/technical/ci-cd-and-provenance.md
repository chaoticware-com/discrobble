# CI/CD and Provenance

## Operating Rule

GitHub is the public source of truth for Discrobble's code review, CI, deployments, releases, and production verification.

- Source code lives in a public GitHub repository.
- Pull requests are reviewed in GitHub.
- `GitHub Actions` is the only allowed system for build, test, deploy, and release automation.
- Manual production deploys from local machines or the Cloudflare dashboard are out of policy.
- Cloudflare Workers Builds is not used for production because GitHub Actions must remain the visible control plane.

## Branch and Review Policy

- `main` is protected from direct pushes.
- Every change merges through a pull request.
- Merge requires:
  - green required GitHub Actions checks
  - passing `CodeRabbit` review status
  - at least one human maintainer approval
- Workflow files, backend auth logic, and shared scrobble/session logic are high-scrutiny paths and should always receive maintainer review.

## Workflow Inventory

### `ci.yml`

Runs on pull requests and pushes to `main`.

- docs consistency checks
- shared Kotlin build and tests
- Android build and tests
- iOS build and smoke tests
- Worker lint and tests
- workflow linting

Required status checks should map to these lanes:

- `ci/docs`
- `ci/shared`
- `ci/android`
- `ci/ios`
- `ci/worker`
- `ci/workflows`

### `preview-worker.yml`

Runs only through GitHub Actions for non-production preview environments.

- intended for maintainer-controlled preview deployments
- uses the `preview` GitHub environment
- never uses production secrets
- should be limited to trusted branches or explicitly approved maintainer dispatches

### `release.yml`

Runs on version tags matching `v*`.

- rebuilds and retests release outputs
- uploads release artifacts
- generates artifact attestations where supported
- deploys the Worker to the `production` GitHub environment
- creates or updates the GitHub Release
- records the deployment in GitHub

### `mobile-beta.yml`

Runs through GitHub Actions for internal mobile distribution.

- packages iOS and Android beta builds
- publishes to TestFlight internal distribution and Google Play internal testing
- links beta outputs back to tag, commit, and workflow run

## Environments and Secrets

### GitHub Environments

- `preview`
  - used for non-production preview deploys
  - uses non-production credentials only
- `production`
  - used only from tagged release workflows
  - protected by maintainer approval and restricted secret access

### Secret Model

- GitHub environment secrets are the operational control point for deployment credentials and provider app secrets.
- Cloudflare deployment credentials such as `CLOUDFLARE_API_TOKEN` and `CLOUDFLARE_ACCOUNT_ID` are stored in GitHub secrets.
- Runtime provider secrets are injected by GitHub Actions into Cloudflare Worker secrets during controlled deploy workflows.
- Secrets never live in source control, local scripts committed to the repo, or plaintext workflow files.

## Action and Workflow Rules

- Workflow files live only in `.github/workflows/`.
- Actions must be pinned to immutable commit SHAs before the first production deployment.
- Reusable workflows are preferred when multiple pipelines share the same job logic.
- Workflow changes are treated as security-sensitive changes and must receive maintainer review.

## Provenance Model

For every production release, the public repository should expose:

- version tag
- commit SHA
- workflow run URL
- GitHub deployment record
- release artifacts
- build provenance attestation where GitHub supports it

GitHub artifact attestations are required for deployable artifacts where supported by the workflow and artifact type.

## How A User Verifies Production

1. Open the public GitHub Release for the production version tag.
2. Confirm the release points to the expected commit SHA.
3. Open the linked GitHub Actions release workflow run.
4. Inspect the GitHub deployment record for the `production` environment.
5. Inspect the release artifacts and the associated build provenance attestation.
6. Verify that the deployed Worker version and the public release metadata agree on tag, commit, and workflow run.

## Cloudflare Worker Deployment Rules

- Worker deployments happen through `Wrangler` in GitHub Actions only.
- Production runtime secrets are set through the GitHub-controlled deployment path and stored in Cloudflare as Worker secrets.
- Direct dashboard edits to production config or secrets are out of policy.
- Preview deployments may exist, but only through the GitHub Actions pipeline.

