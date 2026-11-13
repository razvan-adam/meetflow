# meetflow
Web App that manages meetings, agreements and pending tasks which are discussed on that meeting.

## Running locally

### Prerequisites

- **JDK 25**, with `JAVA_HOME` pointing to it. Maven is not needed: the backend uses the Maven wrapper.
- **Node.js 24 LTS** or later.
- **Docker**, running. The local PostgreSQL database and the backend tests run in containers.

### Start the app

```sh
git clone <repository-url> meetflow
cd meetflow
cd frontend && npm ci && cd ..   # install frontend dependencies (once)
npm run dev                      # from the repository root
```

`npm run dev` starts everything together:

- the **backend** (Spring Boot) on http://localhost:8080. It starts PostgreSQL from `backend/compose.yaml` and creates the schema with Flyway, so no database setup is needed;
- the **frontend** (Vite) on http://localhost:5173, which forwards `/api` calls to the backend.

The first run takes a few minutes while Maven and Docker download dependencies. Open http://localhost:5173, sign up with any email and password, and create a team. `GET http://localhost:8080/api/health` checks that the backend is up. Stop both apps with `Ctrl+C`; the database container stops too and keeps its data for the next run.

No configuration is needed. Locally, **no emails are sent**: links that would be emailed (team invitations, for example) are written to the backend log instead, so you can open them by hand. To change settings (port, an external database), copy `backend/.env.example` to `backend/.env`; never commit it.

### Run the tests

```sh
cd backend && ./mvnw test   # backend: JUnit 5 + Testcontainers, Docker must be running
cd frontend && npm test     # frontend: Vitest + React Testing Library
```

On Windows, use `mvnw.cmd test` (Command Prompt) or `.\mvnw test` (PowerShell). Lint, format and build commands are listed in `AGENTS.md`.

## Terminology: minutes

**Minutes** are the written record of a meeting: attendees, agenda, decisions, and the tasks agreed with their owners and deadlines. MeetFlow generates them automatically when a meeting is closed and sends them to all participants.

We keep the term "minutes". These alternatives were considered:

| Name | Feel | Example in the app |
|---|---|---|
| **Meeting summary** | Clear, neutral, needs no explanation | "Meeting summary sent to 6 participants" |
| **Recap** | Short and friendly, common in modern tools | "Monday ops sync – Recap" |
| **Follow-up** | Puts the focus on what happens next, which fits commitment tracking | "Your follow-up from today's meeting: 3 new tasks" |
| **Meeting report** | More formal, suits larger companies | "Download meeting report (PDF)" |
| **Minutes** | Traditional and well known in business, but sounds old-fashioned | "Minutes of the board meeting" |

"Notes" is not used for this document: MeetFlow is not a note-taking app, and "notes" already refers to the short comments taken on each agenda item during a meeting.
