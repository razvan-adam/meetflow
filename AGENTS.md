# AGENTS.md

Guidance for AI coding agents working on this repository.

## Project overview

MeetFlow is a web application that helps companies run meetings and, above all, follow through on what is decided in them. Its differentiator is **commitment tracking**: every decision or task agreed in a meeting gets an owner and a deadline, receives reminders, and automatically reappears at the next meeting until it is completed.

It is not a note-taking app. When in doubt, favor features and designs that make commitments visible, owned and trackable.

- Product idea: `idea.txt`
- High-level plan: `docs/roadmap.md`

## Current status

The project is in the planning phase. No code, tech stack or tooling has been chosen yet. Do not assume a framework, language or database; ask, or check this file and `docs/` for decisions recorded since. Update this file when the stack and commands are decided.

## Domain model (core concepts)

- **User** – a person with an account.
- **Team** – a group of users who meet regularly.
- **Meeting** – has participants, a date and an agenda.
- **Agenda item** – a topic to discuss; may be carried over from a previous meeting.
- **Decision / agreement** – something decided in a meeting.
- **Task (commitment)** – an action with an owner, a deadline and a status (e.g. open, in progress, done). Open tasks carry over to the next meeting.
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
