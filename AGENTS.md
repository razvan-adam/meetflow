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

## Current status

The tech stack is chosen (`docs/architecture.md`); no code exists yet. The data model (MF-30) and project skeleton (MF-31) come next. Update this file when the commands are defined.

## Tech stack

- **Backend:** Java 25, Spring Boot, Maven. A modular monolith (Spring Modulith) exposing a REST + JSON API.
- **Frontend:** React + TypeScript, built with Vite.
- **Database:** PostgreSQL with Spring Data JPA and Flyway migrations.
- **Auth:** Spring Security, email + password, server-side sessions in a secure cookie.
- **Email:** Amazon SES.
- **Hosting:** AWS in an EU region: ECS Fargate, RDS for PostgreSQL, S3 + CloudFront.
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
- Add or update tests with every behavior change once a test setup exists.

## Commands

_To be defined once the tech stack is chosen (install, run, test, lint, build)._
