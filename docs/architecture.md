# MeetFlow – Architecture and Tech Stack

Decisions for roadmap step 4 (MF-29). The data model is designed in MF-30 and the project skeleton is created in MF-31.

## Overview

One React single-page app talks to one Spring Boot backend over a JSON REST API. The backend is a **modular monolith**: a single deployable split into modules with enforced boundaries, backed by one PostgreSQL database. Everything runs on AWS in an EU region.

```
Browser ── HTTPS ──► CloudFront ──┬── /*     ► S3 (React app, static files)
                                  └── /api/* ► Load balancer ► Spring Boot (ECS Fargate)
                                                                 ├── PostgreSQL (RDS)
                                                                 └── Amazon SES (email)
```

Serving the app and the API from the same domain keeps the session cookie first-party and avoids CORS.

## Decisions

| Area | Choice | Main reason | Alternatives considered |
|---|---|---|---|
| Backend | Java 25 (LTS), Spring Boot (latest stable), Maven | Team preference; mature ecosystem for security, data and email | Gradle (no strong reason to prefer it) |
| Backend structure | Modular monolith with Spring Modulith | One deployable is enough for the MVP; module boundaries let us split out a service later if needed | Microservices: more deployments and cross-service transactions for no MVP benefit |
| API | REST + JSON, documented with OpenAPI (springdoc) | Simple, well supported by Spring and React tooling | GraphQL: unnecessary for the MVP screens |
| Frontend | React + TypeScript, built with Vite; React Router, TanStack Query | Interactive screens (live meeting, inline status updates); largest hiring pool | Thymeleaf + htmx (harder for the live meeting screen), Angular |
| Database | PostgreSQL, Spring Data JPA, Flyway migrations | Relational data with strong constraints; built-in full-text search covers meeting history search | MySQL; a separate search engine (not needed) |
| Authentication | Spring Security, email + password, server-side sessions in an HttpOnly, Secure, SameSite cookie; sessions stored in PostgreSQL (Spring Session JDBC) | Simplest secure option for the MVP; works with several backend instances | JWT in the browser (harder to revoke); Keycloak or Cognito (heavy for the MVP) |
| Email | Amazon SES in the same EU region | Same cloud and region as the app; low cost; data stays in the EU | Brevo, Postmark |
| Hosting | AWS, EU region: ECS Fargate (backend), RDS for PostgreSQL, S3 + CloudFront (frontend) | Team preference; managed services with no servers to patch | Elastic Beanstalk, App Runner, EC2 |
| Secrets | AWS Secrets Manager; `.env` files locally only, never committed | No secrets in the repository or images | SSM Parameter Store |
| Testing | JUnit 5 and Testcontainers (backend); Vitest and React Testing Library (frontend) | Tests run against a real PostgreSQL | H2 in-memory database (behaves differently) |

The AWS region and infrastructure-as-code tool were decided in roadmap step 5 (MF-37, below).

### Tooling chosen with the project skeleton (MF-31)

| Area | Choice | Main reason |
|---|---|---|
| Versions | Spring Boot 4.1, Spring Modulith 2.1, PostgreSQL 18, React 19, Vite 8, TypeScript 7 | Latest stable releases when the skeleton was created |
| Local database | Docker Compose (`backend/compose.yaml`), started by Spring Boot's Docker Compose support | One command runs the whole app; no local PostgreSQL install |
| Maven repositories | `backend/.mvn/maven.config` turns off the two login-only repositories that Flyway's parent POM declares (`flyway-community-db-support`, `MavenFeed`) | Everything we use is on Maven Central; without it, every build logs HTTP 401 warnings |
| Modulith events | JDBC event publication registry, table created by Flyway | Flyway owns the whole schema, including library tables |
| Health check | Spring Boot Actuator, only the `health` endpoint, at `/api/health` | Under `/api/*`, so it goes through the same CloudFront route as the API |
| Frontend lint | oxlint (Vite template default) | Fast, needs little configuration; ESLint is the fallback if a rule we need is missing |
| Fonts | Self-hosted with Fontsource | No requests to a third-party font CDN (GDPR) |
| OpenAPI (springdoc) | Added with the first API endpoints | Nothing to document yet |

### Tooling chosen with the development foundations (MF-32)

| Area | Choice | Main reason |
|---|---|---|
| CI | GitHub Actions (`.github/workflows/ci.yml`): frontend lint, tests and build, and backend tests and package, on every pull request and push to `main` | Built into GitHub, where the code lives; the hosted runners include Docker for Testcontainers |
| Backend formatter | Spotless with Google Java Format (`./mvnw spotless:apply`; `spotless:check` in CI) | One plugin formats Java, the POM and config files; 2-space indentation like the frontend. Its 100-column limit is fixed. Alternatives: Palantir Java Format (4 spaces only), Spring Java Format (tabs, Java only) |
| Backend linter | Error Prone with NullAway, run inside `javac`; every package `@NullMarked` with JSpecify | Catches real bugs at compile time with few false positives; Spring Framework 7 uses JSpecify, so its APIs are null-checked too. Checkstyle overlaps the formatter; PMD and SpotBugs are noisier. `.mvn/jvm.config` opens the javac internals they need |
| Frontend formatter | Prettier, version pinned (`npm run format`; `format:check` in CI) | Stable, best editor support; oxfmt is Prettier-compatible and can replace it once it reaches 1.0 |
| Line endings | LF everywhere via `.gitattributes`, plus `.editorconfig` | Same files on Windows, Linux CI and Docker; formatters don't flag every file on Windows checkouts |

