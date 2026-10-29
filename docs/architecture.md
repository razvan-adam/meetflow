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

The exact AWS region and infrastructure-as-code tool are decided in roadmap step 5.

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

- Every API endpoint except sign-up, login and invitation lookup requires an authenticated session.
- Team-level access control is enforced on the server for every request: users only reach meetings, tasks and minutes of teams they belong to. Each endpoint has tests for access by a non-member.
- Role rules from `docs/core-flow.md` (admin, organizer, task owner) are checked in the backend, never only hidden in the UI.
- Passwords are hashed with Spring Security's password encoder (bcrypt by default) and never logged. Login is rate-limited.
- CSRF protection is enabled for the cookie-based session.
- HTTPS everywhere; the database is not publicly reachable.

## GDPR

- All data, backups, logs and emails are processed in the AWS EU region.
- Personal data is limited to what the product needs: name and email for users, plus the meeting content customers enter. MF-30 lists the personal data fields.
- Meeting content is never written to logs.
