# Security Policy

## Supported Versions

- Before the first public release, security support applies to `main`.
- After public releases begin, security support applies to `main` and the latest production tag.

## Reporting A Vulnerability

- Do not open a public GitHub issue with exploit details.
- Use GitHub's private vulnerability reporting or GitHub Security Advisories when the public repository is available and that feature is enabled.
- If private reporting is not yet enabled, request a private reporting channel without disclosing sensitive details publicly.

## Secret Handling Rules

- Never commit secrets to the repository.
- Never place provider secrets in mobile source code, Markdown docs, workflow YAML, or version-controlled Worker config.
- Store deployment credentials in protected GitHub secrets.
- Store deployed runtime secrets as Cloudflare Worker secrets.
- Use untracked local secret files such as `.dev.vars` only for local development.

## Production Deploy Controls

- Production deploys must originate from `GitHub Actions` only.
- Manual production deploys from local machines are not allowed.
- Manual production deploys or secret edits from the Cloudflare dashboard are not allowed.
- Tagged release workflows are the only allowed production release path.

## Disclosure Expectations

- Security-sensitive changes should receive maintainer review even when normal checks are green.
- Workflow changes are security-sensitive because workflows define what code is built and shipped.
- Public disclosure should wait until a fix is prepared or deployed.