### Infrastructure (MF-37)

Written and validated, not created yet: AWS costs start only when the environment is created (`docs/deployment.md`).

| Area | Choice | Main reason | Alternatives considered |
|---|---|---|---|
| Infrastructure as code | Terraform (`infra/`), state in S3 with native locking | Most widely used, plain declarative code, large AWS ecosystem; one reusable module per environment | AWS CDK (TypeScript or Java; AWS-only, CloudFormation underneath), OpenTofu (same language; pick it if Terraform's BSL licence becomes a concern) |
| Region | `eu-west-1` (Ireland) | Every service we need (including SES), usually the lowest EU prices | `eu-central-1` Frankfurt, `eu-south-2` Spain, `eu-west-3` Paris |
| Staging address | The CloudFront default domain (`*.cloudfront.net`) with its certificate | HTTPS without buying a domain; a custom domain can be added later | Own domain with an ACM certificate |
| Load balancer | Internal ALB reached by CloudFront through a VPC origin | Nothing but CloudFront is public, so HTTPS covers every public endpoint without a certificate on the ALB | Public ALB with an HTTPS listener (needs a custom domain) |
| Network | Backend tasks in public subnets with a public IP, inbound from the ALB only; ALB and RDS in private subnets | No NAT gateway (about 35 USD/month) for staging | NAT gateway or VPC endpoints for private tasks (revisit for production) |
| Backend runtime | ECS Fargate on ARM64 (Graviton), image from `backend/Dockerfile` | About 20% cheaper than x86; the image only wraps the jar CI built | Buildpacks (`spring-boot:build-image`): less control, slower builds |
| Database credentials | Generated by RDS and kept in Secrets Manager (`manage_master_user_password`), injected into the task by ECS | No password in code, Terraform state or the image | Terraform-generated password (would sit in the state) |
| Deploys | `.github/workflows/deploy.yml`, called by CI after every push to `main` passes; GitHub signs in to AWS with OIDC (no stored keys) into a role limited to deploying | Same platform as CI; rollback is the same workflow run with an older commit | AWS CodePipeline |

## Backend modules

Each module owns its tables and exposes a small public API to the others.

| Module | Responsibility |
|---|---|
| `identity` | Users, sign-up, login, sessions |
| `teams` | Teams, memberships and roles, invitations |
| `meetings` | Meeting series, meetings, participants, agenda items, notes, decisions, closing |
| `tasks` | Tasks, status changes and history, carry-over, pending-tasks dashboard |
| `minutes` | Minutes generation and meeting history search |
| `notifications` | Invitation, minutes and task emails |

Closing a meeting publishes an event; `minutes` and `notifications` react to it, so the meeting module doesn't depend on them.

## Security

- Every API endpoint except the health check (`GET /api/health`), sign-up, login and invitation lookup requires an authenticated session. Unauthenticated requests get 401 and never open a session (the request cache is off), so anonymous traffic adds no rows to the session table.
- Team-level access control is enforced on the server for every request: users only reach meetings, tasks and minutes of teams they belong to. Each endpoint has tests for access by a non-member.
- Role rules from `docs/core-flow.md` (admin, organizer, task owner) are checked in the backend, never only hidden in the UI.
- Accounts (MF-8): sign-up (`POST /api/users`) logs the new user in; login is `POST /api/session`, logout `DELETE /api/session` (deletes the session from the database), and `GET /api/users/me` returns the current user. Passwords are at least 12 characters with no composition rules (NIST SP 800-63B) and at most 72 bytes (bcrypt's limit); they are hashed with bcrypt through Spring Security's delegating encoder, so the algorithm can be upgraded later, and never logged or echoed in errors. Emails are stored lowercased. A wrong password and an unknown email get the same 401. Login replaces the session ID and the CSRF token. Login is not rate-limited yet; that needs its own ticket before the public launch.
- The session cookie is Secure, HttpOnly and SameSite=Lax, set in `identity/SecurityConfig` so it is the same in every environment (Spring Boot's `server.servlet.session.cookie.*` properties only apply with the embedded server, not in tests).
- CSRF protection is enabled for the cookie-based session: every response carries an `XSRF-TOKEN` cookie (readable by JavaScript) and the React app echoes it in the `X-XSRF-TOKEN` header on requests that change data (`frontend/src/api/client.ts`).
- Secrets for staging and production (database password, SES credentials) live in AWS Secrets Manager and reach the backend as environment variables through the ECS task definition; the repository and the container image contain none. GitHub secret scanning with push protection is enabled on the repository.
- HTTPS everywhere; the database is not publicly reachable.

## GDPR

- All data, backups, logs and emails are processed in the AWS EU region.
- Personal data is limited to what the product needs: name and email for users, plus the meeting content customers enter. MF-30 lists the personal data fields.
- Meeting content is never written to logs.
