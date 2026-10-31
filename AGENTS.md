# AGENTS.md

Guidance for AI coding agents working on this repository.

## Project overview

MeetFlow is a web application that helps companies run meetings and, above all, follow through on what is decided in them. Its differentiator is **commitment tracking**: every decision or task agreed in a meeting gets an owner and a deadline, is emailed to its owner after the meeting, and automatically reappears at the next meeting until it is completed.

It is not a note-taking app. When in doubt, favor features and designs that make commitments visible, owned and trackable.

- High-level plan: `docs/roadmap.md`
- Core meeting flow and commitment lifecycle: `docs/core-flow.md`
- MVP backlog order (source of truth for priority, not the Jira board order): `docs/mvp-backlog.md`
- User journeys and key screens: `docs/user-journeys.md`
- Wireframes (open in a browser): `docs/wireframes/index.html`
- Basic visual style (colors, type, components): `docs/visual-style.md`
- Architecture and tech stack: `docs/architecture.md`
- Staging environment, deploys and rollback: `docs/deployment.md`
- Data model: `docs/data-model.md`
- Coding conventions (naming, package structure, API style, tests, commits and branches): `docs/conventions.md`

## Current status

The project skeleton (MF-31) is in place: a Spring Boot backend with the full schema as Flyway migrations, and a React frontend. Roadmap step 6 (epic MF-5, core: users, teams and meetings) has started: sign-up, login and logout are built (MF-8). Roadmap step 5 (epic MF-32: CI, coding conventions, baseline security, staging) is done apart from creating staging: CI runs on GitHub Actions (`.github/workflows/ci.yml`), the coding conventions with their formatters and linters are in place (MF-34), the skeleton's review follow-ups are fixed (MF-35), and baseline security is in place (MF-36: authentication, CSRF, secure session cookie, secret scanning). The staging environment (MF-37) is built but not created: the Terraform code, backend image and deploy workflow exist and are checked in CI, but nothing runs in AWS yet and deploys are off (see "Build now, deploy later" below).

## Repository layout

- `backend/` – Spring Boot app (Maven wrapper). One package per module under `com.meetflow` (`identity`, `teams`, `meetings`, `tasks`, `minutes`, `notifications`), each split into layer subpackages (`controller`, `service`, `repository`, …; see `docs/conventions.md`); `ModularityTests` enforces their boundaries. Migrations in `src/main/resources/db/migration`, one per module to start with; never edit an applied migration, add a new one.
- `frontend/` – React app. `src/app` (providers, router), `src/pages` (one component per screen), `src/api` (fetch client and TanStack Query hooks), `src/styles` (visual-style tokens).
- `infra/` – Terraform for AWS: `bootstrap` (one-time, account-wide), `modules/environment` (one environment), `staging`. See `docs/deployment.md`.
- `scripts/dev.mjs` – starts both apps for local development.

## Tech stack

- **Backend:** Java 25, Spring Boot, Maven. A modular monolith (Spring Modulith) exposing a REST + JSON API.
- **Frontend:** React + TypeScript, built with Vite.
- **Database:** PostgreSQL with Spring Data JPA and Flyway migrations.
- **Auth:** Spring Security, email + password, server-side sessions in a secure cookie.
- **Email:** Amazon SES.
- **Hosting:** AWS `eu-west-1` (Ireland): ECS Fargate, RDS for PostgreSQL, S3 + CloudFront, defined with Terraform.
- **Tests:** JUnit 5 and Testcontainers (backend), Vitest and React Testing Library (frontend).

Don't add other frameworks or services without recording the decision in `docs/architecture.md`.

## Domain model (core concepts)

- **User** – a person with an account.
- **Team** – a group of users who meet regularly. Team admins manage membership.
- **Meeting series** – recurring meetings of a team with the same purpose (e.g. weekly sync); has an organizer. A team can have several.
- **Meeting** – belongs to a series; has participants, a date and an agenda.
- **Agenda item** – a topic to discuss; may be carried over from a previous meeting.
- **Decision / agreement** – something decided in a meeting.
- **Task (commitment)** – an action with an owner, a deadline and a status (e.g. open, in progress, done). Open tasks carry over to the next meeting of the same series.
- **Minutes** – structured summary of a meeting, sent to participants.

## MVP scope

1. User and team sign-up
2. Meeting creation
3. Recording of agreements
4. Task assignment with owners
5. Pending-tasks dashboard
6. Meeting history
7. Minutes generation

Out of scope for the MVP (do not build unless asked): AI summaries from transcripts, Microsoft Teams or calendar integrations, advanced automations and analytics, billing.

## Working conventions

- Code, comments, commit messages and documentation are in **English**.
- Keep changes small and focused on the task requested; don't add features outside the MVP scope.
- Treat security as a requirement: authenticate every endpoint, enforce team-level access control (users only see their teams' meetings and tasks), never commit secrets.
- Meeting content is business data from customers; handle personal data with GDPR in mind.
- Add or update tests with every behavior change.
- Follow `docs/conventions.md`. Run the formatters before committing; commit messages start with the Jira key (`MF-34 Add …`) and branch names describe the purpose without it (`feature/team-invitations`).
- **Before creating a pull request**, show the user what changed (the commits and the main diffs) and explain each change and why it was made. Open the PR only after the user has reviewed it and agreed.
- **Never change Jira** (no status transitions, edits, comments or new issues). Reading tickets is fine. Instead, tell the user what should change in Jira (e.g. "move MF-35 to Done") and let them do it.
- **Build now, deploy later (no AWS costs yet).** Infrastructure is written and validated but not created. Never run `terraform plan`, `apply` or `import` against AWS, create AWS resources or accounts, configure AWS credentials, or enable deploys (`STAGING_DEPLOY_ENABLED`, GitHub environments) unless the user explicitly asks to create the environment. Infrastructure changes are checked with `terraform fmt` and `terraform validate` only.

## Commands

Prerequisites: JDK 25 (`JAVA_HOME` pointing to it), Node.js 24 LTS or later, Docker running (local database and backend tests), and Terraform 1.16 or later for infrastructure changes. Maven needs no install; use the wrapper. On Windows, run `mvnw.cmd` (or `.\mvnw` in PowerShell) instead of `./mvnw`.

| Task | Command (run from) |
|---|---|
| Install | `npm ci` (`frontend/`); backend dependencies download on the first Maven run |
| Run locally | `npm run dev` (repo root): backend on :8080, PostgreSQL via Docker Compose, frontend on http://localhost:5173 |
| Health check | `GET /api/health` |
| Test backend | `./mvnw test` (`backend/`); uses Testcontainers, so Docker must be running |
| Test frontend | `npm test` (`frontend/`) |
| Lint frontend | `npm run lint` (`frontend/`, oxlint); type checking runs in the build |
| Lint backend | Error Prone and NullAway run on every compile (`./mvnw compile`, `test`, `package`) and fail the build |
| Format | `./mvnw spotless:apply` (`backend/`) and `npm run format` (`frontend/`, Prettier) |
| Check formatting | `./mvnw spotless:check` (`backend/`) and `npm run format:check` (`frontend/`); CI runs both |
| Build | `./mvnw package` (`backend/`, runs the tests too) and `npm run build` (`frontend/`, output in `dist/`) |
| Backend image | `docker build -t meetflow-backend .` (`backend/`, after `./mvnw package`) |
| Check infrastructure | `terraform -chdir=infra fmt -recursive` (repo root) to format; `terraform init -backend=false` then `terraform validate` in `infra/bootstrap` and `infra/staging` (no AWS access needed); CI runs both |

Configuration: local development needs no settings. Optional overrides go in `backend/.env` (see `backend/.env.example`); never commit it.
